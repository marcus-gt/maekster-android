package com.subtracks.data.audiomuse

import com.subtracks.data.db.AudioMuseConfigDao
import com.subtracks.data.model.AudioMuseConfig
import com.subtracks.data.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.GeneralSecurityException

data class AudioMuseConfigurationState(
    val url: String? = null,
    val hasToken: Boolean = false,
    val tokenUnreadable: Boolean = false,
) {
    val available: Boolean get() = url != null && !tokenUnreadable
}

/** Connection secrets intentionally have no generated toString/copy/component methods. */
class AudioMuseConnection(
    val url: String,
    val token: String?,
)

/** Explicit per-source storage only; observing or saving a configuration never contacts AudioMuse. */
class AudioMuseConfiguration(
    private val dao: AudioMuseConfigDao,
    private val credentials: CredentialStore,
) {
    fun observe(sourceId: Long): Flow<AudioMuseConfigurationState> =
        dao.observe(sourceId).map { config ->
            withContext(Dispatchers.IO) {
                AudioMuseConfigurationState(
                    url = config?.url,
                    hasToken = config?.tokenCiphertext != null,
                    tokenUnreadable = config?.tokenCiphertext?.let { credentials.decrypt(it) == null } ?: false,
                )
            }
        }

    /** Replaces URL and token together. A blank token explicitly selects an unauthenticated instance. */
    suspend fun save(
        sourceId: Long,
        url: String,
        token: String = "",
    ) = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeAudioMuseUrl(url)
        val cleanToken = token.trim()
        require(cleanToken.none(Char::isISOControl)) { "Invalid AudioMuse token" }
        val sealed =
            cleanToken.takeIf { it.isNotEmpty() }?.let { secret ->
                credentials.encrypt(secret).also {
                    if (credentials.decrypt(it) != secret) throw GeneralSecurityException("AudioMuse token could not be verified")
                }
            }
        dao.put(AudioMuseConfig(sourceId, cleanUrl, sealed))
    }

    suspend fun connection(sourceId: Long): AudioMuseConnection? =
        withContext(Dispatchers.IO) {
            val config = dao.get(sourceId) ?: return@withContext null
            val token = config.tokenCiphertext?.let { credentials.decrypt(it) ?: return@withContext null }
            AudioMuseConnection(config.url, token)
        }

    suspend fun disconnect(sourceId: Long) = dao.delete(sourceId)
}

internal fun normalizeAudioMuseUrl(value: String): String {
    val clean = value.trim()
    require(clean.isNotEmpty()) { "AudioMuse URL is required" }
    val url = (if ("://" in clean) clean else "https://$clean").toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "AudioMuse URL must not contain credentials, a query or a fragment"
    }
    return url.toString()
}
