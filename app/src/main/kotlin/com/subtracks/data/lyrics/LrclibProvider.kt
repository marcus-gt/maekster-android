package com.subtracks.data.lyrics

import android.os.SystemClock
import com.subtracks.MaeksterBrand
import com.subtracks.UserAgent
import com.subtracks.data.model.Song
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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

class LrclibHttpException(
    val status: Int,
    val retryAfterMs: Long? = null,
) : IOException("LRCLIB request failed: HTTP $status")

class LrclibProvider internal constructor(
    private val endpoint: HttpUrl,
    private val clock: () -> Long,
    private val jitter: () -> Double,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : LyricsProvider {
    constructor() : this("https://lrclib.net/api/get".toHttpUrl(), SystemClock::elapsedRealtime, { Random.nextDouble() })

    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    private val requests = Mutex()
    private var nextRequestAt = 0L
    private var blockedUntil = 0L
    private var blockedStatus = 429

    /**
     * Looks the song up by title, artist, album and duration. Server album names often differ from LRCLIB's ("… (digital)",
     * a compilation), so after a miss with an album it tries once more without it, still matched by duration.
     */
    override suspend fun fetch(song: Song): List<Lyrics> {
        val title = song.title.trim()
        val artist = song.artist?.trim().orEmpty()
        if (title.isEmpty() || artist.isEmpty()) return emptyList()
        val album = song.album?.trim()?.takeIf { it.isNotEmpty() }
        val duration = song.duration?.takeIf { it in 1..3600 }
        val exact = lookup(url(title, artist, album, duration))
        if (exact != null || album == null || duration == null) return exact.orEmpty()
        return lookup(url(title, artist, null, duration)).orEmpty()
    }

    private fun url(
        title: String,
        artist: String,
        album: String?,
        duration: Long?,
    ): HttpUrl =
        endpoint
            .newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                album?.let { addQueryParameter("album_name", it) }
                duration?.let { addQueryParameter("duration", it.toString()) }
            }.build()

    /** The lyrics for [url], or null when LRCLIB has no such track. */
    private suspend fun lookup(url: HttpUrl): List<Lyrics>? =
        requests.withLock {
            if (blockedUntil > clock()) throw LrclibHttpException(blockedStatus, blockedUntil - clock())
            if (nextRequestAt > clock()) pause(nextRequestAt - clock())
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", UserAgent.value + " (" + MaeksterBrand.REPOSITORY_URL + ")")
                        .header("Accept", "application/json")
                        .build()
                val response = execute(request)
                if (response.status == 429 || (response.status == 503 && response.retryAfter != null)) {
                    val seconds = response.retryAfter?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 60.0
                    val cooldown = (seconds.coerceAtLeast(1.0) * 1000 * (1 + jitter().coerceIn(0.0, 1.0) * 0.3)).toLong()
                    blockedUntil = deadline(clock(), cooldown)
                    blockedStatus = response.status
                    throw LrclibHttpException(response.status, cooldown)
                }
                when (response.status) {
                    200 -> withContext(Dispatchers.Default) { decode(response.body!!) }
                    404 -> null
                    else -> throw LrclibHttpException(response.status)
                }
            } finally {
                nextRequestAt = deadline(clock(), 250)
            }
        }

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
                            val payload =
                                response.use {
                                    val body =
                                        if (it.code == 200) {
                                            val mediaType = it.body.contentType()
                                            if (mediaType?.type != "application" ||
                                                (mediaType.subtype != "json" && !mediaType.subtype.endsWith("+json"))
                                            ) {
                                                throw IOException("Invalid LRCLIB content type")
                                            }
                                            it.body.string()
                                        } else {
                                            null
                                        }
                                    Payload(it.code, body, it.header("Retry-After"))
                                }
                            if (continuation.isActive) continuation.resume(payload)
                        } catch (failure: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(failure)
                        }
                    }
                },
            )
        }

    private fun decode(body: String): List<Lyrics> =
        try {
            val record = JSONObject(body)
            if (!record.has("syncedLyrics") && !record.has("plainLyrics") &&
                !record.has("instrumental")
            ) {
                throw JSONException("Missing lyrics fields")
            }
            val instrumental =
                if (record.has("instrumental")) {
                    record.get("instrumental") as? Boolean
                        ?: throw JSONException("Invalid instrumental flag")
                } else {
                    false
                }
            val timed = record.text("syncedLyrics")?.let(LrcParser::parse)
            val plain =
                record
                    .text(
                        "plainLyrics",
                    )?.lineSequence()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.map { LyricsLine(it) }
                    ?.toList()
            buildList {
                timed?.let { add(it) }
                if (!plain.isNullOrEmpty()) add(Lyrics(plain, synced = false))
                if (isEmpty() && instrumental) add(Lyrics(emptyList(), synced = false, instrumental = true))
            }
        } catch (failure: JSONException) {
            throw IOException("Invalid LRCLIB response", failure)
        }

    private fun JSONObject.text(key: String): String? =
        if (!has(key) || isNull(key)) null else get(key) as? String ?: throw JSONException("Invalid lyrics text")

    private fun deadline(
        now: Long,
        delay: Long,
    ): Long = if (delay > Long.MAX_VALUE - now) Long.MAX_VALUE else now + delay

    private data class Payload(
        val status: Int,
        val body: String?,
        val retryAfter: String?,
    )
}
