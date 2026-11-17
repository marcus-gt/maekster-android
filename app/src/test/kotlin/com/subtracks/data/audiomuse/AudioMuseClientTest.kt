package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AudioMuseClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = null) = OkHttpAudioMuseClient(server.url("/muse/").toString(), token) { "maekster/test" }

    @Test fun readsScoresWithEncodedIdsAndOnlyIntegrationAuthentication() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"other_features":"happy:0.7,sad:0.2","mood_vector":"pop:0.5,jazz:0.8"}"""))
            val scores = client("muse-secret").moodScores("a &?/ü")
            assertEquals(mapOf("happy" to 0.7, "sad" to 0.2), scores.values)
            assertEquals(listOf(AudioMuseStyle("jazz", 0.8), AudioMuseStyle("pop", 0.5)), scores.styles)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/muse/external/get_score", request.requestUrl!!.encodedPath)
            assertEquals("a &?/ü", request.requestUrl!!.queryParameter("id"))
            assertEquals(setOf("id"), request.requestUrl!!.queryParameterNames)
            assertEquals("Bearer muse-secret", request.getHeader("Authorization"))
            assertEquals("maekster/test", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Cookie"))
        }

    @Test fun optionalScoresAndTokenStayAbsent() =
        runBlocking {
            for (body in listOf("{}", """{"other_features":null,"mood_vector":null}""")) {
                server.enqueue(MockResponse().setBody(body))
                assertEquals(AudioMuseMoodScores(emptyMap(), emptyList()), client().moodScores("id"))
                assertNull(server.takeRequest().getHeader("Authorization"))
            }
        }

    @Test fun parserKeepsValidNamedScoresAndOrdersStyles() {
        val scores =
            AudioMuseMoodScores.parse(
                " Happy:0.2,happy:0.8,party:x,sad:NaN,relaxed:Infinity,no:-0.1,high:1.1,:0.3",
                " pop:0.4,jazz:0.8,broken,invalid:NaN",
            )
        assertEquals(mapOf("happy" to 0.8), scores.values)
        assertEquals(listOf(AudioMuseStyle("jazz", 0.8), AudioMuseStyle("pop", 0.4)), scores.styles)
    }

    @Test fun errorsAreDistinctAndNeverExposeResponseSecrets() {
        val expected =
            mapOf(
                400 to AudioMuseFailure.BAD_REQUEST,
                401 to AudioMuseFailure.UNAUTHORIZED,
                403 to AudioMuseFailure.UNAUTHORIZED,
                404 to AudioMuseFailure.NOT_FOUND,
                503 to AudioMuseFailure.NOT_ANALYSED,
                500 to AudioMuseFailure.HTTP,
            )
        for ((status, failure) in expected) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("private-token"))
            val error = assertThrows(AudioMuseHttpException::class.java) { runBlocking { client().moodScores("id") } }
            assertEquals(status, error.status)
            assertEquals(failure, error.failure)
            assertTrue(!error.message.orEmpty().contains("private-token"))
        }
    }

    @Test fun rejectsMalformedPayloadsInsteadOfReportingEmptyScores() {
        for (body in listOf("not json", "[]", """{"other_features":12}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertThrows(IOException::class.java) { runBlocking { client().moodScores("id") } }
        }
    }

    @Test fun redirectsAreNotFollowedWithTheToken() {
        MockWebServer().use { destination ->
            destination.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/stolen")))
            assertThrows(AudioMuseHttpException::class.java) { runBlocking { client("secret").moodScores("id") } }
            assertEquals(0, destination.requestCount)
        }
    }

    @Test fun invalidConfigurationAndEmptyIdsMakeNoRequests() {
        for (url in listOf("not a url", "ftp://example.org", "https://user:pass@example.org", "https://example.org/?token=x")) {
            assertThrows(IllegalArgumentException::class.java) { OkHttpAudioMuseClient(url) }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client().moodScores(" ") } }
        assertEquals(0, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun coroutineCancellationDoesNotWaitForTheServerTimeout() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(Dispatchers.Default) { client().moodScores("id") }
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
}
