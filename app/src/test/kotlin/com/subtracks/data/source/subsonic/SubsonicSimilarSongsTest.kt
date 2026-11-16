package com.subtracks.data.source.subsonic

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.MixCandidate
import com.subtracks.data.mix.MixSeed
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubsonicSimilarSongsTest {
    private lateinit var server: MockWebServer
    private lateinit var similar: SubsonicSimilarSongs

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        similar = SubsonicSimilarSongs(SubsonicClient(server.url("/"), "u", "p", useTokenAuth = true, http = OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun answer(tag: String) =
        server.enqueue(
            MockResponse().setBody(
                """<subsonic-response status="ok" version="1.16.1"><$tag>""" +
                    """<song id="s1" artistId="a1" title="One"/><song id="s2" title="Two"/></$tag></subsonic-response>""",
            ),
        )

    @Test
    fun songAndAlbumSeedsAskForSimilarSongs() {
        answer("similarSongs")

        val songs = runBlocking { similar.similar(MixSeed.Song("seed"), 25) }

        assertEquals(listOf(MixCandidate("s1", "a1"), MixCandidate("s2", null)), songs)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/rest/getSimilarSongs.view", url.encodedPath)
        assertEquals("seed", url.queryParameter("id"))
        assertEquals("25", url.queryParameter("count"))
    }

    @Test
    fun artistSeedsAskForSimilarSongs2() {
        answer("similarSongs2")

        val songs = runBlocking { similar.similar(MixSeed.Artist("ar"), 50) }

        assertEquals(listOf("s1", "s2"), songs.map { it.id })
        assertEquals("/rest/getSimilarSongs2.view", server.takeRequest().requestUrl!!.encodedPath)
    }
}
