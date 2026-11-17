package com.subtracks.data.artistimages

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class WikidataTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpWikidata

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpWikidata(server.url("/w/api.php"), userAgent = { "maekster/1.0 ( https://example.org/maekster )" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun statement(
        file: Any,
        rank: String = "normal",
    ): String {
        val value = if (file is String) "\"$file\"" else file.toString()
        return """{"mainsnak":{"snaktype":"value","datavalue":{"value":$value,"type":"string"}},"rank":"$rank"}"""
    }

    @Test
    fun readsOnlyTheImageClaimAndIdentifiesTheAppWithoutCredentials() =
        runBlocking {
            server.enqueue(json("""{"claims":{"P18":[${statement("Björk Cannes 2000.jpg")}]}}"""))

            assertEquals("Björk Cannes 2000.jpg", client.imageFile("Q129"))
            val request = server.takeRequest()
            val url = request.requestUrl!!
            assertEquals("/w/api.php", url.encodedPath)
            assertEquals("wbgetclaims", url.queryParameter("action"))
            assertEquals("Q129", url.queryParameter("entity"))
            assertEquals("P18", url.queryParameter("property"))
            assertEquals("json", url.queryParameter("format"))
            assertEquals("maekster/1.0 ( https://example.org/maekster )", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }

    @Test
    fun aPreferredImageWinsAndDeprecatedOrMalformedOnesAreSkipped() =
        runBlocking {
            server.enqueue(
                json(
                    """{"claims":{"P18":[${statement("Old.jpg", "deprecated")},${statement("Normal.jpg")},
                    ${statement("Preferred.jpg", "preferred")}]}}""",
                ),
            )
            assertEquals("Preferred.jpg", client.imageFile("Q1"))

            server.enqueue(
                json(
                    """{"claims":{"P18":[${statement(org.json.JSONObject().put("id", 1))},${statement("  ")},${statement("Good.jpg")}]}}""",
                ),
            )
            assertEquals("Good.jpg", client.imageFile("Q1"))

            server.enqueue(json("""{"claims":{"P18":[${statement("Gone.jpg", "deprecated")}]}}"""))
            assertNull(client.imageFile("Q1"))
        }

    @Test
    fun noImageOrNoItemIsADefiniteMiss() =
        runBlocking {
            server.enqueue(json("""{"claims":{}}"""))
            assertNull(client.imageFile("Q1"))
            server.enqueue(json("""{"error":{"code":"no-such-entity","info":"Could not find an entity with the ID \"Q9\"."}}"""))
            assertNull(client.imageFile("Q9"))
        }

    @Test
    fun anythingButAnItemIdMakesNoRequest() =
        runBlocking {
            for (id in listOf("", "P18", "Q0", "Q12&action=edit", "q392", "Q392/x")) assertNull(client.imageFile(id))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun httpErrorsApiErrorsRedirectsAndBadJsonFail() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(503, assertThrows(WikidataHttpException::class.java) { runBlocking { client.imageFile("Q1") } }.status)

        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")))
        assertEquals(302, assertThrows(WikidataHttpException::class.java) { runBlocking { client.imageFile("Q1") } }.status)
        assertEquals(2, server.requestCount)

        server.enqueue(json("""{"error":{"code":"maxlag"}}"""))
        assertThrows(IOException::class.java) { runBlocking { client.imageFile("Q1") } }

        server.enqueue(json("not json"))
        assertThrows(IOException::class.java) { runBlocking { client.imageFile("Q1") } }
    }
}
