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
    private lateinit var queue: QueueRepository
    private var invalidations = 0

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            db.libraryDao().upsertSongs(
                listOf("a", "b", "c", "x").map { Song(1, it, null, null, it.uppercase(), null, null, 60, null, null, null) },
            )
            server = FakePlaylistWriter(1)
            queue = QueueRepository(db)
            repository =
                PlaylistRepository(
                    db,
                    writerFor = { if (online) server else null },
                    libraryChanged = {
                        invalidations++
                        queue.invalidateLibraryCache()
                    },
                )
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
    fun queueLengthsFollowAddRemoveAndDeleteWithoutASync() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            assertEquals(2L, queue.snapshot().size)
            repository.add(PlaylistAddition(1, "p", listOf("c", "a")))
            assertEquals(4L, queue.snapshot().size)
            repository.removeEntry(1, "p", 3, "a")
            assertEquals(3L, queue.snapshot().size)
            repository.delete(1, "p")
            assertEquals(0L, queue.snapshot().size)
        }

    @Test
    fun queueLengthsFollowConflictAndNotFoundRefreshes() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            assertEquals(2L, queue.snapshot().size)
            server.put("p", "Mix", "x", "a", "b")
            expect(PlaylistEditException.Reason.Changed) { repository.removeEntry(1, "p", 0, "a") }
            assertEquals(3L, queue.snapshot().size)
            server.playlists.remove("p")
            expect(PlaylistEditException.Reason.NotFound) { repository.add(PlaylistAddition(1, "p", listOf("c"))) }
            assertEquals(0L, queue.snapshot().size)
        }

    @Test
    fun failedWritesLeaveTheQueueCacheAndMirrorAlone() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            assertEquals(2L, queue.snapshot().size)
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            assertTrue(runCatching { repository.add(PlaylistAddition(1, "p", listOf("c"))) }.isFailure)
            assertEquals(0, invalidations)
            assertEquals(2L, queue.snapshot().size)
        }

    @Test
    fun reorderAndFailedDownloadCleanupAlsoInvalidateTheQueue() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            val version = invalidations
            repository.moveEntry(1, "p", 1, "b", 0)
            assertEquals(version + 1, invalidations)
            assertEquals("b", queue.itemAt(queue.snapshot(), 0)?.song?.id)
            assertEquals(2L, queue.snapshot().size)
            assertTrue(runCatching { repository.delete(1, "p") { throw IOException("disk") } }.isFailure)
            assertEquals(0L, queue.snapshot().size)
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
    fun movingAnEntryPreservesDuplicatesAndUnresolvedSongs() =
        runTest {
            server.put("p", "Mix", "a", "gone", "b", "a")
            mirror("p", "a", "gone", "b", "a")
            repository.moveEntry(1, "p", 3, "a", 1)
            assertEquals(listOf("a", "a", "gone", "b"), server.songs("p"))
            assertEquals(listOf("a", "a", "gone", "b"), db.libraryDao().playlistEntrySongIds(1, "p"))
            repository.moveEntry(1, "p", 0, "a", 3)
            assertEquals(listOf("a", "gone", "b", "a"), db.libraryDao().playlistEntrySongIds(1, "p"))
        }

    @Test
    fun movingAStaleEntryRefreshesWithoutWriting() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            expect(PlaylistEditException.Reason.Changed) { repository.moveEntry(1, "p", 1, "c", 0) }
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
            server.put("p", "Mix", "x", "a", "b", "c")
            expect(PlaylistEditException.Reason.Changed) { repository.moveEntry(1, "p", 1, "b", 0) }
            assertEquals(listOf("x", "a", "b", "c"), mirrored("p"))
            assertTrue(server.replaced.isEmpty())
        }

    @Test
    fun aPartialMoveCanBeRetriedWithoutLosingDuplicateOccurrences() =
        runTest {
            server.put("p", "Mix", "a", "b", "a", "c")
            mirror("p", "a", "b", "a", "c")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.moveEntry(1, "p", 3, "c", 0) }.exceptionOrNull() is IOException)
            assertEquals(listOf("c"), server.songs("p"))
            assertEquals(listOf("a", "b", "a", "c"), mirrored("p"))
            repository.moveEntry(1, "p", 3, "c", 0)
            assertEquals(listOf("c", "a", "b", "a"), server.songs("p"))
            assertEquals(listOf("c", "a", "b", "a"), mirrored("p"))
        }

    @Test
    fun abandoningAPartialMoveMirrorsTheServerWithoutSendingAnotherEdit() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            assertEquals(3L, queue.snapshot().size)
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.moveEntry(1, "p", 2, "c", 0) }.isFailure)
            repository.abandonReorder(1, "p")
            assertEquals(listOf("c"), mirrored("p"))
            assertEquals(1L, queue.snapshot().size)
            assertEquals(1, server.replaced.size)
            expect(PlaylistEditException.Reason.Changed) { repository.retryReorder(1, "p") }
        }

    @Test
    fun abandoningOfflineForgetsTheIntentEvenWhenReadbackCannotRun() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.moveEntry(1, "p", 2, "c", 0) }.isFailure)
            online = false
            expect(PlaylistEditException.Reason.Offline) { repository.abandonReorder(1, "p") }
            online = true
            expect(PlaylistEditException.Reason.Changed) { repository.retryReorder(1, "p") }
            assertEquals(listOf("a", "b", "c"), mirrored("p"))
            assertEquals(1, server.replaced.size)
        }

    @Test
    fun abandoningAPlaylistDeletedElsewhereDropsTheMirror() =
        runTest {
            server.put("p", "Mix", "a")
            mirror("p", "a")
            queue.replace(listOf(queue.playlistEntry(1, "p")))
            assertEquals(1L, queue.snapshot().size)
            server.playlists.remove("p")
            repository.abandonReorder(1, "p")
            assertNull(db.libraryDao().playlist(1, "p").first())
            assertEquals(0L, queue.snapshot().size)
            assertTrue(server.replaced.isEmpty())
        }

    @Test
    fun preflightFailureRetainsTheMoveButDoesNotAuthorizePartialRecovery() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            online = false
            expect(PlaylistEditException.Reason.Offline) { repository.moveEntry(1, "p", 2, "c", 0) }
            online = true
            repository.retryReorder(1, "p")
            assertEquals(listOf("c", "a", "b"), mirrored("p"))
            online = false
            expect(PlaylistEditException.Reason.Offline) { repository.moveEntry(1, "p", 0, "c", 2) }
            online = true
            server.put("p", "Mix", "a")
            expect(PlaylistEditException.Reason.Changed) { repository.retryReorder(1, "p") }
            assertEquals(1, server.replaced.size)
        }

    @Test
    fun retainedRetryUsesTheOriginalOrderAfterTheMirrorWasRefreshed() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.moveEntry(1, "p", 2, "c", 0) }.isFailure)
            mirror("p", "c")
            repository.retryReorder(1, "p")
            assertEquals(listOf("c", "a", "b"), mirrored("p"))
            expect(PlaylistEditException.Reason.Changed) { repository.retryReorder(1, "p") }
        }

    @Test
    fun aLostMoveResponseIsReadBackWithoutAnotherReplacement() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            server.failNext = FakePlaylistWriter.Failure.AfterWrite
            assertTrue(runCatching { repository.moveEntry(1, "p", 2, "c", 0) }.isFailure)
            repository.moveEntry(1, "p", 2, "c", 0)
            assertEquals(1, server.replaced.size)
            assertEquals(listOf("c", "a", "b"), mirrored("p"))
            expect(PlaylistEditException.Reason.Changed) {
                repository.reorder(1, "p", listOf("c", "a", "b"), listOf("a", "b", "c"))
            }
        }

    @Test
    fun aConflictingEditAfterAPartialMoveIsNeverOverwritten() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            mirror("p", "a", "b", "c")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            assertTrue(runCatching { repository.moveEntry(1, "p", 2, "c", 0) }.isFailure)
            server.put("p", "Mix", "c", "x")
            expect(PlaylistEditException.Reason.Changed) { repository.moveEntry(1, "p", 2, "c", 0) }
            assertEquals(1, server.replaced.size)
            assertEquals(listOf("c", "x"), mirrored("p"))
        }

    @Test
    fun aPrefixWithoutAFailedIntentIsAConflict() =
        runTest {
            server.put("p", "Mix", "c")
            mirror("p", "a", "b", "c")
            expect(PlaylistEditException.Reason.Changed) { repository.moveEntry(1, "p", 2, "c", 0) }
            assertTrue(server.replaced.isEmpty())
        }

    @Test
    fun movingOutsideThePlaylistIsRejected() =
        runTest {
            server.put("p", "Mix", "a", "b")
            mirror("p", "a", "b")
            assertTrue(runCatching { repository.moveEntry(1, "p", 0, "a", 2) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(listOf("a", "b"), server.songs("p"))
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
