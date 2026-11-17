package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AudioMuseSearchPathTest {
    private val server = MockWebServer()
    private lateinit var client: OkHttpAudioMuseClient

    @Before fun setUp() {
        server.start()
        client = OkHttpAudioMuseClient(server.url("/proxy/").toString(), "muse-token") { "maekster/test" }
    }

    @After fun tearDown() = server.shutdown()

    private fun respond(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test fun searchScopesOnceAndPreservesInternalIdsAndMetadata() =
        runBlocking {
            respond("""{"servers":[{"server_id":"first"}],"default_id":"chosen"}""")
            respond("""{"results":[{"item_id":"fp_x","title":"T","author":"A","album":"B","similarity":0.8},{"item_id":"real"}]}""")
            val tracks = client.search("calm & bright", 900)
            assertEquals(AudioMuseTrack("fp_x", "T", "A", "B", 0.8), tracks[0])
            assertTrue(tracks[0].hasInternalId)
            assertFalse(tracks[1].hasInternalId)
            assertEquals("/proxy/api/servers", server.takeRequest().requestUrl!!.encodedPath)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/proxy/api/clap/search", request.requestUrl!!.encodedPath)
            assertEquals("Bearer muse-token", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("chosen", body.getString("server"))
            assertEquals("calm & bright", body.getString("query"))
            assertEquals(500, body.getInt("limit"))
            respond("""{"results":[]}""")
            assertTrue(client.search("calm", 0).isEmpty())
            assertEquals(1, JSONObject(server.takeRequest().body.readUtf8()).getInt("limit"))
            assertEquals(3, server.requestCount)
        }

    @Test fun discoveryFallsBackToFirstIdOrNameAndFailureAllowsUnscopedSearch() =
        runBlocking {
            for (discovery in listOf(
                """{"servers":[{"server_id":"first"}]}""",
                """{"servers":[{"name":"first"}]}""",
                """{"servers":[]}""",
                "invalid",
            )) {
                val fresh = OkHttpAudioMuseClient(server.url("/").toString())
                respond(discovery)
                respond("""{"results":[]}""")
                fresh.search("q", 5)
                server.takeRequest()
                val body = JSONObject(server.takeRequest().body.readUtf8())
                if (discovery.contains("first")) {
                    assertEquals("first", body.getString("server"))
                } else {
                    assertFalse(body.has("server"))
                }
            }
        }

    @Test fun pathEncodesEndpointsAndKeepsServerOrderAndDuplicates() =
        runBlocking {
            respond("""{"servers":[],"default_id":"nav"}""")
            respond("""{"path":[{"item_id":"a"},{"item_id":"b"},{"item_id":"a"}]}""")
            assertEquals(
                listOf("a", "b", "a"),
                client
                    .findPath(
                        AudioMusePathEnd.Song("a &?/ü"),
                        AudioMusePathEnd.Mood(AudioMuseMood.RELAXED),
                        1,
                    ).map { it.itemId },
            )
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            val url = request.requestUrl!!
            assertEquals("/proxy/api/find_path", url.encodedPath)
            assertEquals("a &?/ü", url.queryParameter("start_song_id"))
            assertEquals("relaxed", url.queryParameter("end_mood"))
            assertEquals("nav", url.queryParameter("server"))
            assertEquals("2", url.queryParameter("max_steps"))
            assertEquals("true", url.queryParameter("path_fix_size"))
            respond("""{"path":[]}""")
            client.findPath(AudioMusePathEnd.Mood(AudioMuseMood.HAPPY), AudioMusePathEnd.Song("end"), 900)
            val reverse = server.takeRequest().requestUrl!!
            assertEquals("happy", reverse.queryParameter("start_mood"))
            assertEquals("end", reverse.queryParameter("end_song_id"))
            assertEquals("500", reverse.queryParameter("max_steps"))
        }

    @Test fun rejectsInvalidInputsBeforeDiscovery() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.search(" ", 5) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.findPath(AudioMusePathEnd.Mood(AudioMuseMood.HAPPY), AudioMusePathEnd.Mood(AudioMuseMood.SAD), 5)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.findPath(AudioMusePathEnd.Song(""), AudioMusePathEnd.Song("end"), 5)
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun warmupFailureIsNonfatalAndPathErrorsStayDistinct() =
        runBlocking {
            respond("{}", 503)
            respond("{}", 400)
            assertFalse(client.warmup())
            server.takeRequest()
            assertEquals("/proxy/api/clap/warmup", server.takeRequest().requestUrl!!.encodedPath)
            respond("""{"path":[]}""", 404)
            val error =
                assertThrows(AudioMuseHttpException::class.java) {
                    runBlocking {
                        client.findPath(AudioMusePathEnd.Song("a"), AudioMusePathEnd.Song("b"), 5)
                    }
                }
            assertEquals(AudioMuseFailure.NOT_FOUND, error.failure)
            server.takeRequest()
            respond("")
            assertTrue(client.warmup())
            assertEquals("POST", server.takeRequest().method)
        }

    @Test fun malformedTracksFailInsteadOfBecomingEmptySuccess() =
        runBlocking {
            respond("""{"servers":[]}""")
            for (body in listOf("{}", """{"results":[{}]}""", """{"results":[{"item_id":"x","author":3}]}""")) {
                respond(body)
                assertThrows(IOException::class.java) { runBlocking { client.search("q", 5) } }
            }
        }

    @Test(timeout = 10_000)
    fun cancellingDiscoveryDoesNotStartSearchOrCacheAFailure() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(Dispatchers.Default) { client.search("q", 5) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            respond("""{"servers":[],"default_id":"retry"}""")
            respond("""{"results":[]}""")
            client.search("q", 5)
            assertEquals("/proxy/api/servers", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("retry", JSONObject(server.takeRequest().body.readUtf8()).getString("server"))
            assertEquals(3, server.requestCount)
        }
}
