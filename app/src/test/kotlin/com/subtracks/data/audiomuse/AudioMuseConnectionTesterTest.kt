package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioMuseConnectionTesterTest {
    private val server = MockWebServer()
    private var search: () -> MockResponse = { MockResponse().setBody("""{"results":[]}""") }
    private val searches = mutableListOf<RecordedRequest>()

    @Before fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.requestUrl!!.encodedPath) {
                        "/muse/api/servers" -> MockResponse().setBody("""{"servers":[]}""")
                        "/muse/api/clap/warmup" -> MockResponse()
                        "/muse/api/clap/search" -> search().also { searches += request }
                        else -> MockResponse().setResponseCode(404)
                    }
            }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun test(token: String? = "secret") =
        runBlocking { AudioMuseConnectionTester().test(AudioMuseConnection(server.url("/muse/").toString(), token)) }

    @Test fun aWorkingInstanceReportsHowManyTracksMatchedASmallFixedSearch() {
        search = { MockResponse().setBody("""{"results":[{"item_id":"a"},{"item_id":"fp_b"}]}""") }
        assertEquals(AudioMuseTestResult.Connected(2), test())
        val request = searches.single()
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(AudioMuseConnectionTester.TEST_QUERY, body.getString("query"))
        assertEquals(AudioMuseConnectionTester.TEST_LIMIT, body.getInt("limit"))
        search = { MockResponse().setBody("""{"results":[]}""") }
        assertEquals(AudioMuseTestResult.Connected(0), test(token = null))
        assertEquals(null, searches.last().getHeader("Authorization"))
    }

    @Test fun eachFailureIsReportedAsItsOwnKind() {
        val cases =
            listOf(
                MockResponse().setResponseCode(401) to AudioMuseTestResult.Unauthorized,
                MockResponse().setResponseCode(403) to AudioMuseTestResult.Unauthorized,
                MockResponse().setResponseCode(404) to AudioMuseTestResult.NotAudioMuse,
                MockResponse().setBody("<html>router login</html>") to AudioMuseTestResult.NotAudioMuse,
                MockResponse().setBody("""{"unexpected":true}""") to AudioMuseTestResult.NotAudioMuse,
                MockResponse().setResponseCode(500) to AudioMuseTestResult.Failed,
                MockResponse().setResponseCode(503) to AudioMuseTestResult.Failed,
            )
        for ((index, case) in cases.withIndex()) {
            search = { case.first }
            assertEquals("case $index", case.second, test())
        }
    }

    @Test fun anUnreachableOrSilentServerIsUnreachable() {
        val url = server.url("/muse/").toString()
        server.shutdown()
        assertEquals(
            AudioMuseTestResult.Unreachable,
            runBlocking { AudioMuseConnectionTester().test(AudioMuseConnection(url, null)) },
        )
        val silent =
            object : AudioMuseClient {
                override suspend fun moodScores(itemId: String) = error("Unexpected")

                override suspend fun warmup(): Boolean = awaitCancellation()

                override suspend fun search(
                    query: String,
                    limit: Int,
                ) = error("Unexpected")

                override suspend fun findPath(
                    start: AudioMusePathEnd,
                    end: AudioMusePathEnd,
                    length: Int,
                ) = error("Unexpected")
            }
        val started = System.nanoTime()
        val result = runBlocking { AudioMuseConnectionTester({ silent }, timeoutMillis = 50).test(AudioMuseConnection(url, null)) }
        assertEquals(AudioMuseTestResult.Unreachable, result)
        assertTrue(System.nanoTime() - started < 5_000_000_000)
    }
}
