package com.subtracks.data.source.subsonic

import com.subtracks.data.model.PlaylistSong
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class SubsonicPlaylistWriterTest {
    private lateinit var server: MockWebServer
    private lateinit var writer: SubsonicPlaylistWriter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            SubsonicClient(
                server.url("/music/"),
                "listener",
                "fixture",
                false,
                OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
            )
        writer = SubsonicPlaylistWriter(7, client, batchSize = 2)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String = "") = MockResponse().setBody("<subsonic-response status=\"ok\">$body</subsonic-response>")

    private fun failure(code: Int) =
        MockResponse().setBody("<subsonic-response status=\"failed\"><error code=\"$code\" message=\"Fixture\"/></subsonic-response>")

    private fun RecordedRequest.method() = requestUrl!!.encodedPath.substringAfterLast('/')

    private fun RecordedRequest.values(name: String) = requestUrl!!.queryParameterValues(name)

    @Test
    fun appendSendsSongsInOrderedBatches() =
        runBlocking {
            repeat(2) { server.enqueue(ok()) }
            writer.append("p1", listOf("a", "b", "a"))
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("updatePlaylist.view", first.method())
            assertEquals(listOf("p1"), first.values("playlistId"))
            assertEquals(listOf("a", "b"), first.values("songIdToAdd"))
            assertEquals(listOf("a"), second.values("songIdToAdd"))
            assertEquals(2, server.requestCount)
        }

    @Test
    fun aFailedBatchStopsTheAppend() {
        server.enqueue(ok())
        server.enqueue(failure(0))
        assertThrows(SubsonicException::class.java) { runBlocking { writer.append("p1", listOf("a", "b", "c", "d", "e")) } }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun removeSendsHighestPositionsFirstSoLaterBatchesStayValid() =
        runBlocking {
            repeat(2) { server.enqueue(ok()) }
            writer.remove("p1", listOf(1, 4, 3, 1))
            assertEquals(listOf("4", "3"), server.takeRequest().values("songIndexToRemove"))
            assertEquals(listOf("1"), server.takeRequest().values("songIndexToRemove"))
        }

    @Test
    fun replaceRewritesTheListThenAppendsTheRest() =
        runBlocking {
            repeat(2) { server.enqueue(ok()) }
            writer.replace("p1", listOf("c", "b", "a"))
            val replaced = server.takeRequest()
            assertEquals("createPlaylist.view", replaced.method())
            assertEquals(listOf("p1"), replaced.values("playlistId"))
            assertEquals(listOf("c", "b"), replaced.values("songId"))
            assertEquals(listOf("a"), server.takeRequest().values("songIdToAdd"))
        }

    @Test
    fun replacingWithNothingEmptiesThePlaylist() =
        runBlocking {
            server.enqueue(ok())
            writer.replace("p1", emptyList())
            val request = server.takeRequest()
            assertEquals("createPlaylist.view", request.method())
            assertEquals(emptyList<String>(), request.values("songId"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun renameAndCommentTouchOnlyTheirField() =
        runBlocking {
            repeat(2) { server.enqueue(ok()) }
            writer.rename("p1", "Night drive")
            writer.setComment("p1", "")
            val rename = server.takeRequest()
            assertEquals(listOf("Night drive"), rename.values("name"))
            assertEquals(emptyList<String?>(), rename.values("comment"))
            val comment = server.takeRequest()
            assertEquals(listOf(""), comment.values("comment"))
            assertEquals(emptyList<String?>(), comment.values("name"))
        }

    @Test
    fun createReturnsTheReportedPlaylist() =
        runBlocking {
            server.enqueue(ok("<playlist id=\"p9\" name=\"New\" songCount=\"0\" created=\"2026-11-13T10:00:00Z\"/>"))
            val created = writer.create("New")!!
            assertEquals("p9", created.playlist.id)
            assertEquals(7L, created.playlist.sourceId)
            assertEquals(emptyList<PlaylistSong>(), created.songs)
            assertEquals(listOf("New"), server.takeRequest().values("name"))
        }

    @Test
    fun anOlderServerMayNotReportTheCreatedPlaylist() =
        runBlocking {
            server.enqueue(ok())
            assertNull(writer.create("New"))
        }

    @Test
    fun aFreshReadKeepsOrderAndDuplicatesAndSkipsBlankEntries() =
        runBlocking {
            server.enqueue(
                ok(
                    "<playlist id=\"p1\" name=\"Mix\" songCount=\"3\">" +
                        "<entry id=\"a\" title=\"A\"/><entry id=\"\" title=\"?\"/><entry id=\"b\"/><entry id=\"a\"/></playlist>",
                ),
            )
            val detail = writer.playlist("p1")!!
            assertEquals("Mix", detail.playlist.name)
            assertEquals(
                listOf(PlaylistSong(7, "p1", "a", 0), PlaylistSong(7, "p1", "b", 1), PlaylistSong(7, "p1", "a", 2)),
                detail.songs,
            )
        }

    @Test
    fun batchedReadIsColdAndKeepsAbsolutePositionsAndDuplicateOccurrences() =
        runBlocking {
            val ids = List(251) { "song${it % 7}" }
            server.enqueue(
                ok(
                    "<playlist id=\"p1\" name=\"Mix\" songCount=\"251\">" +
                        ids.joinToString("") { "<entry id=\"$it\"/>" } + "</playlist>",
                ),
            )
            val flow = writer.playlistBatches("p1")
            assertEquals(0, server.requestCount)
            val batches = flow.toList()
            assertEquals(listOf(100, 100, 51), batches.map { it.songs.size })
            assertEquals(ids, batches.flatMap { it.songs }.map { it.songId })
            assertEquals((0L..250L).toList(), batches.flatMap { it.songs }.map { it.position })
            batches.forEach { batch ->
                assertEquals(7L, batch.playlist.sourceId)
                assertEquals("p1", batch.playlist.id)
                assertEquals("Mix", batch.playlist.name)
                batch.songs.forEach {
                    assertEquals(7L, it.sourceId)
                    assertEquals("p1", it.playlistId)
                }
            }
            assertEquals(listOf("p1"), server.takeRequest().values("id"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun batchedReadDistinguishesEmptyFromMissingAndDoesNotEmitAnEmptyTail() =
        runBlocking {
            server.enqueue(ok("<playlist id=\"empty\" name=\"Empty\"/>"))
            val empty = writer.playlistBatches("empty").toList()
            assertEquals(1, empty.size)
            assertEquals("empty", empty.single().playlist.id)
            assertEquals(emptyList<PlaylistSong>(), empty.single().songs)
            server.enqueue(failure(70))
            assertEquals(emptyList<Any>(), writer.playlistBatches("gone").toList())
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(emptyList<Any>(), writer.playlistBatches("gone").toList())
            server.enqueue(ok("<playlist id=\"p1\">" + "<entry id=\"a\"/>".repeat(200) + "</playlist>"))
            assertEquals(listOf(100, 100), writer.playlistBatches("p1").toList().map { it.songs.size })
        }

    @Test
    fun batchedReadCancellationStopsBeforeTheResponseTailAndNextReadStillWorks() =
        runBlocking {
            val truncated = "<playlist id=\"p1\">" + "<entry id=\"a\"/>".repeat(500)
            server.enqueue(ok(truncated))
            assertEquals(
                100,
                writer
                    .playlistBatches("p1")
                    .take(1)
                    .toList()
                    .single()
                    .songs.size,
            )
            server.enqueue(ok("<playlist id=\"p2\"/>"))
            assertEquals(
                "p2",
                writer
                    .playlistBatches("p2")
                    .toList()
                    .single()
                    .playlist.id,
            )
        }

    @Test
    fun batchedReadDoesNotHideFailuresAfterAnEmittedBatch() =
        runBlocking {
            var emitted = 0
            server.enqueue(
                ok(
                    "<playlist id=\"p1\">" + "<entry id=\"a\"/>".repeat(100) +
                        "</playlist><error code=\"70\" message=\"late failure\"/>",
                ),
            )
            val failure = runCatching { writer.playlistBatches("p1").collect { emitted++ } }.exceptionOrNull()
            assertEquals(true, failure is SubsonicException)
            assertEquals(1, emitted)
            server.enqueue(ok("<playlist id=\"p1\">" + "<entry id=\"a\"/>".repeat(150)))
            emitted = 0
            assertEquals(true, runCatching { writer.playlistBatches("p1").collect { emitted++ } }.isFailure)
            assertEquals(1, emitted)
        }

    @Test
    fun batchedReadRejectsUnusableIdentityAndOtherServerFailures() =
        runBlocking {
            for (body in listOf(
                "<playlist id=\"other\"/>",
                "<playlist id=\"p1\"><entry id=\"\"/></playlist>",
                "<playlist id=\"p1\"/><playlist id=\"p1\"/>",
            )) {
                server.enqueue(ok(body))
                assertEquals(true, runCatching { writer.playlistBatches("p1").toList() }.isFailure)
            }
            server.enqueue(failure(50))
            val failure = runCatching { writer.playlistBatches("p1").toList() }.exceptionOrNull()
            assertEquals(true, failure is SubsonicException)
        }

    @Test
    fun aPlaylistTheServerNoLongerHasReadsAsNull() =
        runBlocking {
            server.enqueue(failure(70))
            assertNull(writer.playlist("gone"))
        }

    @Test
    fun deletingAPlaylistThatIsAlreadyGoneSucceeds() =
        runBlocking {
            server.enqueue(failure(70))
            server.enqueue(MockResponse().setResponseCode(404))
            writer.delete("gone")
            writer.delete("gone")
            assertEquals(listOf("gone"), server.takeRequest().values("id"))
        }

    @Test
    fun otherDeleteFailuresAreReported() {
        server.enqueue(failure(50))
        assertThrows(SubsonicException::class.java) { runBlocking { writer.delete("p1") } }
    }
}
