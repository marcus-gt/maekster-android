package com.subtracks.data.source.subsonic

import com.subtracks.data.lyrics.Lyrics
import com.subtracks.data.lyrics.LyricsLine
import com.subtracks.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SubsonicLyricsProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: SubsonicLyricsProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider =
            SubsonicLyricsProvider(
                1,
                SubsonicClient(
                    server.url("/music/"),
                    "listener",
                    "fixture",
                    false,
                    OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
                ),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun transportFailureDoesNotStartClassicLookup() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThrows(IOException::class.java) { runBlocking { provider.fetch(song()) } }
        assertEquals(1, server.requestCount)
    }

    private fun song() = Song(1, "song/a", null, null, "  Night & Day  ", "Album", "  An Artist  ", 120, null, null, null)

    private fun response(body: String) = MockResponse().setBody("<subsonic-response status=\"ok\">$body</subsonic-response>")

    private fun failure(code: Int) =
        MockResponse().setBody(
            "<subsonic-response status=\"failed\"><error code=\"$code\" message=\"Fixture failure\"/></subsonic-response>",
        )

    @Test
    fun prefersSongIdLyricsAndDoesNotSendMetadataToAnotherEndpoint() =
        runBlocking {
            server.enqueue(
                response("<lyricsList><structuredLyrics synced=\"true\"><line start=\"1000\">Line</line></structuredLyrics></lyricsList>"),
            )
            assertEquals(listOf(Lyrics(listOf(LyricsLine("Line", 1000)), synced = true)), provider.fetch(song()))
            val request = server.takeRequest().requestUrl!!
            assertEquals("/music/rest/getLyricsBySongId.view", request.encodedPath)
            assertEquals("song/a", request.queryParameter("id"))
            assertEquals(null, request.queryParameter("artist"))
            assertEquals(null, request.queryParameter("title"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun emptyResponseFallsBackWithTrimmedAndEncodedArtistAndTitle() =
        runBlocking {
            server.enqueue(response("<lyricsList/>"))
            server.enqueue(response("<lyrics>Plain line</lyrics>"))
            assertEquals(listOf(Lyrics(listOf(LyricsLine("Plain line")), synced = false)), provider.fetch(song()))
            server.takeRequest()
            val request = server.takeRequest().requestUrl!!
            assertEquals("/music/rest/getLyrics.view", request.encodedPath)
            assertEquals("An Artist", request.queryParameter("artist"))
            assertEquals("Night & Day", request.queryParameter("title"))
            assertEquals(null, request.queryParameter("id"))
        }

    @Test
    fun unsupportedVersionGenericAndNotFoundResponsesAllowClassicFallback() =
        runBlocking {
            for (code in listOf(0, 20, 30, 70)) {
                server.enqueue(failure(code))
                server.enqueue(response("<lyrics>Fallback</lyrics>"))
                assertEquals(
                    "Fallback",
                    provider
                        .fetch(song())
                        .single()
                        .lines
                        .single()
                        .text,
                )
            }
            for (status in listOf(404, 405, 501)) {
                server.enqueue(MockResponse().setResponseCode(status))
                server.enqueue(response("<lyrics>Fallback</lyrics>"))
                assertEquals(
                    "Fallback",
                    provider
                        .fetch(song())
                        .single()
                        .lines
                        .single()
                        .text,
                )
            }
            assertEquals(14, server.requestCount)
        }

    @Test
    fun authenticationPermissionServerAndMalformedFailuresDoNotProbeClassicApi() {
        for (code in listOf(10, 40, 43, 44, 50, 60)) {
            server.enqueue(failure(code))
            val failure = assertThrows(SubsonicException::class.java) { runBlocking { provider.fetch(song()) } }
            assertEquals(code, failure.code)
        }
        for (status in listOf(401, 403, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(status))
            val failure = assertThrows(HttpStatusException::class.java) { runBlocking { provider.fetch(song()) } }
            assertEquals(status, failure.status)
        }
        server.enqueue(MockResponse().setBody("<html>Unexpected</html>"))
        assertThrows(SubsonicException::class.java) { runBlocking { provider.fetch(song()) } }
        assertEquals(11, server.requestCount)
    }

    @Test
    fun classicNotFoundIsEmptyButClassicAuthFailureSurfaces() =
        runBlocking {
            server.enqueue(response("<lyricsList/>"))
            server.enqueue(failure(70))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song()))
            server.enqueue(response("<lyricsList/>"))
            server.enqueue(failure(40))
            val failure = assertThrows(SubsonicException::class.java) { runBlocking { provider.fetch(song()) } }
            assertEquals(40, failure.code)
        }

    @Test
    fun cancellationAfterSongIdRequestDoesNotStartClassicLookup() =
        runBlocking {
            server.enqueue(response("<lyricsList/>").setBodyDelay(300, TimeUnit.MILLISECONDS))
            server.enqueue(response("<lyrics>Must not request</lyrics>"))
            val completed = AtomicBoolean()
            val job =
                launch(Dispatchers.Default) {
                    provider.fetch(song())
                    completed.set(true)
                }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertFalse(completed.get())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun rejectsWrongSourceAndSkipsMetadataLookupWithoutArtistOrTitle() =
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.fetch(song().copy(sourceId = 2)) } }
            assertEquals(0, server.requestCount)
            server.enqueue(response("<lyricsList/>"))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song().copy(artist = null)))
            server.enqueue(response("<lyricsList/>"))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song().copy(title = " ")))
            assertEquals(2, server.requestCount)
        }
}
