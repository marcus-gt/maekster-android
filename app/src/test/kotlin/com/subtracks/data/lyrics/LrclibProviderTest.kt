package com.subtracks.data.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.MaeksterBrand
import com.subtracks.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class LrclibProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: LrclibProvider
    private val clock = AtomicLong(1000)
    private val pauses = CopyOnWriteArrayList<Long>()
    private val song = Song(42, "private-server-id", null, null, "  夜 & Day  ", " Album ", " Artist ", 123, null, null, null)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider =
            LrclibProvider(server.url("/api/get"), clock::get, { 1.0 }) { milliseconds ->
                pauses += milliseconds
                clock.addAndGet(milliseconds)
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun sendsOnlyPublicMetadataAndBrandIdentification() =
        runBlocking {
            server.enqueue(response("""{"plainLyrics":"Plain","syncedLyrics":"[00:01.25]Timed","instrumental":false}"""))
            val result = provider.fetch(song)
            assertEquals(listOf(LyricsLine("Timed", 1250)), result[0].lines)
            assertEquals(listOf(LyricsLine("Plain")), result[1].lines)
            val request = server.takeRequest()
            val url = request.requestUrl!!
            assertEquals("/api/get", url.encodedPath)
            assertEquals(setOf("track_name", "artist_name", "album_name", "duration"), url.queryParameterNames)
            assertEquals("夜 & Day", url.queryParameter("track_name"))
            assertEquals("Artist", url.queryParameter("artist_name"))
            assertEquals("Album", url.queryParameter("album_name"))
            assertEquals("123", url.queryParameter("duration"))
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals(null, request.getHeader("Cookie"))
            assertEquals(null, request.getHeader("X-Server-Token"))
            assertTrue(request.getHeader("User-Agent")!!.contains(MaeksterBrand.CLIENT_ID))
            assertTrue(request.getHeader("User-Agent")!!.contains(MaeksterBrand.REPOSITORY_URL))
            assertFalse(request.path!!.contains(song.id))
            assertEquals("application/json", request.getHeader("Accept"))
        }

    @Test
    fun omitsInvalidOptionalMetadataAndSkipsMissingRequiredMetadata() =
        runBlocking {
            for (duration in listOf(null, -1L, 0L, 3601L)) {
                clock.addAndGet(1000)
                server.enqueue(response("""{"instrumental":true}"""))
                provider.fetch(song.copy(album = " ", duration = duration))
                val url = server.takeRequest().requestUrl!!
                assertEquals(setOf("track_name", "artist_name"), url.queryParameterNames)
            }
            assertEquals(emptyList<Lyrics>(), provider.fetch(song.copy(artist = null)))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song.copy(title = " ")))
            assertEquals(4, server.requestCount)
        }

    // UX-26 (#56): a server album name such as "… (digital)" misses LRCLIB's exact match although the song is there.
    @Test
    fun anAlbumMissRetriesOnceWithoutTheAlbumButKeepsTheDuration() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(response("""{"syncedLyrics":"[00:02.00]Timed","instrumental":false}"""))
            assertEquals(listOf(Lyrics(listOf(LyricsLine("Timed", 2000)), synced = true)), provider.fetch(song))
            assertEquals("Album", server.takeRequest().requestUrl!!.queryParameter("album_name"))
            val retry = server.takeRequest().requestUrl!!
            assertEquals(setOf("track_name", "artist_name", "duration"), retry.queryParameterNames)
            assertEquals("夜 & Day", retry.queryParameter("track_name"))
            assertEquals("Artist", retry.queryParameter("artist_name"))
            assertEquals("123", retry.queryParameter("duration"))
            assertEquals(listOf(250L), pauses)
        }

    @Test
    fun theAlbumRetryNeedsADurationAndHappensOnlyOnce() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song))
            assertEquals(2, server.requestCount)
            clock.addAndGet(1000)
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(emptyList<Lyrics>(), provider.fetch(song.copy(duration = null)))
            assertEquals(3, server.requestCount)
            clock.addAndGet(1000)
            server.enqueue(MockResponse().setResponseCode(500))
            assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
            assertEquals(4, server.requestCount)
        }

    @Test
    fun instrumentalAndPlainFallbackDecodeWithoutInventingTimedText() =
        runBlocking {
            server.enqueue(response("""{"instrumental":true,"plainLyrics":null,"syncedLyrics":null}"""))
            assertEquals(listOf(Lyrics(emptyList(), synced = false, instrumental = true)), provider.fetch(song))
            clock.addAndGet(1000)
            server.enqueue(response("""{"instrumental":false,"plainLyrics":"夜\n\nAgain","syncedLyrics":"Malformed"}"""))
            assertEquals(listOf(Lyrics(listOf(LyricsLine("夜"), LyricsLine("Again")), synced = false)), provider.fetch(song))
        }

    @Test
    fun notFoundIsEmptyAndOtherStatusesOrBadDataSurfaceWithoutFollowingRedirects() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(emptyList<Lyrics>(), runBlocking { provider.fetch(song.copy(album = null)) })
        for (status in listOf(302, 401, 500)) {
            clock.addAndGet(1000)
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", server.url("/must-not-follow")))
            val error = assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
            assertEquals(status, error.status)
        }
        for (body in listOf("invalid", "{}", """{"instrumental":"true"}""", """{"plainLyrics":123}""")) {
            clock.addAndGet(1000)
            server.enqueue(response(body))
            assertThrows(IOException::class.java) { runBlocking { provider.fetch(song) } }
        }
        clock.addAndGet(1000)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Unexpected</html>"))
        assertThrows(IOException::class.java) { runBlocking { provider.fetch(song) } }
        assertEquals(9, server.requestCount)
    }

    @Test
    fun rateLimitHonorsRetryAfterWithJitterBeforeAnySubsequentRequest() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
            val first = assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
            assertEquals(2600L, first.retryAfterMs)
            clock.set(3599)
            assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
            assertEquals(1, server.requestCount)
            clock.set(3600)
            server.enqueue(response("""{"instrumental":true}"""))
            assertTrue(provider.fetch(song).single().instrumental)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun busyRetryAfterAlsoPreventsImmediateRequests() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "1"))
        assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
        val blocked = assertThrows(LrclibHttpException::class.java) { runBlocking { provider.fetch(song) } }
        assertEquals(503, blocked.status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun requestsAreSerializedAndSpacedAfterCompletion() =
        runBlocking {
            val release = CountDownLatch(1)
            val count = AtomicInteger()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (count.getAndIncrement() == 0) release.await(3, TimeUnit.SECONDS)
                        return response("""{"instrumental":true}""")
                    }
                }
            val first = async(Dispatchers.Default) { provider.fetch(song) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            val second = async(Dispatchers.Default) { provider.fetch(song) }
            try {
                assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            assertTrue(first.await().single().instrumental)
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertTrue(second.await().single().instrumental)
            assertEquals(listOf(250L), pauses.toList())
        }

    @Test
    fun cancellationCancelsTheInFlightCallPromptly() =
        runBlocking {
            server.enqueue(response("""{"instrumental":true}""").setBodyDelay(2, TimeUnit.SECONDS))
            val completed = AtomicBoolean()
            val job =
                launch(Dispatchers.Default) {
                    provider.fetch(song)
                    completed.set(true)
                }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(1000) { job.cancelAndJoin() }
            assertFalse(completed.get())
            assertEquals(1, server.requestCount)
        }
}
