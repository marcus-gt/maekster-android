package com.subtracks.data.repo

import com.subtracks.R
import com.subtracks.UiException
import com.subtracks.UiMessage
import com.subtracks.data.db.StoredSubsonicConfig
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.download.ArtworkStore
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Source
import com.subtracks.data.model.SourceHeader
import com.subtracks.data.model.SubsonicConfig
import com.subtracks.data.model.SubsonicSource
import com.subtracks.data.model.artistImageKey
import com.subtracks.data.model.coverArtKey
import com.subtracks.data.net.HeaderEntry
import com.subtracks.data.net.HeaderValidator
import com.subtracks.data.net.NetworkMode
import com.subtracks.data.net.OriginScopedHeadersInterceptor
import com.subtracks.data.net.ScopedHeaders
import com.subtracks.data.net.ServerHeaders
import com.subtracks.data.prefs.StreamQuality
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.security.CredentialStore
import com.subtracks.data.source.MusicSource
import com.subtracks.data.source.declaredStreamLength
import com.subtracks.data.source.streamLengthSuffix
import com.subtracks.data.source.subsonic.SubsonicClient
import com.subtracks.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import com.subtracks.data.source.subsonic.SubsonicSource as SubsonicMusicSource

class SourceRepository(
    private val db: SubtracksDatabase,
    private val http: OkHttpClient,
    private val prefs: UserPreferences,
    private val artworkStore: ArtworkStore,
    private val credentials: CredentialStore,
    private val serverHeaders: ServerHeaders = ServerHeaders(),
    networkMode: Flow<NetworkMode> = flowOf(NetworkMode.Wifi),
    private val showMessage: (UiMessage) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    // [source] is null while the server needs signing in: the mirror and downloads stay usable, the network does not.
    private data class ActiveSource(
        val id: Long,
        val source: SubsonicMusicSource?,
    )

    private data class ResolvedSource(
        val config: SubsonicConfig?,
        val quality: StreamQuality,
        val mode: NetworkMode,
        val concurrency: Int,
    )

    @Volatile
    private var active: ActiveSource? = null

    private val _quality = MutableStateFlow(StreamQuality())
    val quality: StateFlow<StreamQuality> = _quality

    @Volatile
    private var downloadQuality = StreamQuality()

    @Volatile
    private var allowMeteredDownloads = false

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline

    private val _activeSource = MutableStateFlow<Long?>(null)
    val activeSource: StateFlow<Long?> = _activeSource

    fun downloadsAllowedOverMetered(): Boolean = allowMeteredDownloads

    fun setOfflineMode(enabled: Boolean) {
        Log.i("net", "offline mode -> $enabled")
        scope.launch { prefs.setOfflineMode(enabled) }
    }

    init {
        scope.launch {
            prefs.downloadQuality().collect { downloadQuality = it }
        }
        scope.launch {
            prefs.downloadOverMetered().collect { allowMeteredDownloads = it }
        }
        scope.launch {
            prefs.offlineMode().collect { _offline.value = it }
        }
        scope.launch {
            // Read the persisted flag before the source config can build a source or probe it, so a
            // cold start with offline on never touches the network.
            _offline.value = prefs.offlineMode().first()
            sealLegacyPasswords()
            var lastMode: NetworkMode? = null
            var lastNeededSignIn = false
            combine(
                activeConfig(),
                networkMode,
                prefs.streamQuality(NetworkMode.Wifi),
                prefs.streamQuality(NetworkMode.Mobile),
                prefs.syncConcurrency(),
            ) { config, mode, wifi, mobile, concurrency ->
                ResolvedSource(config, if (mode == NetworkMode.Wifi) wifi else mobile, mode, concurrency)
            }.collect { (config, quality, mode, concurrency) ->
                if (mode != lastMode) {
                    Log.i("net", "network mode -> ${mode.key}")
                    lastMode = mode
                }
                val sourceChanged = config?.id != active?.id
                if (sourceChanged) {
                    Log.i("net", "active source -> ${config?.name ?: "none"}")
                }
                val needsSignIn = config?.needsSignIn == true
                if (needsSignIn && (sourceChanged || !lastNeededSignIn)) {
                    Log.w("net", "the active source needs signing in again")
                    showMessage(UiMessage(R.string.server_needs_sign_in, listOf(config.name)))
                }
                lastNeededSignIn = needsSignIn
                // Before the source is built, so nothing it logs can show them.
                Log.setSecrets(listOfNotNull(config?.password) + config?.headers.orEmpty().map { it.value })
                // Before the source is built, so its first request already carries them.
                serverHeaders.set(
                    config?.takeIf { !needsSignIn }?.let { ScopedHeaders(normalizeAddress(it.address), it.headers) },
                )
                active =
                    config?.let {
                        ActiveSource(it.id, if (needsSignIn) null else it.toMusicSource(quality, concurrency))
                    }
                _activeSource.value = config?.id
                _quality.value = quality
                if (sourceChanged && config != null && !needsSignIn && config.useTokenAuth && !_offline.value) {
                    scope.launch {
                        runCatching { config.toClient().check("ping") }
                            .onSuccess { Log.i("net", "connected (ping ok)") }
                    }
                }
            }
        }
    }

    fun sources(): Flow<List<Source>> = db.sourcesDao().sources()

    fun activeSourceId(): Flow<Long?> = db.sourcesDao().activeSourceId()

    suspend fun activeSourceIdOnce(): Long? = db.sourcesDao().activeSourceIdOnce()

    fun activeConfig(): Flow<SubsonicConfig?> =
        combine(db.sourcesDao().activeSubsonicConfig(), db.sourcesDao().activeHeaders()) { config, headers ->
            config?.open(headers)
        }.flowOn(Dispatchers.Default)

    suspend fun sourceConfigOnce(id: Long): SubsonicConfig? {
        val dao = db.sourcesDao()
        return dao.subsonicConfigOnce(id)?.open(dao.headersOnce(id))
    }

    /** The servers whose stored password or a header value can no longer be decrypted, so the user must re-enter it. */
    fun sourcesNeedingSignIn(): Flow<Set<Long>> =
        combine(db.sourcesDao().sealedPasswords(), db.sourcesDao().allHeaders()) { sealed, headers ->
            val passwords = sealed.filter { credentials.decrypt(it.passwordCiphertext) == null }.map { it.sourceId }
            val values = headers.filter { credentials.decrypt(it.valueCiphertext) == null }.map { it.sourceId }
            (passwords + values).toSet()
        }.flowOn(Dispatchers.Default)

    /**
     * Moves every plaintext password left from before encryption into the [CredentialStore]. Each ciphertext is
     * opened again and compared before the row changes, and storing it clears the plaintext in the same statement,
     * so a failure at any point leaves the plaintext in place to try again on the next start.
     */
    internal suspend fun sealLegacyPasswords() {
        val dao = db.sourcesDao()
        for (legacy in dao.legacyPasswords()) {
            val ciphertext = runCatching { credentials.encrypt(legacy.password) }.getOrNull()
            if (ciphertext == null || credentials.decrypt(ciphertext) != legacy.password) {
                Log.w("net", "could not seal the password of source ${legacy.sourceId}; keeping it for now")
                continue
            }
            dao.sealLegacyPassword(legacy.sourceId, legacy.password, ciphertext)
        }
    }

    private fun StoredSubsonicConfig.open(stored: List<SourceHeader>): SubsonicConfig {
        val secret = if (passwordCiphertext != null) credentials.decrypt(passwordCiphertext) else password
        val headers = stored.map { header -> credentials.decrypt(header.valueCiphertext)?.let { HeaderEntry(header.name, it) } }
        val sealedAway = secret == null || headers.any { it == null }
        return SubsonicConfig(
            id = id,
            name = name,
            address = address,
            username = username,
            password = secret.takeUnless { sealedAway }.orEmpty(),
            useTokenAuth = useTokenAuth,
            needsSignIn = sealedAway,
            headers = if (sealedAway) emptyList() else headers.filterNotNull(),
        )
    }

    /** The headers to send with a request to [url]; for DownloadManager, which cannot run an OkHttp interceptor. */
    fun headersFor(url: String): List<HeaderEntry> = serverHeaders.forUrl(url)

    fun coverArt(
        coverArt: String?,
        thumbnail: Boolean = false,
    ): CoverArtRef? {
        val current = active ?: return null
        if (coverArt == null) return null
        val cacheKey = coverArtKey(current.id, coverArt, thumbnail)
        artworkStore.uri(current.id, cacheKey)?.let { return CoverArtRef(url = it, cacheKey = cacheKey) }
        if (_offline.value) return null
        val url = current.source?.coverArtUri(coverArt, thumbnail)?.toString() ?: return null
        return CoverArtRef(url = url, cacheKey = cacheKey)
    }

    fun artistImage(
        artistId: String,
        url: String?,
    ): CoverArtRef? {
        val current = active ?: return null
        if (url.isNullOrBlank()) return null
        val cacheKey = artistImageKey(current.id, artistId)
        artworkStore.uri(current.id, cacheKey)?.let { return CoverArtRef(url = it, cacheKey = cacheKey) }
        if (_offline.value) return null
        return CoverArtRef(url = url, cacheKey = cacheKey)
    }

    fun networkCoverArt(
        sourceId: Long,
        coverArt: String,
        thumbnail: Boolean,
    ): String? {
        if (_offline.value) return null
        val current = active?.takeIf { it.id == sourceId } ?: return null
        return current.source?.coverArtUri(coverArt, thumbnail)?.toString()
    }

    fun streamUri(
        songId: String,
        durationMs: Long?,
        quality: StreamQuality,
    ): String? {
        if (_offline.value) return null
        val uri = active?.source?.streamUri(songId)?.toString() ?: return null
        val length = declaredStreamLength(durationMs, quality) ?: return uri
        return uri + streamLengthSuffix(length)
    }

    fun downloadUri(
        sourceId: Long,
        songId: String,
    ): String? {
        if (_offline.value) return null
        val source = active?.takeIf { it.id == sourceId }?.source ?: return null
        // A transcode preference downloads the stream, so the saved file is already the wanted
        // quality rather than the original that would then have to be transcoded on every play.
        val quality = downloadQuality
        return if (quality.transcodes) {
            source.streamUri(songId, quality.maxBitrate, quality.format).toString()
        } else {
            source.downloadUri(songId).toString()
        }
    }

    suspend fun addSource(
        name: String,
        address: String,
        username: String,
        password: String,
        useTokenAuth: Boolean,
        headers: List<HeaderEntry> = emptyList(),
    ): Long {
        requireValidHeaders(headers)
        val dao = db.sourcesDao()
        val id =
            dao.insertSource(
                Source(
                    name = name.trim(),
                    address = normalizeAddress(address),
                    isActive = false,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        dao.upsertSubsonicSource(sealed(id, username, password, useTokenAuth))
        dao.replaceHeaders(id, sealedHeaders(id, headers))
        dao.setActiveSource(id)
        return id
    }

    suspend fun selectSource(id: Long) = db.sourcesDao().setActiveSource(id)

    suspend fun updateSource(
        id: Long,
        name: String,
        address: String,
        username: String,
        password: String,
        useTokenAuth: Boolean,
        headers: List<HeaderEntry> = emptyList(),
    ) {
        requireValidHeaders(headers)
        val dao = db.sourcesDao()
        if (dao.sourceOnce(id) == null) return
        dao.updateSourceDetails(id, name.trim(), normalizeAddress(address))
        dao.upsertSubsonicSource(sealed(id, username, password, useTokenAuth))
        dao.replaceHeaders(id, sealedHeaders(id, headers))
    }

    private fun requireValidHeaders(headers: List<HeaderEntry>) {
        val problem = HeaderValidator.problem(headers)
        require(problem == null) { "Invalid custom header: $problem" }
    }

    private fun sealedHeaders(
        sourceId: Long,
        headers: List<HeaderEntry>,
    ) = headers.mapIndexed { position, header ->
        SourceHeader(sourceId, position, header.name, credentials.encrypt(header.value))
    }

    // Only the ciphertext is stored; the plaintext column stays empty.
    private fun sealed(
        sourceId: Long,
        username: String,
        password: String,
        useTokenAuth: Boolean,
    ) = SubsonicSource(
        sourceId = sourceId,
        username = username.trim(),
        password = "",
        useTokenAuth = useTokenAuth,
        passwordCiphertext = credentials.encrypt(password),
    )

    suspend fun deleteSource(id: Long): Boolean {
        if (db.sourcesDao().activeSourceIdOnce() == id) return false
        db.sourcesDao().deleteSource(id)
        return true
    }

    suspend fun ping(
        address: String,
        username: String,
        password: String,
        useTokenAuth: Boolean,
        headers: List<HeaderEntry> = emptyList(),
    ): Result<Boolean> =
        if (_offline.value) {
            Result.failure(UiException(UiMessage(R.string.sync_offline), "Offline mode is on"))
        } else {
            withContext(Dispatchers.IO) {
                var fellBack = false
                runCatching {
                    client(address, username, password, useTokenAuth, headersOverride = headers) { fellBack = true }
                        .check("ping")
                }.map { fellBack }
            }
        }

    suspend fun activeMusicSource(): MusicSource? {
        if (_offline.value) return null
        val config = activeConfig().first() ?: return null
        if (config.needsSignIn) return null
        return config.toMusicSource(quality.value, prefs.syncConcurrency().first())
    }

    suspend fun artistBiography(
        sourceId: Long,
        artistId: String,
    ): String? {
        check(!_offline.value) { "Offline mode is on" }
        val config = sourceConfigOnce(sourceId) ?: error("No configured source")
        check(!config.needsSignIn) { "The source needs signing in" }
        return config.toMusicSource(quality.value, prefs.syncConcurrency().first()).artistBiography(artistId)
    }

    private fun SubsonicConfig.toClient(): SubsonicClient =
        client(
            address = address,
            username = username,
            password = password,
            useTokenAuth = useTokenAuth,
            onTokenAuthUnsupported = { disableTokenAuth(id) },
        )

    private fun SubsonicConfig.toMusicSource(
        quality: StreamQuality,
        syncConcurrency: Int,
    ): SubsonicMusicSource =
        SubsonicMusicSource(
            id = id,
            client = toClient(),
            maxBitrate = quality.maxBitrate,
            streamFormat = quality.format,
            maxConcurrentFetches = syncConcurrency,
        )

    private fun disableTokenAuth(sourceId: Long) {
        scope.launch {
            db.sourcesDao().disableTokenAuth(sourceId)
            showMessage(UiMessage(R.string.connection_token_fallback))
        }
    }

    private fun client(
        address: String,
        username: String,
        password: String,
        useTokenAuth: Boolean,
        // The headers typed into the server form, for testing a connection before they are saved.
        headersOverride: List<HeaderEntry>? = null,
        onTokenAuthUnsupported: () -> Unit = {},
    ) = SubsonicClient(
        baseUrl = normalizeAddress(address).toHttpUrl(),
        username = username,
        password = password,
        useTokenAuth = useTokenAuth,
        http = headersOverride?.let { withHeaders(normalizeAddress(address), it) } ?: http,
        onTokenAuthUnsupported = onTokenAuthUnsupported,
    )

    // The shared client sends the active server's saved headers; this one sends [headers] instead.
    private fun withHeaders(
        address: String,
        headers: List<HeaderEntry>,
    ): OkHttpClient {
        val scoped = ScopedHeaders(address, headers)
        return http
            .newBuilder()
            .apply { networkInterceptors().removeAll { it is OriginScopedHeadersInterceptor } }
            .addNetworkInterceptor(OriginScopedHeadersInterceptor { scoped })
            .build()
    }

    private fun normalizeAddress(address: String): String = normalizeServerAddress(address)
}

/**
 * The address as stored: trimmed, with one trailing slash, and `https://` when no scheme was typed (D5), so a bare host
 * never falls back to plain HTTP silently. Typing `http://` keeps plain HTTP, for servers on the home network.
 */
internal fun normalizeServerAddress(address: String): String {
    val trimmed = address.trim()
    val withScheme =
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    return withScheme.trimEnd('/') + "/"
}

/** Whether [address] would be reached over plain HTTP, so the password and music travel unencrypted. */
internal fun isPlainHttp(address: String): Boolean = normalizeServerAddress(address).startsWith("http://", ignoreCase = true)
