package com.subtracks.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * A custom HTTP header the user set for a server, such as a reverse proxy's access token. The value is a secret:
 * it is stored sealed, and [toString] never shows it.
 */
data class HeaderEntry(
    val name: String,
    val value: String,
) {
    override fun toString(): String = "HeaderEntry(name=$name, value=[REDACTED])"
}

/** What makes a custom header acceptable, mirroring the iOS app's rules plus what OkHttp itself can send. */
object HeaderValidator {
    // Headers the HTTP stack, the player or the app set themselves; overriding them would break requests.
    private val reserved =
        setOf(
            "host",
            "content-length",
            "content-type",
            "transfer-encoding",
            "connection",
            "keep-alive",
            "proxy-connection",
            "te",
            "trailer",
            "upgrade",
            "range",
            "user-agent",
        )

    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~"

    /** An RFC 7230 token: letters, digits and `!#$%&'*+-.^_`|~`, and not empty. */
    fun isValidName(name: String): Boolean = name.isNotEmpty() && name.all { it.isLetterOrDigitAscii() || it in TOKEN_SYMBOLS }

    /**
     * Printable ASCII and tabs only. That rules out CR, LF and NUL (header injection) and also anything OkHttp
     * refuses to send, which would otherwise fail every request at runtime.
     */
    fun isValidValue(value: String): Boolean = value.all { it == '\t' || it in ' '..'~' }

    fun isReserved(name: String): Boolean = name.lowercase() in reserved

    /** The first problem with [headers], or null when every one can be sent. Names compare case-insensitively. */
    fun problem(headers: List<HeaderEntry>): HeaderProblem? {
        val seen = mutableSetOf<String>()
        for (header in headers) {
            val problem =
                when {
                    !isValidName(header.name) -> HeaderProblem.InvalidName
                    isReserved(header.name) -> HeaderProblem.ReservedName
                    !isValidValue(header.value) -> HeaderProblem.InvalidValue
                    !seen.add(header.name.lowercase()) -> HeaderProblem.Duplicate
                    else -> null
                }
            if (problem != null) return problem
        }
        return null
    }

    private fun Char.isLetterOrDigitAscii() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

enum class HeaderProblem { InvalidName, ReservedName, InvalidValue, Duplicate }

/** [headers] for requests to one origin: the same scheme, host and port as the server address. */
class ScopedHeaders(
    serverAddress: String,
    val headers: List<HeaderEntry>,
) {
    private val origin: HttpUrl? = serverAddress.toHttpUrlOrNull()

    fun appliesTo(url: HttpUrl): Boolean {
        val origin = origin ?: return false
        return url.scheme == origin.scheme && url.host.equals(origin.host, ignoreCase = true) && url.port == origin.port
    }

    fun appliesTo(url: String): Boolean = url.toHttpUrlOrNull()?.let(::appliesTo) == true
}

/**
 * The active server's custom headers, shared by every HTTP path: the API client, artwork, streaming and downloads.
 * [SourceRepository][com.subtracks.data.repo.SourceRepository] keeps it current.
 */
class ServerHeaders {
    @Volatile
    private var scoped: ScopedHeaders? = null

    fun set(scoped: ScopedHeaders?) {
        this.scoped = scoped?.takeIf { it.headers.isNotEmpty() }
    }

    /** The headers to send with a request to [url], for clients that cannot run [interceptor] (DownloadManager). */
    fun forUrl(url: String): List<HeaderEntry> = scoped?.takeIf { it.appliesTo(url) }?.headers.orEmpty()

    /**
     * Adds the headers to each request that goes to the server's origin. Install it with `addNetworkInterceptor`:
     * a network interceptor sees every hop, so a redirect to another host is checked again and gets nothing. An
     * application interceptor would not be enough, because OkHttp copies request headers onto redirects.
     */
    fun interceptor(): Interceptor = OriginScopedHeadersInterceptor { scoped }
}

/** Adds [scoped]'s headers to requests for its origin only, replacing any header of the same name. */
class OriginScopedHeadersInterceptor(
    private val scoped: () -> ScopedHeaders?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val current = scoped()?.takeIf { it.appliesTo(request.url) } ?: return chain.proceed(request)
        val builder = request.newBuilder()
        current.headers.forEach { builder.header(it.name, it.value) }
        return chain.proceed(builder.build())
    }
}
