package com.subtracks.data.musicbrainz

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class MusicBrainzTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpMusicBrainz
    private val clock = AtomicLong(10_000)
    private val limiterPauses = CopyOnWriteArrayList<Long>()
    private val retryPauses = CopyOnWriteArrayList<Long>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val limiter =
            MusicBrainzRateLimiter(clock::get, { milliseconds ->
                limiterPauses += milliseconds
                clock.addAndGet(milliseconds)
            })
        client =
            OkHttpMusicBrainz(server.url("/ws/2/"), limiter, pause = { milliseconds ->
                retryPauses += milliseconds
                clock.addAndGet(milliseconds)
            }, userAgent = { "maekster/1.0 ( https://example.org/maekster )" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun group(
        id: String,
        title: String,
        date: String = "2026-10-01",
    ) = """{"id":"$id","title":"$title","primary-type":"Album","secondary-types":["Live"],"first-release-date":"$date",
        "artist-credit":[{"name":"The Avalanches","joinphrase":" & ","artist":{"id":"a1","name":"The Avalanches"}},
        {"name":"John Glacier","artist":{"id":"a2","name":"John Glacier"}}]}"""

    @Test
    fun releaseGroupsQueryEveryArtistInTheWindowAndReadEveryPage() =
        runBlocking {
            server.enqueue(json("""{"count":3,"release-groups":[${group("g1", "One")},${group("g2", "Two")}]}"""))
            server.enqueue(json("""{"count":3,"release-groups":[${group("g3", "Three")}]}"""))

            val groups = client.releaseGroups(listOf("a1", "a2"), "2026-08-01", "2026-10-30")

            assertEquals(listOf("g1", "g2", "g3"), groups.map { it.id })
            val first = groups.first()
            assertEquals("Album", first.primaryType)
            assertEquals(listOf("Live"), first.secondaryTypes)
            assertEquals("2026-10-01", first.firstReleaseDate)
            assertEquals("The Avalanches & John Glacier", first.creditName)
            assertEquals(listOf("a1", "a2"), first.credits.map { it.artistId })

            val request = server.takeRequest().requestUrl!!
            assertEquals("/ws/2/release-group", request.encodedPath)
            assertEquals("(arid:a1 OR arid:a2) AND firstreleasedate:[2026-08-01 TO 2026-10-30]", request.queryParameter("query"))
            assertEquals("100", request.queryParameter("limit"))
            assertEquals("0", request.queryParameter("offset"))
            assertEquals("json", request.queryParameter("fmt"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("offset"))
        }

    @Test
    fun requestsIdentifyTheAppAndCarryNoServerCredentials() =
        runBlocking {
            server.enqueue(json("""{"count":0,"release-groups":[]}"""))
            client.releaseGroups(listOf("a1"), "2026-01-01", "2026-02-01")
            val request = server.takeRequest()
            assertEquals("maekster/1.0 ( https://example.org/maekster )", request.getHeader("User-Agent"))
            assertEquals("application/json", request.getHeader("Accept"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertEquals(setOf("fmt", "limit", "offset", "query"), request.requestUrl!!.queryParameterNames)
        }

    @Test
    fun manyArtistsAreSplitIntoQueriesOfTwentyFive() =
        runBlocking {
            repeat(2) { server.enqueue(json("""{"count":0,"release-groups":[]}""")) }
            client.releaseGroups(List(30) { "artist-$it" }, "2026-01-01", "2026-02-01")
            assertEquals(
                25,
                server
                    .takeRequest()
                    .requestUrl!!
                    .queryParameter("query")!!
                    .split(" OR ")
                    .size,
            )
            assertEquals(
                5,
                server
                    .takeRequest()
                    .requestUrl!!
                    .queryParameter("query")!!
                    .split(" OR ")
                    .size,
            )
        }

    @Test
    fun requestsAreSpacedOneSecondApart() =
        runBlocking {
            repeat(3) { server.enqueue(json("""{"count":0,"release-groups":[]}""")) }
            client.releaseGroups(listOf("a1"), "2026-01-01", "2026-02-01")
            client.releaseGroups(listOf("a2"), "2026-01-01", "2026-02-01")
            client.releaseGroups(listOf("a3"), "2026-01-01", "2026-02-01")
            // The first request goes at once; the clock does not move on its own, so each later one waits a full second.
            assertEquals(listOf(1_000L, 1_000L), limiterPauses)
        }

    @Test
    fun aBusyServerIsRetriedWithGrowingWaitsThenGivesUp() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(json("""{"count":0,"release-groups":[]}"""))
            client.releaseGroups(listOf("a1"), "2026-01-01", "2026-02-01")
            assertEquals(listOf(2_000L, 4_000L), retryPauses)
        }
        retryPauses.clear()
        repeat(OkHttpMusicBrainz.MAX_ATTEMPTS) { server.enqueue(MockResponse().setResponseCode(503)) }
        val failure = assertThrows(MusicBrainzHttpException::class.java) { runBlocking { client.artistId("Burial") } }
        assertEquals(503, failure.status)
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L), retryPauses)
    }

    @Test
    fun otherErrorsFailAtOnce() {
        server.enqueue(MockResponse().setResponseCode(400))
        val failure = assertThrows(MusicBrainzHttpException::class.java) { runBlocking { client.artistId("Burial") } }
        assertEquals(400, failure.status)
        assertTrue(retryPauses.isEmpty())
    }

    @Test
    fun anArtistMatchesOnlyWhenItIsCertain() =
        runBlocking {
            server.enqueue(
                json(
                    """{"artists":[{"id":"wrong","name":"Röyksopp Tribute","score":100},
                    {"id":"right","name":"Röyksopp","score":95}]}""",
                ),
            )
            assertEquals("right", client.artistId("Royksopp"))
            assertEquals("artist:\"Royksopp\"", server.takeRequest().requestUrl!!.queryParameter("query"))

            server.enqueue(json("""{"artists":[{"id":"low","name":"Burial","score":80}]}"""))
            assertNull(client.artistId("Burial"))
            server.enqueue(json("""{"artists":[]}"""))
            assertNull(client.artistId("Nobody"))
        }

    @Test
    fun quotesInANameAreEscapedInTheQuery() =
        runBlocking {
            server.enqueue(json("""{"artists":[]}"""))
            client.artistId("The \"Band\"")
            assertEquals("artist:\"The \\\"Band\\\"\"", server.takeRequest().requestUrl!!.queryParameter("query"))
        }
}
