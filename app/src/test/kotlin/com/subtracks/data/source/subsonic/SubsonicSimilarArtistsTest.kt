package com.subtracks.data.source.subsonic

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.recommendations.SimilarArtist
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubsonicSimilarArtistsTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: SubsonicSimilarArtists

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        provider = SubsonicSimilarArtists(SubsonicClient(server.url("/"), "u", "p", useTokenAuth = true, http = OkHttpClient()))
    }

    @After fun tearDown() = server.shutdown()

    private fun answer(body: String) {
        server.enqueue(
            MockResponse().setBody(
                """<subsonic-response status="ok" version="1.16.1"><artistInfo2>$body</artistInfo2></subsonic-response>""",
            ),
        )
    }

    @Test fun preservesRemoteMetadataAndExternalArtistsInServerOrder() {
        answer(
            """<similarArtist id="a" name="One &amp; Two" coverArt="cover" musicBrainzId="mbid"/>
                <similarArtist id="-1" name="External"/>
                <similarArtist name="No id" coverArt="" musicBrainzId=""/>""",
        )
        assertEquals(
            listOf(SimilarArtist("a", "One & Two", "cover", "mbid"), SimilarArtist(null, "External"), SimilarArtist(null, "No id")),
            runBlocking { provider.similar("seed", 20) },
        )
        val url = server.takeRequest().requestUrl!!
        assertEquals("/rest/getArtistInfo2.view", url.encodedPath)
        assertEquals("seed", url.queryParameter("id"))
        assertEquals("20", url.queryParameter("count"))
        assertEquals("true", url.queryParameter("includeNotPresent"))
    }

    @Test fun skipsBlankNamesSelfAndDuplicatesAndBoundsOversizedResponses() {
        answer(
            """<similarArtist id="seed" name="Self"/><similarArtist id="bad" name=" "/>
                <similarArtist id="a" name="First"/><similarArtist id="a" name="Duplicate"/>
                <similarArtist id="-1" name="External"/><similarArtist id="-1" name="Other"/>""",
        )
        assertEquals(listOf("First", "External"), runBlocking { provider.similar("seed", 2) }.map { it.name })
    }

    @Test fun clampsRequestedCountAndDoesNotRequestForEmptyInputs() {
        assertEquals(emptyList<SimilarArtist>(), runBlocking { provider.similar(" ", 20) })
        assertEquals(emptyList<SimilarArtist>(), runBlocking { provider.similar("seed", 0) })
        assertEquals(0, server.requestCount)
        answer("")
        assertEquals(emptyList<SimilarArtist>(), runBlocking { provider.similar("seed", Int.MAX_VALUE) })
        assertEquals("50", server.takeRequest().requestUrl!!.queryParameter("count"))
    }

    @Test fun errorsAndMalformedResponsesAreNotEmptySuccesses() {
        server.enqueue(
            MockResponse().setBody("""<subsonic-response status="failed"><error code="40" message="Unauthorized"/></subsonic-response>"""),
        )
        assertThrows(SubsonicException::class.java) { runBlocking { provider.similar("seed", 20) } }
        server.enqueue(MockResponse().setBody("<html>Not a Subsonic response</html>"))
        assertThrows(SubsonicException::class.java) { runBlocking { provider.similar("seed", 20) } }
    }
}
