package com.subtracks.data.listenbrainz

import com.subtracks.data.db.PendingListenDao
import com.subtracks.data.model.PendingListen
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.security.CredentialStore
import com.subtracks.log.Log
import com.subtracks.playback.ListenSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What Settings shows about the ListenBrainz account. */
data class ListenBrainzState(
    val connected: Boolean = false,
    val username: String? = null,
    val root: String = ListenBrainzApi.DEFAULT_ROOT,
    val enabled: Boolean = false,
    /** The stored token can no longer be opened (Keystore reset); the user has to paste it again. */
    val tokenUnreadable: Boolean = false,
    val pending: Int = 0,
)

sealed interface ConnectResult {
    data class Connected(
        val username: String,
    ) : ConnectResult

    data object InvalidToken : ConnectResult

    data class Failed(
        val error: ListenBrainzException,
    ) : ConnectResult
}

/**
 * The ListenBrainz account and its listens (iOS `ListenBrainzService`). `playing_now` goes out when a song
 * starts, a `single` listen at the scrobble threshold. A listen that fails for a transient reason is queued
 * in `pending_listens` and imported later; a permanent failure drops it. Nothing is sent unless the user
 * connected an account and left it enabled.
 */
class ListenBrainzRepository(
    private val api: ListenBrainzApi,
    private val prefs: UserPreferences,
    private val credentials: CredentialStore,
    private val pending: PendingListenDao,
    /** The metadata ListenBrainz needs for a song of the active server, or null if it cannot be named. */
    private val track: suspend (songId: String) -> ListenTrack?,
) : ListenSink {
    private val flushLock = Mutex()

    val state: Flow<ListenBrainzState> =
        combine(prefs.listenBrainz(), pending.count()) { settings, count ->
            val token = settings.tokenCiphertext
            ListenBrainzState(
                connected = token != null,
                username = settings.username,
                root = settings.root,
                enabled = settings.enabled,
                tokenUnreadable = token != null && credentials.decrypt(token) == null,
                pending = count,
            )
        }

    /** Whether listens are sent: an account is connected, readable and enabled. */
    override val active: Flow<Boolean> =
        state.map { it.connected && it.enabled && !it.tokenUnreadable }.distinctUntilChanged()

    /** Checks [token] against [root] and, when valid, stores it sealed and turns scrobbling on. */
    suspend fun connect(
        token: String,
        root: String = ListenBrainzApi.DEFAULT_ROOT,
    ): ConnectResult {
        val cleanToken = token.trim()
        val cleanRoot = root.trim().trimEnd('/').ifEmpty { ListenBrainzApi.DEFAULT_ROOT }
        val validation =
            try {
                api.validateToken(cleanRoot, cleanToken)
            } catch (failure: ListenBrainzException) {
                return ConnectResult.Failed(failure)
            }
        val username = validation.username
        if (!validation.valid || username == null) return ConnectResult.InvalidToken
        val previous = prefs.listenBrainz().first()
        val sameAccount =
            previous.tokenCiphertext?.let(credentials::decrypt) == cleanToken && previous.root.trimEnd('/') == cleanRoot
        // Queued listens belong to the account that played them; never send them to a different one.
        if (!sameAccount) pending.clear()
        prefs.setListenBrainzAccount(credentials.encrypt(cleanToken), cleanRoot, username)
        flush()
        return ConnectResult.Connected(username)
    }

    suspend fun disconnect() {
        prefs.clearListenBrainzAccount()
        pending.clear()
    }

    suspend fun setEnabled(enabled: Boolean) {
        prefs.setListenBrainzEnabled(enabled)
        if (enabled) flush()
    }

    override suspend fun nowPlaying(songId: String) {
        val account = account() ?: return
        val track = track(songId) ?: return
        try {
            api.playingNow(account.root, account.token, track)
        } catch (failure: ListenBrainzException) {
            Log.w("listenbrainz", "playing now failed: ${failure.message}")
        }
    }

    override suspend fun listen(
        songId: String,
        startedAtMs: Long,
    ) {
        val account = account() ?: return
        val track = track(songId) ?: return
        val listen = Listen(startedAtMs / 1000, track)
        try {
            api.single(account.root, account.token, listen)
        } catch (failure: ListenBrainzException) {
            if (failure.transient) {
                pending.insert(listen.toPending())
                Log.i("listenbrainz", "listen queued: ${failure.message}")
            } else {
                Log.w("listenbrainz", "listen dropped: ${failure.message}")
            }
            return
        }
        flush()
    }

    /** Imports queued listens oldest first, in batches; stops at the first failure and keeps what is left. */
    suspend fun flush() =
        flushLock.withLock {
            while (true) {
                val account = account() ?: return@withLock
                val batch = pending.oldest(ListenBrainzApi.MAX_LISTENS_PER_REQUEST)
                if (batch.isEmpty()) return@withLock
                try {
                    api.import(account.root, account.token, batch.map { it.toListen() })
                } catch (failure: ListenBrainzException) {
                    Log.i("listenbrainz", "queue flush failed, kept ${batch.size}: ${failure.message}")
                    // A permanent rejection would block the queue forever; drop that batch instead.
                    if (!failure.transient && failure !is ListenBrainzException.Unauthorized) {
                        pending.delete(batch.map { it.id })
                    }
                    return@withLock
                }
                pending.delete(batch.map { it.id })
                Log.i("listenbrainz", "imported ${batch.size} queued listens")
            }
        }

    private suspend fun account(): Account? {
        val settings = prefs.listenBrainz().first()
        if (!settings.enabled) return null
        val token = settings.tokenCiphertext?.let(credentials::decrypt) ?: return null
        return Account(settings.root, token)
    }

    private class Account(
        val root: String,
        val token: String,
    )
}

/** The ListenBrainz form of a library song; null without a title or an artist, which ListenBrainz requires. */
fun Song.toListenTrack(): ListenTrack? {
    val artistName = artist?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val trackName = title.trim().takeIf { it.isNotEmpty() } ?: return null
    return ListenTrack(
        trackName = trackName,
        artistName = artistName,
        releaseName = album?.trim()?.takeIf { it.isNotEmpty() },
        durationMs = duration?.takeIf { it > 0 }?.times(1000),
    )
}

private fun Listen.toPending() =
    PendingListen(
        listenedAt = listenedAt,
        trackName = track.trackName,
        artistName = track.artistName,
        releaseName = track.releaseName,
        durationMs = track.durationMs,
    )

private fun PendingListen.toListen() = Listen(listenedAt, ListenTrack(trackName, artistName, releaseName, durationMs))
