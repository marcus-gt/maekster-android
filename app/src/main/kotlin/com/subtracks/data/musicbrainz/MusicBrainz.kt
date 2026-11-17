package com.subtracks.data.musicbrainz

import android.os.SystemClock
import com.subtracks.MaeksterBrand
import com.subtracks.UserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A release group from MusicBrainz' search, trimmed to what the fresh-releases feed needs. */
data class MbReleaseGroup(
    val id: String,
    val title: String,
    val primaryType: String? = null,
    val secondaryTypes: List<String> = emptyList(),
    val firstReleaseDate: String? = null,
    val credits: List<MbCredit> = emptyList(),
) {
    /** "The Avalanches & John Glacier": the credit as MusicBrainz writes it. */
    val creditName: String get() = credits.joinToString("") { it.name + it.joinPhrase }
}

/** One artist in a release group's credit, with the text that joins it to the next. */
data class MbCredit(
    val artistId: String,
    val name: String,
    val joinPhrase: String = "",
)

class MusicBrainzHttpException(
    val status: Int,
) : IOException("MusicBrainz request failed: HTTP $status")

/** The MusicBrainz lookups the fresh-releases feed makes. A Kotlin interface so tests and features can use a fake. */
interface MusicBrainz {
    /** Release groups credited to any of [artistIds] first released between [from] and [to] ("YYYY-MM-DD"), all pages. */
    suspend fun releaseGroups(
        artistIds: List<String>,
        from: String,
        to: String,
    ): List<MbReleaseGroup>

    /**
     * The MusicBrainz artist for [name], only when the match is certain: score 90 or more and the same name once
     * case, diacritics and punctuation are ignored. A guess would follow the wrong artist.
     */
    suspend fun artistId(name: String): String?

    companion object {
        /** Artist ids per release-group search; keeps the query URL short. */
        const val ARTISTS_PER_SEARCH = 25
    }
}

/**
 * MusicBrainz allows one request per second per application. Every MusicBrainz request in the app waits for one
 * shared limiter, so features never add up to more than that.
 */
class MusicBrainzRateLimiter internal constructor(
    private val clock: () -> Long,
    private val pause: suspend (Long) -> Unit,
    private val intervalMs: Long = INTERVAL_MS,
) {
    constructor() : this(SystemClock::elapsedRealtime, { delay(it) })

    private val lock = Mutex()
    private var nextAt = Long.MIN_VALUE

    suspend fun awaitPermit() =
        lock.withLock {
            val now = clock()
            if (nextAt > now) pause(nextAt - now)
            nextAt = clock() + intervalMs
        }

    companion object {
        const val INTERVAL_MS = 1_000L
    }
}

/**
 * MusicBrainz over its own OkHttp client: no server credentials, headers or redirects, and a User-Agent naming the
 * app and its homepage as MusicBrainz asks. A 503 (busy, or asked to slow down) is retried with growing waits.
 */
class OkHttpMusicBrainz internal constructor(
    private val base: HttpUrl,
    private val limiter: MusicBrainzRateLimiter,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val userAgent: () -> String = { UserAgent.value + " ( " + MaeksterBrand.REPOSITORY_URL + " )" },
) : MusicBrainz {
    constructor(limiter: MusicBrainzRateLimiter) : this("https://musicbrainz.org/ws/2/".toHttpUrl(), limiter)

    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    override suspend fun releaseGroups(
        artistIds: List<String>,
        from: String,
        to: String,
    ): List<MbReleaseGroup> =
        artistIds.distinct().chunked(MusicBrainz.ARTISTS_PER_SEARCH).flatMap { batch ->
            val query = batch.joinToString(" OR ", "(", ")") { "arid:$it" } + " AND firstreleasedate:[$from TO $to]"
            val all = mutableListOf<MbReleaseGroup>()
            var offset = 0
            for (page in 0 until MAX_PAGES) {
                val json = get("release-group", "query" to query, "limit" to PAGE_SIZE.toString(), "offset" to offset.toString())
                val groups = json.optJSONArray("release-groups").objects().map(::releaseGroup)
                all += groups
                offset += groups.size
                if (groups.isEmpty() || offset >= json.optInt("count", 0)) break
            }
            all
        }

    override suspend fun artistId(name: String): String? {
        if (name.isBlank()) return null
        val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = get("artist", "query" to "artist:\"$escaped\"", "limit" to "3")
        val wanted = NewReleaseMatching.normalisedName(name)
        return json
            .optJSONArray("artists")
            .objects()
            .firstOrNull { it.optInt("score", 0) >= 90 && NewReleaseMatching.normalisedName(it.optString("name")) == wanted }
            ?.getString("id")
    }

    private suspend fun get(
        path: String,
        vararg parameters: Pair<String, String>,
    ): JSONObject {
        val url =
            base
                .newBuilder()
                .addPathSegments(path)
                .apply { parameters.sortedBy { it.first }.forEach { (key, value) -> addQueryParameter(key, value) } }
                .addQueryParameter("fmt", "json")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
                .build()
        for (attempt in 0 until MAX_ATTEMPTS) {
            limiter.awaitPermit()
            val response = execute(request)
            if (response.status == 503 && attempt < MAX_ATTEMPTS - 1) {
                pause(RETRY_DELAY_MS shl attempt)
                continue
            }
            if (response.status !in 200..299 || response.body == null) throw MusicBrainzHttpException(response.status)
            return try {
                withContext(Dispatchers.Default) { JSONObject(response.body) }
            } catch (failure: JSONException) {
                throw IOException("Invalid MusicBrainz response", failure)
            }
        }
        throw MusicBrainzHttpException(503)
    }

    private fun releaseGroup(json: JSONObject) =
        MbReleaseGroup(
            id = json.getString("id"),
            title = json.optString("title"),
            primaryType = json.optNullableString("primary-type"),
            secondaryTypes = json.optJSONArray("secondary-types")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(),
            firstReleaseDate = json.optNullableString("first-release-date")?.takeIf { it.isNotEmpty() },
            credits =
                json.optJSONArray("artist-credit").objects().mapNotNull { credit ->
                    val artist = credit.optJSONObject("artist") ?: return@mapNotNull null
                    MbCredit(
                        artistId = artist.getString("id"),
                        name = credit.optNullableString("name") ?: artist.optString("name"),
                        joinPhrase = credit.optNullableString("joinphrase").orEmpty(),
                    )
                },
        )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    private suspend fun execute(request: Request): Payload =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val payload = response.use { Payload(it.code, if (it.isSuccessful) it.body.string() else null) }
                            if (continuation.isActive) continuation.resume(payload)
                        } catch (failure: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }

    private data class Payload(
        val status: Int,
        val body: String?,
    )

    companion object {
        const val PAGE_SIZE = 100

        /** A safety stop for a query that keeps paging (10,000 release groups). */
        const val MAX_PAGES = 100

        /** Attempts per request when MusicBrainz answers 503, waiting 2, 4, 8 and 16 seconds between them. */
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 2_000L
    }
}
