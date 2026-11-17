package com.subtracks.data.audiomuse

import com.subtracks.UserAgent
import kotlinx.coroutines.suspendCancellableCoroutine
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

fun interface AudioMuseClient {
    suspend fun moodScores(itemId: String): AudioMuseMoodScores
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

    override suspend fun moodScores(itemId: String): AudioMuseMoodScores {
        require(itemId.isNotBlank())
        val url =
            base
                .newBuilder()
                .addPathSegments("external/get_score")
                .addQueryParameter("id", itemId)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
        token?.takeIf { it.isNotBlank() }?.let { request.header("Authorization", "Bearer $it") }
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request.build())
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
                                    val json = JSONObject(it.body.string())
                                    AudioMuseMoodScores.parse(text(json, "other_features"), text(json, "mood_vector"))
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
