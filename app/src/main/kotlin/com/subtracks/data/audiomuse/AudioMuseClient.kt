package com.subtracks.data.audiomuse

import com.subtracks.UserAgent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AudioMuseClient {
    suspend fun moodScores(itemId: String): AudioMuseMoodScores

    suspend fun warmup(): Boolean

    suspend fun search(
        query: String,
        limit: Int,
    ): List<AudioMuseTrack>

    suspend fun findPath(
        start: AudioMusePathEnd,
        end: AudioMusePathEnd,
        length: Int,
    ): List<AudioMuseTrack>
}

data class AudioMuseTrack(
    val itemId: String,
    val title: String? = null,
    val author: String? = null,
    val album: String? = null,
    val similarity: Double? = null,
) {
    val hasInternalId: Boolean get() = itemId.startsWith("fp_")
}

enum class AudioMuseMood(
    val wireName: String,
) {
    HAPPY("happy"),
    SAD("sad"),
    AGGRESSIVE("aggressive"),
    RELAXED("relaxed"),
    DANCEABLE("danceable"),
}

sealed interface AudioMusePathEnd {
    data class Song(
        val id: String,
    ) : AudioMusePathEnd

    data class Mood(
        val mood: AudioMuseMood,
    ) : AudioMusePathEnd
}

data class AudioMuseStyle(
    val label: String,
    val score: Double,
)

data class AudioMuseMoodScores(
    val values: Map<String, Double>,
    val styles: List<AudioMuseStyle>,
) {
    companion object {
        fun parse(
            otherFeatures: String,
            moodVector: String = "",
        ): AudioMuseMoodScores =
            AudioMuseMoodScores(
                pairs(otherFeatures).associate { it.first.lowercase(java.util.Locale.ROOT) to it.second },
                pairs(moodVector).map { AudioMuseStyle(it.first, it.second) }.sortedByDescending { it.score },
            )

        private fun pairs(text: String): List<Pair<String, Double>> =
            text.split(',').mapNotNull { pair ->
                val parts = pair.split(':', limit = 2)
                val label = parts[0].trim()
                val score = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                if (label.isEmpty() || score == null || !score.isFinite() || score !in 0.0..1.0) null else label to score
            }
    }
}

enum class AudioMuseFailure {
    BAD_REQUEST,
    UNAUTHORIZED,
    NOT_FOUND,
    NOT_ANALYSED,
    HTTP,
}

class AudioMuseHttpException(
    val status: Int,
    val failure: AudioMuseFailure,
) : IOException("AudioMuse request failed: HTTP $status")

/** Unwired transport: callers supply this integration's token, never Subsonic credentials or headers. */
class OkHttpAudioMuseClient(
    baseUrl: String,
    private val token: String? = null,
    private val userAgent: () -> String = { UserAgent.value },
) : AudioMuseClient {
    private val base: HttpUrl =
        baseUrl.trim().toHttpUrl().also {
            require(it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null)
        }
    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private val serverLock = Mutex()
    private var serverResolved = false
    private var selectedServer: String? = null

    private suspend fun server(): String? =
        serverLock.withLock {
            if (!serverResolved) {
                selectedServer =
                    try {
                        val json = execute(request("api/servers").build())
                        optionalText(json, "default_id")
                            ?: json.getJSONArray("servers").optJSONObject(0)?.let {
                                optionalText(it, "server_id") ?: optionalText(it, "name")
                            }
                    } catch (_: IOException) {
                        null
                    } catch (_: JSONException) {
                        null
                    }
                serverResolved = true
            }
            selectedServer
        }

    override suspend fun warmup(): Boolean {
        server()
        return try {
            execute(request("api/clap/warmup").post(ByteArray(0).toRequestBody()).build(), allowEmpty = true)
            true
        } catch (_: IOException) {
            false
        }
    }

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<AudioMuseTrack> {
        require(query.isNotBlank())
        val body = JSONObject().put("query", query).put("limit", limit.coerceIn(1, 500))
        server()?.let { body.put("server", it) }
        val result =
            execute(
                request("api/clap/search").post(body.toString().toRequestBody("application/json".toMediaType())).build(),
            )
        return tracks(result, "results")
    }

    override suspend fun findPath(
        start: AudioMusePathEnd,
        end: AudioMusePathEnd,
        length: Int,
    ): List<AudioMuseTrack> {
        require(start !is AudioMusePathEnd.Mood || end !is AudioMusePathEnd.Mood)
        for (value in listOf(start, end)) if (value is AudioMusePathEnd.Song) require(value.id.isNotBlank())
        val url =
            base
                .newBuilder()
                .addPathSegments("api/find_path")
                .addQueryParameter("max_steps", length.coerceIn(2, 500).toString())
                .addQueryParameter("path_fix_size", "true")
        for ((prefix, value) in listOf("start" to start, "end" to end)) {
            when (value) {
                is AudioMusePathEnd.Song -> url.addQueryParameter("${prefix}_song_id", value.id)
                is AudioMusePathEnd.Mood -> url.addQueryParameter("${prefix}_mood", value.mood.wireName)
            }
        }
        server()?.let { url.addQueryParameter("server", it) }
        return tracks(execute(request("api/find_path").url(url.build()).build()), "path")
    }

    private fun request(path: String): Request.Builder =
        Request
            .Builder()
            .url(base.newBuilder().addPathSegments(path).build())
            .header("User-Agent", userAgent())
            .header("Accept", "application/json")
            .apply {
                token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }

    private fun tracks(
        json: JSONObject,
        key: String,
    ): List<AudioMuseTrack> =
        try {
            val array = json.getJSONArray(key)
            if (array.length() > 500) throw JSONException("Too many tracks")
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id =
                    optionalText(item, "item_id")?.takeIf { it.isNotBlank() }
                        ?: throw JSONException("Missing track id")
                val similarity =
                    item.opt("similarity").let {
                        if (it == null || it == JSONObject.NULL) {
                            null
                        } else {
                            (it as? Number)?.toDouble()?.takeIf(Double::isFinite)
                                ?: throw JSONException("Invalid similarity")
                        }
                    }
                AudioMuseTrack(id, optionalText(item, "title"), optionalText(item, "author"), optionalText(item, "album"), similarity)
            }
        } catch (failure: JSONException) {
            throw IOException("Invalid AudioMuse tracks", failure)
        }

    private fun optionalText(
        json: JSONObject,
        key: String,
    ): String? = if (!json.has(key) || json.isNull(key)) null else text(json, key)

    override suspend fun moodScores(itemId: String): AudioMuseMoodScores {
        require(itemId.isNotBlank())
        val url =
            base
                .newBuilder()
                .addPathSegments("external/get_score")
                .addQueryParameter("id", itemId)
                .build()
        val json = execute(request("external/get_score").url(url).build())
        return try {
            AudioMuseMoodScores.parse(text(json, "other_features"), text(json, "mood_vector"))
        } catch (failure: JSONException) {
            throw IOException("Invalid AudioMuse response", failure)
        }
    }

    private suspend fun execute(
        request: Request,
        allowEmpty: Boolean = false,
    ): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("AudioMuse transport failed", e))
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val scores =
                                response.use {
                                    if (!it.isSuccessful) throw httpFailure(it.code)
                                    val body = it.body.string()
                                    if (allowEmpty && body.isBlank()) JSONObject() else JSONObject(body)
                                }
                            if (continuation.isActive) continuation.resume(scores)
                        } catch (failure: Exception) {
                            val error = if (failure is JSONException) IOException("Invalid AudioMuse response", failure) else failure
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                },
            )
        }

    private fun text(
        json: JSONObject,
        key: String,
    ): String {
        val value = json.opt(key)
        if (value == null || value == JSONObject.NULL) return ""
        if (value !is String) throw JSONException("Invalid score field")
        return value
    }

    private fun httpFailure(status: Int) =
        AudioMuseHttpException(
            status,
            when (status) {
                400 -> AudioMuseFailure.BAD_REQUEST
                401, 403 -> AudioMuseFailure.UNAUTHORIZED
                404 -> AudioMuseFailure.NOT_FOUND
                503 -> AudioMuseFailure.NOT_ANALYSED
                else -> AudioMuseFailure.HTTP
            },
        )
}
