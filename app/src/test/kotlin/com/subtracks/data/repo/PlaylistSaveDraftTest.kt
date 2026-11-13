package com.subtracks.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Source
import com.subtracks.data.source.FakePlaylistWriter
import com.subtracks.data.source.PlaylistWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PlaylistSaveDraftTest {
    /** The fake server, failing the [failAppend]th append (1-based) before or, if [loseResponse], after it lands. */
    private class FlakyWriter(
        val server: FakePlaylistWriter,
    ) : PlaylistWriter by server {
        var appends = 0
        var failAppend = 0
        var loseResponse = false

        override suspend fun append(
            playlistId: String,
            songIds: List<String>,
        ) {
            appends++
            if (appends == failAppend && !loseResponse) throw IOException("connection lost")
            server.append(playlistId, songIds)
            if (appends == failAppend) throw IOException("response lost")
        }
    }

    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var writer: FlakyWriter
    private var invalidations = 0

    private fun repository() = PlaylistRepository(db, writerFor = { writer }, libraryChanged = { invalidations++ })

    private val songs = List(250) { "s${it % 9}" }

    private fun batches(ids: List<String> = songs) =
        flow {
            ids.chunked(100).forEach { emit(it) }
        }

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            server = FakePlaylistWriter(1)
            writer = FlakyWriter(server)
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        fail("expected a failure")
        error("unreachable")
    }

    @Test
    fun savingCreatesOnePlaylistInBatchesAndMirrorsItFromTheDraft() =
        runTest {
            val repository = repository()
            val draft = repository.stageSave(1, "  Queue  ", batches())
            val id = repository.saveDraft(draft)
            assertEquals(1, server.creates)
            assertEquals("Queue", server.playlists.getValue(id).first)
            assertEquals(songs, server.songs(id))
            assertEquals(listOf(100, 100, 50), server.appended.map { it.size })
            assertEquals(songs, db.libraryDao().playlistEntrySongIds(1, id))
            assertEquals(2, invalidations)
            assertEquals(
                listOf(id),
                db
                    .playlistDestinationDao()
                    .recent(1)
                    .first()
                    .map { it.id },
            )
            assertNull(db.playlistSaveDraftDao().draft(draft))
            assertEquals(0, db.playlistSaveDraftDao().songIds(draft, 0, 1000).size)
        }

    @Test
    fun aRetryAfterAFailedBatchSendsOnlyTheMissingSongs() =
        runTest {
            val repository = repository()
            val draft = repository.stageSave(1, "Queue", batches())
            writer.failAppend = 2
            expectFailure { repository.saveDraft(draft) }
            val id = server.playlists.keys.single()
            assertEquals(100, server.songs(id).size)
            assertEquals(id, db.playlistSaveDraftDao().draft(draft)?.playlistId)

            assertEquals(id, repository.saveDraft(draft))
            assertEquals(1, server.creates)
            assertEquals(songs, server.songs(id))
            assertEquals(songs, db.libraryDao().playlistEntrySongIds(1, id))
        }

    @Test
    fun aLostResponseIsNotSentAgainAndTheDraftSurvivesANewRepository() =
        runTest {
            val draft = repository().stageSave(1, "Queue", batches())
            writer.failAppend = 2
            writer.loseResponse = true
            expectFailure { repository().saveDraft(draft) }
            val id = server.playlists.keys.single()
            assertEquals(200, server.songs(id).size)

            assertEquals(id, repository().saveDraft(draft))
            assertEquals(listOf(100, 100, 50), server.appended.map { it.size })
            assertEquals(songs, server.songs(id))
        }

    @Test
    fun aPlaylistEditedElsewhereIsNotWrittenAndADeletedOneIsRecreated() =
        runTest {
            val repository = repository()
            val draft = repository.stageSave(1, "Queue", batches())
            writer.failAppend = 2
            expectFailure { repository.saveDraft(draft) }
            val id = server.playlists.keys.single()
            server.playlists
                .getValue(id)
                .second
                .add(0, "elsewhere")
            val appended = server.appended.size
            assertEquals(
                PlaylistEditException.Reason.Changed,
                (expectFailure { repository.saveDraft(draft) } as PlaylistEditException).reason,
            )
            assertEquals(appended, server.appended.size)

            server.playlists.remove(id)
            assertEquals(
                PlaylistEditException.Reason.NotFound,
                (expectFailure { repository.saveDraft(draft) } as PlaylistEditException).reason,
            )
            val recreated = repository.saveDraft(draft)
            assertEquals(2, server.creates)
            assertEquals(songs, server.songs(recreated))
        }

    @Test
    fun aFailedOrEmptyCaptureLeavesNoDraftAndReachesNoServer() =
        runTest {
            val repository = repository()
            val failing =
                flow {
                    emit(songs.take(100))
                    throw IllegalStateException("Queue or library changed during playlist export")
                }
            expectFailure { repository.stageSave(1, "Queue", failing) }
            expectFailure { repository.stageSave(1, "Queue", flowOf()) }
            expectFailure { repository.stageSave(1, " ", batches()) }
            val draft = repository.stageSave(1, "Queue", batches(listOf("a")))
            assertEquals(listOf("a"), db.playlistSaveDraftDao().songIds(draft, 0, 1000))
            assertEquals(draft, db.playlistSaveDraftDao().draft(draft)?.id)
            assertEquals(0, server.creates)

            val replacement = repository.stageSave(1, "Other", batches(listOf("b")))
            assertNull(db.playlistSaveDraftDao().draft(draft))
            repository.discardSave(replacement)
            assertNull(db.playlistSaveDraftDao().draft(replacement))
            assertEquals(0, server.creates)
        }
}
