package com.subtracks.data.audiomuse

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import java.io.IOException

sealed interface AudioMuseTestResult {
    /** AudioMuse answered a test search; [matches] may be 0 when nothing has been analysed yet. */
    data class Connected(
        val matches: Int,
    ) : AudioMuseTestResult

    data object Unauthorized : AudioMuseTestResult

    /** Something answered, but not like AudioMuse (wrong path, 404 or an unreadable reply). */
    data object NotAudioMuse : AudioMuseTestResult

    data object Unreachable : AudioMuseTestResult

    /** AudioMuse answered with another error status. */
    data object Failed : AudioMuseTestResult
}

/**
 * Explicit connection check: a warmup and one small fixed search against the given connection. It never reads or
 * writes stored configuration and never surfaces exception details, which could echo the URL.
 */
class AudioMuseConnectionTester(
    private val client: (AudioMuseConnection) -> AudioMuseClient = { OkHttpAudioMuseClient(it.url, it.token) },
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) {
    suspend fun test(connection: AudioMuseConnection): AudioMuseTestResult =
        try {
            withTimeout(timeoutMillis) {
                val api = client(connection)
                api.warmup()
                AudioMuseTestResult.Connected(api.search(TEST_QUERY, TEST_LIMIT).size)
            }
        } catch (_: TimeoutCancellationException) {
            AudioMuseTestResult.Unreachable
        } catch (failure: AudioMuseHttpException) {
            when (failure.failure) {
                AudioMuseFailure.UNAUTHORIZED -> AudioMuseTestResult.Unauthorized
                AudioMuseFailure.NOT_FOUND, AudioMuseFailure.BAD_REQUEST -> AudioMuseTestResult.NotAudioMuse
                AudioMuseFailure.NOT_ANALYSED, AudioMuseFailure.HTTP -> AudioMuseTestResult.Failed
            }
        } catch (failure: IOException) {
            // Coroutine stack-trace recovery may wrap the client's exception, so search the whole chain.
            if (generateSequence(failure.cause) { it.cause }.any { it is JSONException }) {
                AudioMuseTestResult.NotAudioMuse
            } else {
                AudioMuseTestResult.Unreachable
            }
        }

    companion object {
        const val TEST_QUERY = "relaxed mellow laid back"
        const val TEST_LIMIT = 5
        const val TIMEOUT_MILLIS = 20_000L
    }
}
