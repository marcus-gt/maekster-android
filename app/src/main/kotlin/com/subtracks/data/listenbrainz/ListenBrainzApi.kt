package com.subtracks.data.listenbrainz

import java.io.IOException

/** What ListenBrainz needs to identify a track. */
data class ListenTrack(
    val trackName: String,
    val artistName: String,
    val releaseName: String? = null,
    val durationMs: Long? = null,
)

/** A completed listen; [listenedAt] is epoch seconds when it started. */
data class Listen(
    val listenedAt: Long,
    val track: ListenTrack,
)

/** The result of checking a token: [username] is the account it belongs to when [valid]. */
data class TokenValidation(
    val valid: Boolean,
    val username: String?,
)

/**
 * A ListenBrainz failure. Only network trouble, rate limits and server errors are [transient], so a listen
 * that fails with one of those is kept for later; anything else (bad token, rejected payload) is dropped.
 * Messages never include the token or the username.
 */
sealed class ListenBrainzException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    abstract val transient: Boolean

    class Network(
        cause: Throwable,
    ) : ListenBrainzException("ListenBrainz network error", cause) {
        override val transient = true
    }

    class RateLimited(
        val retryAfterSeconds: Long?,
    ) : ListenBrainzException("ListenBrainz rate limit") {
        override val transient = true
    }

    class Unauthorized : ListenBrainzException("ListenBrainz rejected the token") {
        override val transient = false
    }

    class Http(
        val status: Int,
    ) : ListenBrainzException("ListenBrainz returned HTTP $status") {
        override val transient = status in 500..599
    }

    class InvalidAddress : ListenBrainzException("Invalid ListenBrainz server address") {
        override val transient = false
    }

    class Decoding(
        cause: Throwable,
    ) : ListenBrainzException("Unreadable ListenBrainz response", cause) {
        override val transient = false
    }
}

/**
 * The ListenBrainz HTTP API used for scrobbling, against a [root] such as `https://api.listenbrainz.org`
 * (a self-hosted server works too). Implementations throw [ListenBrainzException].
 */
interface ListenBrainzApi {
    /** A 401 or `valid: false` is an invalid token, not an error. */
    suspend fun validateToken(
        root: String,
        token: String,
    ): TokenValidation

    suspend fun playingNow(
        root: String,
        token: String,
        track: ListenTrack,
    )

    suspend fun single(
        root: String,
        token: String,
        listen: Listen,
    )

    /** Several past listens at once, each with its own time (the offline queue). */
    suspend fun import(
        root: String,
        token: String,
        listens: List<Listen>,
    )

    companion object {
        const val DEFAULT_ROOT = "https://api.listenbrainz.org"

        /** ListenBrainz accepts at most this many listens per submission. */
        const val MAX_LISTENS_PER_REQUEST = 1000
    }
}
