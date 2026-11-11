package com.subtracks.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerHeadersTest {
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer
    private val holder = ServerHeaders()
    private val http = OkHttpClient.Builder().addNetworkInterceptor(holder.interceptor()).build()
    private val token = HeaderEntry("CF-Access-Client-Secret", "s3cret")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        other.shutdown()
    }

    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().close()

    @Test
    fun requestsToTheServerCarryItsHeaders() {
        holder.set(ScopedHeaders(server.url("/").toString(), listOf(token)))
        server.enqueue(MockResponse())

        get(server.url("/rest/ping").toString())

        assertEquals("s3cret", server.takeRequest().getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun requestsToAnotherOriginGetNone() {
        holder.set(ScopedHeaders(server.url("/").toString(), listOf(token)))
        other.enqueue(MockResponse())

        get(other.url("/cover.jpg").toString())

        assertNull(other.takeRequest().getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun aRedirectToAnotherHostDoesNotTakeTheHeadersAlong() {
        holder.set(ScopedHeaders(server.url("/").toString(), listOf(token)))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/elsewhere")))
        other.enqueue(MockResponse())

        get(server.url("/rest/stream").toString())

        assertEquals("s3cret", server.takeRequest().getHeader("CF-Access-Client-Secret"))
        assertNull("the redirect target must not see the secret", other.takeRequest().getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun aRedirectWithinTheServerKeepsThem() {
        holder.set(ScopedHeaders(server.url("/").toString(), listOf(token)))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/moved")))
        server.enqueue(MockResponse())

        get(server.url("/rest/stream").toString())

        assertEquals("s3cret", server.takeRequest().getHeader("CF-Access-Client-Secret"))
        assertEquals("s3cret", server.takeRequest().getHeader("CF-Access-Client-Secret"))
    }

    @Test
    fun theOriginIsTheSchemeTheHostAndThePort() {
        val scoped = ScopedHeaders("https://music.example:8443/sub/", listOf(token))

        assertTrue(scoped.appliesTo("https://music.example:8443/rest/ping"))
        assertTrue(scoped.appliesTo("https://MUSIC.example:8443/elsewhere"))
        assertFalse(scoped.appliesTo("http://music.example:8443/rest/ping"))
        assertFalse(scoped.appliesTo("https://music.example/rest/ping"))
        assertFalse(scoped.appliesTo("https://evil.example:8443/rest/ping"))
        assertFalse(scoped.appliesTo("https://music.example.evil.example:8443/rest/ping"))
        assertFalse(scoped.appliesTo("not a url"))
    }

    @Test
    fun aCustomHeaderReplacesOneOfTheSameName() {
        holder.set(ScopedHeaders(server.url("/").toString(), listOf(HeaderEntry("Authorization", "Bearer proxy"))))
        server.enqueue(MockResponse())

        http
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/"))
                    .header("Authorization", "Basic app")
                    .build(),
            ).execute()
            .close()

        assertEquals(listOf("Bearer proxy"), server.takeRequest().headers.values("Authorization"))
    }

    @Test
    fun downloadsGetTheHeadersOnlyForTheServer() {
        holder.set(ScopedHeaders("https://music.example/", listOf(token)))

        assertEquals(listOf(token), holder.forUrl("https://music.example/rest/download?id=1"))
        assertEquals(emptyList<HeaderEntry>(), holder.forUrl("https://cdn.example/file"))

        holder.set(null)
        assertEquals(emptyList<HeaderEntry>(), holder.forUrl("https://music.example/rest/download?id=1"))
    }

    @Test
    fun aHeaderNeverPrintsItsValue() {
        assertFalse(token.toString().contains("s3cret"))
    }
}
