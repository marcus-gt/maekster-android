package com.subtracks.data.listenbrainz

import com.subtracks.MaeksterBrand
import com.subtracks.UserAgent
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [ListenBrainzApi] over OkHttp. The token is sent only in the Authorization header and never logged. */
class OkHttpListenBrainzApi(
    private val http: OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build(),
) : ListenBrainzApi {
    override suspend fun validateToken(
        root: String,
        token: String,
    ): TokenValidation {
        val request = authorized(endpoint(root, "1/validate-token"), token).get().build()
        val response = execute(request)
        return when (response.status) {
            200 -> {
                try {
                    val json = JSONObject(response.body.orEmpty())
                    val valid = json.optBoolean("valid", false)
                    TokenValidation(valid, json.optString("user_name").takeIf { valid && it.isNotEmpty() })
                } catch (failure: JSONException) {
                    throw ListenBrainzException.Decoding(failure)
                }
            }

            401 -> {
                TokenValidation(false, null)
            }

            else -> {
                throw failure(response)
            }
        }
    }

    override suspend fun playingNow(
        root: String,
        token: String,
        track: ListenTrack,
    ) = submit(root, token, "playing_now", listOf(payload(null, track)))

    override suspend fun single(
        root: String,
        token: String,
        listen: Listen,
    ) = submit(root, token, "single", listOf(payload(listen.listenedAt, listen.track)))

    override suspend fun import(
        root: String,
        token: String,
        listens: List<Listen>,
    ) {
        require(listens.size <= ListenBrainzApi.MAX_LISTENS_PER_REQUEST) { "Too many listens in one request" }
        if (listens.isEmpty()) return
        submit(root, token, "import", listens.map { payload(it.listenedAt, it.track) })
    }

    private suspend fun submit(
        root: String,
        token: String,
        type: String,
        payload: List<JSONObject>,
    ) {
        val body = JSONObject().put("listen_type", type).put("payload", JSONArray(payload)).toString()
        val request =
            authorized(endpoint(root, "1/submit-listens"), token)
                .post(body.toRequestBody(JSON))
                .build()
        val response = execute(request)
        if (response.status != 200) throw failure(response)
    }

    private fun payload(
        listenedAt: Long?,
        track: ListenTrack,
    ): JSONObject {
        val info =
            JSONObject()
                .put("submission_client", MaeksterBrand.NAME)
                .put("media_player", MaeksterBrand.NAME)
        track.durationMs?.takeIf { it > 0 }?.let { info.put("duration_ms", it) }
        val metadata =
            JSONObject()
                .put("track_name", track.trackName)
                .put("artist_name", track.artistName)
                .put("additional_info", info)
        track.releaseName?.takeIf { it.isNotBlank() }?.let { metadata.put("release_name", it) }
        return JSONObject().apply {
            listenedAt?.let { put("listened_at", it) }
            put("track_metadata", metadata)
        }
    }

    private fun authorized(
        url: HttpUrl,
        token: String,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("Authorization", "Token $token")
            .header("User-Agent", UserAgent.value)
            .header("Accept", "application/json")

    private fun failure(response: Payload): ListenBrainzException =
        when (response.status) {
            401 -> ListenBrainzException.Unauthorized()
            429 -> ListenBrainzException.RateLimited(response.retryAfter?.trim()?.toLongOrNull())
            else -> ListenBrainzException.Http(response.status)
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
                        if (continuation.isActive) continuation.resumeWithException(ListenBrainzException.Network(e))
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val payload = response.use { Payload(it.code, it.body.string(), it.header("Retry-After")) }
                            if (continuation.isActive) continuation.resume(payload)
                        } catch (failure: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(ListenBrainzException.Network(failure))
                        }
                    }
                },
            )
        }

    private class Payload(
        val status: Int,
        val body: String?,
        val retryAfter: String?,
    )

    companion object {
        private val JSON = "application/json".toMediaType()

        /** [path] under [root], keeping any path the root has (a self-hosted server may live under one). */
        fun endpoint(
            root: String,
            path: String,
        ): HttpUrl {
            val base = root.trim().trimEnd('/').toHttpUrlOrNull() ?: throw ListenBrainzException.InvalidAddress()
            return base.newBuilder().addPathSegments(path).build()
        }
    }
}
