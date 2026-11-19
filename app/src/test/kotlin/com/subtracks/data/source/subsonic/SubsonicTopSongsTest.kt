package com.subtracks.data.source.subsonic

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubsonicTopSongsTest {
    @Test fun requestsArtistNameAndCountAndParsesIdsInServerOrder() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """<subsonic-response status="ok"><topSongs>
                        <song id="b" title="Wrong metadata"/><song/><song id="a"/>
                        </topSongs></subsonic-response>""",
                ),
            )
            val remote = SubsonicTopSongs(SubsonicClient(server.url("/"), "u", "p", useTokenAuth = true, http = OkHttpClient()))
            assertEquals(listOf("b", "a"), runBlocking { remote.top("Earth, Wind & Fire", 25) })
            val request = server.takeRequest().requestUrl!!
            assertEquals("/rest/getTopSongs.view", request.encodedPath)
            assertEquals("Earth, Wind & Fire", request.queryParameter("artist"))
            assertEquals("25", request.queryParameter("count"))
        }
    }

    @Test fun aServerErrorDoesNotBecomeAnEmptySuccessfulAnswer() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """<subsonic-response status="failed"><error code="70" message="Unavailable"/></subsonic-response>""",
                ),
            )
            val remote = SubsonicTopSongs(SubsonicClient(server.url("/"), "u", "p", useTokenAuth = true, http = OkHttpClient()))
            assertThrows(SubsonicException::class.java) { runBlocking { remote.top("Artist", 25) } }
        }
    }
}
