package com.subtracks.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var repository: PlaylistRepository
    private var online = true

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            db.libraryDao().upsertSongs(
                listOf("a", "b", "c", "x").map { Song(1, it, null, null, it.uppercase(), null, null, 60, null, null, null) },
            )
            server = FakePlaylistWriter(1)
            repository = PlaylistRepository(db, writerFor = { if (online) server else null })
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun mirrored(id: String) = db.queueDao().playlistSongIds(1, id)

    private suspend fun mirror(
        id: String,
        vararg songIds: String,
    ) {
        db.libraryDao().replacePlaylist(
            Playlist(1, id, "Shown", null, null, songIds.size.toLong(), 1),
            songIds.mapIndexed { position, songId -> PlaylistSong(1, id, songId, position.toLong()) },
        )
    }

    private inline fun expect(
        reason: PlaylistEditException.Reason,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("expected $reason")
        } catch (failure: PlaylistEditException) {
            assertEquals(reason, failure.reason)
        }
    }

    @Test
    fun addingMirrorsTheServerResult() =
        runTest {
            server.put("p", "Mix", "x")
            repository.add(PlaylistAddition(1, "p", listOf("a", "b")))
            assertEquals(listOf("x", "a", "b"), server.songs("p"))
            assertEquals(listOf("x", "a", "b"), mirrored("p"))
            assertEquals(
                3L,
                db
                    .libraryDao()
                    .playlist(1, "p")
                    .first()!!
                    .songCount,
            )
        }

    @Test
    fun onlySuccessfulAdditionsBecomeRecentDestinations() =
        runTest {
            server.put("p", "Mix")
            server.put("q", "Other")
            mirror("q")
            repository.add(PlaylistAddition(1, "p", listOf("a")))
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            assertTrue(runCatching { repository.add(PlaylistAddition(1, "q", listOf("a"))) }.isFailure)
            val created = repository.create(PlaylistCreation(1, "Empty", emptyList()))
            assertEquals(listOf(created, "p"), repository.recentDestinations(1).first().map { it.id })
        }

    @Test
    fun aRetryAfterALostResponseDoesNotAddTheSongsAgain() =
        runTest {
            server.put("p", "Mix", "a")
            val addition = PlaylistAddition(1, "p", listOf("a", "b"))
            server.failNext = FakePlaylistWriter.Failure.AfterWrite
            assertTrue(runCatching { repository.add(addition) }.exceptionOrNull() is IOException)
            repository.add(addition)
            assertEquals(listOf("a", "a", "b"), server.songs("p"))
            assertEquals(listOf(listOf("a", "b")), server.appended)
            assertEquals(listOf("a", "a", "b"), mirrored("p"))
        }

    @Test
    fun aRetryAfterAPartialAddSendsOnlyWhatIsMissing() =
        runTest {
            server.put("p", "Mix")
            val addition = PlaylistAddition(1, "p", listOf("a", "b", "c"))
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.add(addition) }.exceptionOrNull() is IOException)
            repository.add(addition)
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
            assertEquals(listOf(listOf("a", "b", "c"), listOf("b", "c")), server.appended)
        }

    @Test
    fun offlineEditsReachNoServer() =
        runTest {
            online = false
            expect(PlaylistEditException.Reason.Offline) { repository.add(PlaylistAddition(1, "p", listOf("a"))) }
            expect(PlaylistEditException.Reason.Offline) { repository.delete(1, "p") }
            assertEquals(emptyList<List<String>>(), server.appended)
        }

    @Test
    fun aPlaylistGoneFromTheServerLeavesTheMirror() =
        runTest {
            mirror("gone", "a")
            expect(PlaylistEditException.Reason.NotFound) { repository.add(PlaylistAddition(1, "gone", listOf("b"))) }
            assertNull(db.libraryDao().playlist(1, "gone").first())
            assertEquals(emptyList<String>(), mirrored("gone"))
        }

    @Test
    fun removingDropsThePositionsShown() =
        runTest {
            server.put("p", "Mix", "a", "b", "a", "c")
            mirror("p", "a", "b", "a", "c")
            repository.remove(1, "p", listOf(2, 0), shownSongIds = listOf("a", "b", "a", "c"))
            assertEquals(listOf("b", "c"), server.songs("p"))
            assertEquals(listOf("b", "c"), mirrored("p"))
        }

    @Test
    fun deletingRunsTheCleanupWhileTheMirrorStillListsTheSongs() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            var seenDuringCleanup: List<String>? = null
            repository.delete(1, "p") {
                assertNull(server.playlists["p"])
                seenDuringCleanup = mirrored("p")
            }
            assertEquals(listOf("a", "b"), seenDuringCleanup)
            assertEquals(emptyList<String>(), mirrored("p"))
            assertNull(db.libraryDao().playlist(1, "p").first())
        }

    @Test
    fun aFailedDeleteRunsNoCleanupAndKeepsTheMirror() =
        runTest {
            server.put("p", "Mix", "a")
            mirror("p", "a")
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            var cleaned = false
            assertTrue(runCatching { repository.delete(1, "p") { cleaned = true } }.exceptionOrNull() is IOException)
            assertFalse(cleaned)
            assertEquals(listOf("a"), mirrored("p"))
        }

    @Test
    fun aFailingCleanupStillDropsTheMirror() =
        runTest {
            server.put("p", "Mix", "a")
            mirror("p", "a")
            assertTrue(runCatching { repository.delete(1, "p") { throw IllegalStateException("disk") } }.isFailure)
            assertEquals(emptyList<String>(), mirrored("p"))
        }

    @Test
    fun removingAnEntryDropsOnlyThatCopyOfTheSong() =
        runTest {
            server.put("p", "Mix", "a", "b", "a")
            mirror("p", "a", "b", "a")
            repository.removeEntry(1, "p", position = 2, songId = "a")
            assertEquals(listOf(listOf(2)), server.removed)
            assertEquals(listOf("a", "b"), server.songs("p"))
            assertEquals(listOf("a", "b"), mirrored("p"))
        }

    @Test
    fun removingAnEntryCountsSongsTheMirrorHasNoRowFor() =
        runTest {
            server.put("p", "Mix", "gone", "a", "b")
            mirror("p", "gone", "a", "b")
            repository.removeEntry(1, "p", position = 2, songId = "b")
            assertEquals(listOf("gone", "a"), server.songs("p"))
        }

    @Test
    fun aStaleMenuRemovesNothing() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            expect(PlaylistEditException.Reason.Changed) {
                repository.removeEntry(1, "p", position = 1, songId = "c")
            }
            assertEquals(emptyList<List<Int>>(), server.removed)
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
        }

    @Test
    fun aPlaylistChangedElsewhereIsRefreshedInsteadOfLosingTheWrongSong() =
        runTest {
            server.put("p", "Mix", "x", "a", "b")
            mirror("p", "a", "b")
            expect(PlaylistEditException.Reason.Changed) {
                repository.remove(1, "p", listOf(0), shownSongIds = listOf("a", "b"))
            }
            assertEquals(emptyList<List<Int>>(), server.removed)
            assertEquals(listOf("x", "a", "b"), mirrored("p"))
        }

    @Test
    fun reorderingReplacesTheOrderWhenNothingChanged() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            repository.reorder(1, "p", listOf("c", "a", "b"), shownSongIds = listOf("a", "b", "c"))
            assertEquals(listOf("c", "a", "b"), server.songs("p"))
            assertEquals(listOf("c", "a", "b"), mirrored("p"))
            expect(PlaylistEditException.Reason.Changed) {
                repository.reorder(1, "p", listOf("a", "b", "c"), shownSongIds = listOf("a", "b", "c"))
            }
        }

    @Test
    fun aRetriedCreationFinishesThePlaylistAlreadyMade() =
        runTest {
            val creation = PlaylistCreation(1, "Saved queue", listOf("a", "b"))
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            assertTrue(runCatching { repository.create(creation) }.exceptionOrNull() is IOException)
            assertEquals(0, server.creates)
            val id = repository.create(creation).also { assertEquals(it, creation.playlistId) }
            assertEquals(listOf("a", "b"), server.songs(id))
            assertEquals(1, server.creates)
            assertEquals(listOf("a", "b"), mirrored(id))
        }

    @Test
    fun aCreationInterruptedBeforeItsSongsIsNotDuplicated() =
        runTest {
            val creation = PlaylistCreation(1, "Saved queue", listOf("a", "b"))
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.create(creation) }.exceptionOrNull() is IOException)
            assertEquals(1, server.creates)
            repository.create(creation)
            assertEquals(1, server.creates)
            assertEquals(listOf("a", "b"), server.songs(creation.playlistId!!))
            assertEquals(listOf(listOf("a", "b"), listOf("b")), server.appended)
        }

    @Test
    fun aServerThatDoesNotReportTheNewPlaylistSaysSo() =
        runTest {
            server.reportsCreated = false
            expect(PlaylistEditException.Reason.NotReported) { repository.create(PlaylistCreation(1, "New", listOf("a"))) }
        }

    @Test
    fun renamingAndDeletingAreMirrored() =
        runTest {
            server.put("p", "Mix", "a")
            mirror("p", "a")
            repository.rename(1, "p", "Night drive")
            assertEquals(
                "Night drive",
                db
                    .libraryDao()
                    .playlist(1, "p")
                    .first()!!
                    .name,
            )
            repository.delete(1, "p")
            assertNull(db.libraryDao().playlist(1, "p").first())
            assertEquals(emptyList<String>(), mirrored("p"))
        }
}
