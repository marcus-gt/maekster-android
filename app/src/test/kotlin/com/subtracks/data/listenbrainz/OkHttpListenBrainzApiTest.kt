package com.subtracks.data.listenbrainz

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric for the real org.json.
@RunWith(AndroidJUnit4::class)
class OkHttpListenBrainzApiTest {
    private lateinit var server: MockWebServer
    private val api = OkHttpListenBrainzApi()
    private val track = ListenTrack("Song", "Artist", "Album", 200_000)

    private val root get() = server.url("/").toString()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun aValidTokenReturnsItsUsernameAndSendsTheTokenHeader() {
        server.enqueue(MockResponse().setBody("""{"code":200,"valid":true,"user_name":"listener"}"""))

        val result = runBlocking { api.validateToken(root, "secret") }

        assertEquals(TokenValidation(true, "listener"), result)
        val request = server.takeRequest()
        assertEquals("/1/validate-token", request.path)
        assertEquals("Token secret", request.getHeader("Authorization"))
    }

    @Test
    fun anInvalidTokenIsAResultNotAnError() {
        server.enqueue(MockResponse().setBody("""{"code":200,"valid":false}"""))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(TokenValidation(false, null), runBlocking { api.validateToken(root, "a") })
        assertEquals(TokenValidation(false, null), runBlocking { api.validateToken(root, "b") })
    }

    @Test
    fun aSingleListenCarriesItsTimeAndTheClientName() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        runBlocking { api.single(root, "secret", Listen(1_700_000_000, track)) }

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/1/submit-listens", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("single", body.getString("listen_type"))
        val listen = body.getJSONArray("payload").getJSONObject(0)
        assertEquals(1_700_000_000L, listen.getLong("listened_at"))
        val metadata = listen.getJSONObject("track_metadata")
        assertEquals("Song", metadata.getString("track_name"))
        assertEquals("Artist", metadata.getString("artist_name"))
        assertEquals("Album", metadata.getString("release_name"))
        val info = metadata.getJSONObject("additional_info")
        assertEquals(200_000L, info.getLong("duration_ms"))
        assertEquals("maekster", info.getString("submission_client"))
        assertEquals("maekster", info.getString("media_player"))
    }

    @Test
    fun playingNowHasNoTimeAndImportSendsEveryListen() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))

        runBlocking {
            api.playingNow(root, "t", ListenTrack("Song", "Artist"))
            api.import(root, "t", listOf(Listen(10, track), Listen(20, track)))
        }

        val now = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("playing_now", now.getString("listen_type"))
        val nowListen = now.getJSONArray("payload").getJSONObject(0)
        assertFalse(nowListen.has("listened_at"))
        assertFalse(nowListen.getJSONObject("track_metadata").has("release_name"))
        val imported = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("import", imported.getString("listen_type"))
        assertEquals(2, imported.getJSONArray("payload").length())
        assertEquals(20L, imported.getJSONArray("payload").getJSONObject(1).getLong("listened_at"))
    }

    @Test
    fun failuresAreSortedIntoTransientAndPermanent() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "7"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(400))

        val unauthorized = assertThrows(ListenBrainzException.Unauthorized::class.java) { submit() }
        val limited = assertThrows(ListenBrainzException.RateLimited::class.java) { submit() }
        val unavailable = assertThrows(ListenBrainzException.Http::class.java) { submit() }
        val rejected = assertThrows(ListenBrainzException.Http::class.java) { submit() }

        assertFalse(unauthorized.transient)
        assertTrue(limited.transient)
        assertEquals(7L, limited.retryAfterSeconds)
        assertTrue(unavailable.transient)
        assertFalse(rejected.transient)
        assertEquals(400, rejected.status)
    }

    @Test
    fun anUnreachableServerIsATransientNetworkFailure() {
        val address = root
        server.shutdown()

        val failure = assertThrows(ListenBrainzException.Network::class.java) { runBlocking { api.validateToken(address, "t") } }

        assertTrue(failure.transient)
    }

    @Test
    fun aRootWithAPathKeepsItAndABadAddressIsPermanent() {
        assertEquals(
            "https://lb.example.org/listenbrainz/1/submit-listens",
            OkHttpListenBrainzApi.endpoint("https://lb.example.org/listenbrainz/", "1/submit-listens").toString(),
        )
        val bad = assertThrows(ListenBrainzException.InvalidAddress::class.java) { OkHttpListenBrainzApi.endpoint("not a url", "1/x") }
        assertFalse(bad.transient)
        assertNull(TokenValidation(false, null).username)
    }

    private fun submit() = runBlocking { api.single(root, "t", Listen(1, track)) }
}
