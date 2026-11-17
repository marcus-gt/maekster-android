package com.subtracks.data.artistimages

import com.subtracks.MaeksterBrand
import com.subtracks.UserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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

class WikidataHttpException(
    val status: Int,
) : IOException("Wikidata request failed: HTTP $status")

/** The Wikidata lookup external artist images need. A Kotlin interface so tests and features can use a fake. */
fun interface Wikidata {
    /**
     * The Wikimedia Commons file name of [itemId]'s image (property P18), preferring a preferred-rank statement and
     * skipping deprecated ones; null when the item has no image, does not exist, or [itemId] is not an item id.
     */
    suspend fun imageFile(itemId: String): String?
}

/**
 * Wikidata over its own OkHttp client: no server credentials, headers or redirects, and a User-Agent naming the app
 * and its homepage as the Wikimedia User-Agent policy asks. Reads only the P18 claim, not the whole item.
 */
class OkHttpWikidata internal constructor(
    private val endpoint: HttpUrl,
    private val userAgent: () -> String = { UserAgent.value + " ( " + MaeksterBrand.REPOSITORY_URL + " )" },
) : Wikidata {
    constructor() : this("https://www.wikidata.org/w/api.php".toHttpUrl())

    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    override suspend fun imageFile(itemId: String): String? {
        if (!ITEM.matches(itemId)) return null
        val url =
            endpoint
                .newBuilder()
                .addQueryParameter("action", "wbgetclaims")
                .addQueryParameter("entity", itemId)
                .addQueryParameter("property", IMAGE_PROPERTY)
                .addQueryParameter("format", "json")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
                .build()
        val response = execute(request)
        if (response.status !in 200..299 || response.body == null) throw WikidataHttpException(response.status)
        val json =
            try {
                withContext(Dispatchers.Default) { JSONObject(response.body) }
            } catch (failure: JSONException) {
                throw IOException("Invalid Wikidata response", failure)
            }
        json.optJSONObject("error")?.let { error ->
            if (error.optString("code") == "no-such-entity") return null
            throw IOException("Wikidata error: " + error.optString("code"))
        }
        val statements =
            json
                .optJSONObject("claims")
                ?.optJSONArray(IMAGE_PROPERTY)
                .objects()
                .filter { it.optString("rank") != "deprecated" }
                .sortedByDescending { it.optString("rank") == "preferred" }
        return statements.firstNotNullOfOrNull { statement ->
            statement
                .optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")
                ?.opt("value")
                ?.let { it as? String }
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_FILE_NAME && it.none(Char::isISOControl) }
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()

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
        /** Wikidata's "image" property, whose values are Wikimedia Commons file names. */
        const val IMAGE_PROPERTY = "P18"

        /** Commons file names are at most 240 bytes; anything far longer is not a file name. */
        private const val MAX_FILE_NAME = 255

        private val ITEM = Regex("Q[1-9][0-9]*")
    }
}
