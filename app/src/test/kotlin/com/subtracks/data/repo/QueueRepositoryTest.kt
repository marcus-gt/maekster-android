package com.subtracks.data.repo

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SongListSpec
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.db.songsPageQuery
import com.subtracks.data.model.Album
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.QueueEntry
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.model.StoredQueueOrder
import com.subtracks.data.model.StoredQueueOrderEntry
import com.subtracks.data.prefs.SongSort
import com.subtracks.data.prefs.StarredFilter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        db = createTestDatabase()
        repository = QueueRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun extensionAppendAndTruncateKeepCurrentHistoryAndManualEntries() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.songEntry(1, "s1")))
            val original = repository.snapshot()
            assertTrue(repository.appendExtension(original, 1, listOf("s2", "s3")))
            assertFalse(repository.appendExtension(original, 1, listOf("s4")))
            assertEquals(1L, repository.extensionStart())
            assertTrue(repository.appendExtension(repository.snapshot(), 1, listOf("s4")))
            assertEquals(1L, repository.extensionStart())
            repository.setCursor(1)
            repository.setPosition(1234, "s2")
            repository.addUpNext(repository.snapshot(), 1, repository.songEntry(1, "s1"), false)
            assertTrue(repository.truncateExtension(repository.snapshot()))
            val kept = repository.snapshot()
            assertEquals(listOf("s1", "s2"), kept.entries.map { it.entry.refId })
            assertEquals(listOf("s1"), kept.upNext.map { it.entry.refId })
            assertEquals(1L, repository.cursor())
            assertEquals(1234L, repository.cursorPositionMs())
            assertEquals(null, repository.extensionStart())
        }

    @Test
    fun extensionRejectsModesWrongSourcesAndUnknownSongsWithoutPartialWrites() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.songEntry(1, "s1")))
            assertFalse(repository.appendExtension(repository.snapshot(), 2, listOf("s2")))
            assertFalse(repository.appendExtension(repository.snapshot(), 1, listOf("s2", "missing")))
            repository.setRepeat(1)
            assertFalse(repository.appendExtension(repository.snapshot(), 1, listOf("s2")))
            repository.setRepeat(0)
            repository.setShuffle(42)
            assertFalse(repository.appendExtension(repository.snapshot(), 1, listOf("s2")))
            repository.setShuffle(null)
            assertTrue(runCatching { repository.appendExtension(repository.snapshot(), 1, List(51) { "s2" }) }.isFailure)
            assertEquals(listOf("s1"), repository.snapshot().entries.map { it.entry.refId })
            assertEquals(null, repository.extensionStart())
        }

    @Test
    fun manualContextEditsPromoteTheTailSoItCannotBeDeletedLater() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.songEntry(1, "s1")))
            assertTrue(repository.appendExtension(repository.snapshot(), 1, listOf("s2", "s3", "s4")))
            assertTrue(repository.move(repository.snapshot(), 1, 0))
            assertEquals(null, repository.extensionStart())
            assertFalse(repository.truncateExtension(repository.snapshot()))
            assertEquals(4L, repository.snapshot().size)
            for (clear in listOf(false, true)) {
                repository.replace(listOf(repository.songEntry(1, "s1")))
                assertTrue(repository.appendExtension(repository.snapshot(), 1, listOf("s2", "s3")))
                if (clear) {
                    repository.clearUpcoming(repository.snapshot(), 1)
                } else {
                    repository.removeAt(repository.snapshot(), 0)
                }
                assertEquals(null, repository.extensionStart())
                assertFalse(repository.truncateExtension(repository.snapshot()))
                assertEquals(2L, repository.snapshot().size)
            }
        }

    @Test
    fun extensionTruncationKeepsTheManualCurrentSongAndDoesNotResolveTheContext() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(repository.albumEntry(1, "al1")),
                upNext = listOf(repository.songEntry(1, "s4")),
                upNextAnchor = 0,
            )
            assertTrue(repository.appendExtension(repository.snapshot(), 1, listOf("s2", "s3")))
            val snapshot = repository.snapshot()
            repository.setCursor(snapshot.anchorPlay + 1)
            repository.setPosition(456, "s4")
            val before = repository.resolvedRowCount
            assertTrue(repository.truncateExtension(snapshot))
            assertEquals(before, repository.resolvedRowCount)
            val kept = repository.snapshot()
            assertEquals(1, kept.entries.size)
            assertEquals(
                QueueKind.Album,
                kept.entries
                    .single()
                    .entry.kind,
            )
            assertEquals(listOf("s4"), kept.upNext.map { it.entry.refId })
            assertEquals(snapshot.anchorPlay + 1, repository.cursor())
            assertEquals(456L, repository.cursorPositionMs())
        }

    @Test
    fun extensionBoundarySurvivesCursorWritesAndRecreationButNotQueueReplacement() =
        runTest {
            seedLibrary()
            val entries = listOf(repository.songEntry(1, "s1"), repository.songEntry(1, "s4"))
            repository.replace(entries)
            val dao = db.queueDao()
            dao.setCursor(dao.cursor()!!.copy(extensionStart = 1))
            repository.setCursor(1)
            repository.setPosition(1234, "s4")
            repository.setRepeat(2)
            repository.setShuffle(42)
            assertEquals(1L, db.queueDao().cursor()!!.extensionStart)
            assertEquals(1L, QueueRepository(db).extensionStart())
            repository.replace(entries)
            assertEquals(null, repository.extensionStart())
        }

    @Test
    fun playlistExportPreservesCombinedShuffleOrderAndDuplicates() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s1")),
                upNext = listOf(repository.songEntry(1, "s4"), repository.songEntry(1, "s4")),
                upNextAnchor = 1,
            )
            repository.setShuffle(42)
            val snapshot = repository.snapshot()
            val expected = resolveAll(snapshot)
            val batches = repository.playlistBatches(snapshot, 1, batchSize = 2).toList()
            assertEquals(expected, batches.flatten())
            assertEquals(listOf(2, 2, 2), batches.map { it.size })
            assertEquals(2, batches.flatten().count { it == "s4" })
            repository.setCursor(3)
            assertEquals(expected, repository.playlistBatches(snapshot, 1, batchSize = 3).toList().flatten())
        }

    @Test
    fun playlistExportDoesNotResolvePastTheBatchTheCollectorRequests() =
        runTest {
            seedLibrary()
            val entry = repository.songEntry(1, "s1")
            val snapshot = QueueSnapshot(listOf(ResolvedQueueEntry(entry, 1_000_000_000)))
            val before = repository.resolvedRowCount
            // A synthetic huge snapshot whose first row exists: taking one single-row batch must not visit its tail.
            assertEquals(listOf(listOf("s1")), repository.playlistBatches(snapshot, 1, batchSize = 1).take(1).toList())
            assertEquals(1L, repository.resolvedRowCount - before)
        }

    @Test
    fun playlistExportRejectsChangesBeforeOrBetweenBatches() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            val snapshot = repository.snapshot()
            var emitted = 0
            val failure =
                runCatching {
                    repository.playlistBatches(snapshot, 1, batchSize = 1).collect {
                        emitted++
                        repository.invalidateLibraryCache()
                    }
                }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(1, emitted)
            emitted = 0
            assertTrue(
                runCatching {
                    repository.playlistBatches(snapshot, 1).collect { emitted++ }
                }.exceptionOrNull() is IllegalStateException,
            )
            assertEquals(0, emitted)
        }

    @Test
    fun playlistExportPreservesRetainedShufflePrefixes() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")))
            repository.setShuffle(42)
            val before = repository.snapshot()
            val expected = resolveAll(before).take(3)
            assertTrue(repository.clearUpcoming(before, 2))
            val retained = repository.snapshot()
            assertTrue(retained.orders.isNotEmpty())
            assertEquals(expected, repository.playlistBatches(retained, 1, 2).toList().flatten())
        }

    @Test
    fun playlistExportNeverEmitsAnIncompleteBatchOrClaimsCompletionAfterAnEdit() =
        runTest {
            seedLibrary()
            val invalid = QueueSnapshot(listOf(ResolvedQueueEntry(repository.songEntry(1, "s1"), 2)))
            var emitted = 0
            assertTrue(
                runCatching {
                    repository.playlistBatches(invalid, 1, 2).collect { emitted++ }
                }.exceptionOrNull() is IllegalStateException,
            )
            assertEquals(0, emitted)
            val valid = QueueSnapshot(listOf(ResolvedQueueEntry(repository.songEntry(1, "s1"), 1)))
            assertTrue(
                runCatching {
                    repository.playlistBatches(valid, 1).collect {
                        emitted++
                        repository.invalidateLibraryCache()
                    }
                }.exceptionOrNull() is IllegalStateException,
            )
            assertEquals(1, emitted)
        }

    @Test
    fun playlistExportRejectsMixedSourcesAndInvalidBatchSizesBeforeReading() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.songEntry(1, "s1")))
            val captured = repository.snapshot()
            val mixed = captured.copy(upNext = listOf(ResolvedQueueEntry(repository.songEntry(2, "foreign"), 1)))
            val before = repository.resolvedRowCount
            assertTrue(runCatching { repository.playlistBatches(mixed, 1).toList() }.exceptionOrNull() is IllegalArgumentException)
            for (size in listOf(0, -1, 101, Int.MAX_VALUE)) {
                assertTrue(
                    runCatching { repository.playlistBatches(captured, 1, size).toList() }.exceptionOrNull() is IllegalArgumentException,
                )
            }
            assertEquals(before, repository.resolvedRowCount)
            assertTrue(repository.playlistBatches(QueueSnapshot(emptyList(), version = captured.version), 1).toList().isEmpty())
        }

    @Test
    fun shuffledClearUpcomingKeepsPlayedOrderAfterRestartAndUnshuffle() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")), sourceLabel = "Artist")
            repository.setShuffle(9)
            val before = repository.snapshot()
            val expected = resolveAll(before).take(3)
            repository.setCursor(2)
            repository.setPosition(12345)
            assertTrue(repository.clearUpcoming(before, 2))
            repository = QueueRepository(db)
            val after = repository.snapshot()
            assertTrue(after.shuffled)
            assertEquals(expected, resolveAll(after))
            assertEquals(2L, repository.cursor())
            assertEquals(12345L, repository.cursorPositionMs())
            assertEquals("Artist", repository.sourceLabel())
            assertEquals(1, after.entries.size)
            repository.setShuffle(null)
            assertEquals(expected, resolveAll(repository.snapshot()))
            repository.setShuffle(42)
            assertEquals(expected.toSet(), resolveAll(repository.snapshot()).toSet())
        }

    @Test
    fun shuffledClearHandlesEveryManualBlockBoundaryAndDuplicates() =
        runTest {
            seedLibrary()
            for (anchor in listOf(-1L, 1L)) {
                for (cut in 0L..6L) {
                    repository.replace(
                        listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s1")),
                        upNext = listOf(repository.songEntry(1, "s1"), repository.albumEntry(1, "al2")),
                        upNextAnchor = anchor,
                    )
                    repository.setShuffle(42)
                    val before = repository.snapshot()
                    if (cut >= before.size - 1) continue
                    val expected = resolveAll(before).take((cut + 1).toInt())
                    repository.setCursor(cut)
                    assertTrue(repository.clearUpcoming(before, cut))
                    repository = QueueRepository(db)
                    assertEquals("anchor=$anchor cut=$cut", expected, resolveAll(repository.snapshot()))
                    repository.setShuffle(null)
                    assertEquals(expected, resolveAll(repository.snapshot()))
                }
            }
        }

    @Test
    fun repeatedShuffledClearKeepsAnIdentityPrefixAndDoesNotResolveHugeQueues() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")))
            repository.setShuffle(9)
            val before = repository.snapshot()
            val expected = resolveAll(before).take(2)
            assertTrue(repository.clearUpcoming(before, 3))
            assertTrue(repository.clearUpcoming(repository.snapshot(), 1))
            repository = QueueRepository(db)
            assertEquals(expected, resolveAll(repository.snapshot()))
            repository.setShuffle(null)
            assertEquals(expected, resolveAll(repository.snapshot()))
            val entry = repository.albumEntry(1, "al1")
            repository.replace(listOf(entry))
            val synthetic =
                QueueSnapshot(
                    listOf(ResolvedQueueEntry(entry, 1_000_000_000)),
                    shuffle = ShuffleState(42, 1_000_000_000),
                )
            val resolvedBefore = repository.resolvedRowCount
            assertTrue(repository.clearUpcoming(synthetic, 500_000_000))
            assertEquals(resolvedBefore, repository.resolvedRowCount)
            val root = db.queueDao().entries().single()
            assertEquals(QueueKind.Order, root.kind)
            val order = db.queueOrderDao().order(1, root.refId.toLong())!!
            assertEquals(500_000_001L, ShufflePrefix.decode(order.prefix)!!.size)
            assertEquals(1, db.queueOrderDao().entries(order.id).size)
        }

    private suspend fun storedOrder(
        snapshot: QueueSnapshot,
        count: Long,
    ): QueueEntry {
        val dao = db.queueOrderDao()
        val id =
            dao.insertOrder(
                StoredQueueOrder(sourceId = 1, prefix = ShufflePrefix(snapshot.shuffle!!, count).encode()),
            )
        dao.insertEntries(
            snapshot.entries.mapIndexed { index, resolved ->
                val entry = resolved.entry
                StoredQueueOrderEntry(
                    id,
                    index.toLong(),
                    entry.sourceId,
                    entry.kind,
                    entry.refId,
                    entry.rangeStart,
                    entry.rangeEnd,
                    resolved.length,
                )
            },
        )
        return QueueEntry(position = 0, sourceId = 1, kind = QueueKind.Order, refId = id.toString())
    }

    @Test
    fun retainedOrderResolvesOriginalReferencesAndSurvivesEditsAndRestart() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")))
            repository.setShuffle(9)
            val original = repository.snapshot()
            val expected = resolveAll(original).take(4)
            val reference = storedOrder(original, 4)
            repository.replace(listOf(reference), sourceLabel = "Artist")
            repository = QueueRepository(db)
            val restored = repository.snapshot()
            assertEquals(expected, resolveAll(restored))
            assertTrue((0L until restored.size).all { restored.isAlbumContext(it) })
            assertEquals(expected, repository.range(restored, 0, 3).map { it.item.song.id })
            repository.setShuffle(42)
            assertEquals(expected.toSet(), resolveAll(repository.snapshot()).toSet())
            repository.setShuffle(null)
            assertEquals(expected, resolveAll(repository.snapshot()))
            repository.removeAt(repository.snapshot(), 1)
            assertEquals(expected.filterIndexed { index, _ -> index != 1 }, resolveAll(repository.snapshot()))
            assertEquals("Artist", repository.sourceLabel())
        }

    @Test
    fun nestedRetainedOrdersKeepTheirDependenciesUntilQueueReplacement() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")))
            repository.setShuffle(9)
            val child = storedOrder(repository.snapshot(), 4)
            repository.replace(listOf(child))
            repository.setShuffle(42)
            val before = repository.snapshot()
            val expected = resolveAll(before).take(2)
            val parent = storedOrder(before, 2)
            repository.replace(listOf(parent))
            repository = QueueRepository(db)
            assertEquals(expected, resolveAll(repository.snapshot()))
            assertTrue(db.queueOrderDao().order(1, child.refId.toLong()) != null)
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            assertEquals(null, db.queueOrderDao().order(1, parent.refId.toLong()))
            assertEquals(null, db.queueOrderDao().order(1, child.refId.toLong()))
        }

    @Test
    fun invalidStoredOrderReferencesPlayNothing() =
        runTest {
            seedLibrary()
            db.sourcesDao().upsertSource(Source(2, "other", "https://other", false, 2))
            val dao = db.queueOrderDao()
            for (mode in listOf("cycle", "cross-source", "changed-length")) {
                val domain = if (mode == "changed-length") 4L else 3L
                val id = dao.insertOrder(StoredQueueOrder(sourceId = 1, prefix = ShufflePrefix(ShuffleState(42, domain), 2).encode()))
                dao.insertEntries(
                    listOf(
                        StoredQueueOrderEntry(
                            id,
                            0,
                            if (mode == "cross-source") 2 else 1,
                            if (mode == "cycle") QueueKind.Order else QueueKind.Album,
                            if (mode == "cycle") id.toString() else "al1",
                            null,
                            null,
                            domain,
                        ),
                    ),
                )
                repository.replace(listOf(QueueEntry(position = 0, sourceId = 1, kind = QueueKind.Order, refId = id.toString())))
                repository = QueueRepository(db)
                assertEquals(mode, 0L, repository.snapshot().size)
            }
        }

    @Test
    fun sourceLabelSurvivesRepositoryRecreationAndEditsAndClearsForANewQueue() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")), sourceLabel = "Album · Kid A")
            repository = QueueRepository(db)
            assertEquals("Album · Kid A", repository.sourceLabel())
            repository.setCursor(1)
            repository.setPosition(1234)
            repository.setRepeat(2)
            repository.setShuffle(42)
            assertEquals("Album · Kid A", repository.sourceLabel())
            repository.setShuffle(null)
            assertTrue(repository.clearUpcoming(repository.snapshot(), 1))
            assertEquals("Album · Kid A", repository.sourceLabel())
            repository.replace(listOf(repository.albumEntry(1, "al2")))
            assertEquals(null, repository.sourceLabel())
        }

    @Test
    fun theSavedPositionKnowsItsSongUntilTheCursorMoves() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            repository.setCursor(1)
            repository.setPosition(1234, "s2")
            repository.setCursor(1)
            assertEquals("s2", repository.positionSongId())
            assertEquals(1234L, repository.cursorPositionMs())

            repository.setCursor(0)

            assertEquals(null, repository.positionSongId())
            assertEquals(0L, repository.cursorPositionMs())
        }

    @Test
    fun clearUpcomingKeepsTheCurrentContextTrackAndItsPosition() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(repository.albumEntry(1, "al1"), repository.albumEntry(1, "al2")),
                upNext = listOf(repository.albumEntry(1, "al2")),
                upNextAnchor = 1,
            )
            repository.setCursor(1)
            repository.setPosition(12345)
            repository.setRepeat(2)
            assertTrue(repository.clearUpcoming(repository.snapshot(), 1))

            val after = repository.snapshot()
            assertEquals(listOf("s1", "s2"), resolveAll(after))
            assertTrue(after.upNext.isEmpty())
            assertEquals(1L, repository.cursor())
            assertEquals(12345L, repository.cursorPositionMs())
            assertEquals(2, repository.modes().repeat)
            assertEquals(1, after.entries.size)
        }

    @Test
    fun clearUpcomingInsideTheManualBlockKeepsItsPrefixAndContextHistory() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(repository.albumEntry(1, "al1")),
                upNext = listOf(repository.albumEntry(1, "al2")),
                upNextAnchor = 0,
            )
            repository.setCursor(1)
            assertTrue(repository.clearUpcoming(repository.snapshot(), 1))
            val after = repository.snapshot()
            assertEquals(listOf("s1", "s4"), resolveAll(after))
            assertEquals(1L, after.contextSize)
            assertEquals(1L, after.upNextSize)
            assertEquals(0L, after.upNextAnchor)
        }

    @Test
    fun clearUpcomingAfterTheManualBlockKeepsDuplicatesAndExistingRangeOffsets() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(
                    repository.albumEntry(1, "al1").copy(rangeStart = 1, rangeEnd = 2),
                    repository.albumEntry(1, "al2"),
                ),
                upNext = listOf(repository.albumEntry(1, "al1").copy(rangeStart = 1, rangeEnd = 1)),
                upNextAnchor = 0,
            )
            assertTrue(repository.clearUpcoming(repository.snapshot(), 2))
            val after = repository.snapshot()
            assertEquals(listOf("s2", "s2", "s3"), resolveAll(after))
            assertEquals(
                1L,
                after.entries
                    .single()
                    .entry.rangeStart,
            )
            assertEquals(
                2L,
                after.entries
                    .single()
                    .entry.rangeEnd,
            )
        }

    @Test
    fun clearUpcomingNoOpsAtTheEndWithoutWriting() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            val before = repository.snapshot()
            assertFalse(repository.clearUpcoming(before, -1))
            assertFalse(repository.clearUpcoming(before, before.size))
            assertFalse(repository.clearUpcoming(before, before.size - 1))
            repository.setShuffle(42)
            val shuffled = repository.snapshot()
            assertFalse(repository.clearUpcoming(shuffled, shuffled.size - 1))
            assertEquals(listOf("s1", "s2", "s3").sorted(), resolveAll(repository.snapshot()).sorted())
            assertEquals(42L, repository.snapshot().shuffleSeed)
        }

    @Test
    fun clearUpcomingRepresentsAHugePrefixAsOneRange() =
        runTest {
            seedLibrary()
            val entry = repository.albumEntry(1, "al1")
            repository.replace(listOf(entry))
            val synthetic = QueueSnapshot(listOf(ResolvedQueueEntry(entry, 1_000_000_000)))
            assertTrue(repository.clearUpcoming(synthetic, 500_000_000))
            val entries = db.queueDao().entries()
            assertEquals(1, entries.size)
            assertEquals(0L, entries.single().rangeStart)
            assertEquals(500_000_000L, entries.single().rangeEnd)
        }

    @Test
    fun wholeAlbumEntryResolvesEveryTrackInOrder() =
        runTest {
            seedLibrary()

            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            assertEquals(3, snapshot.size)
            assertEquals(listOf("s1", "s2", "s3"), resolveAll(snapshot))
        }

    @Test
    fun wholeGenreEntryResolvesEveryTrackInTitleOrder() =
        runTest {
            seedLibrary()
            insertGenre("s4", "Rock")
            insertGenre("s1", "Rock")
            insertGenre("s3", "Rock")
            insertGenre("s2", "Jazz")

            val snapshot = repository.snapshotAfter(listOf(repository.genreEntry(1, "Rock")))

            assertEquals(3, snapshot.size)
            assertEquals(listOf("s1", "s3", "s4"), resolveAll(snapshot))
        }

    @Test
    fun downloadedEntryResolvesOnlyCompletedDownloads() =
        runTest {
            seedLibrary()
            db.downloadDao().upsert(SongDownload(1, "s1", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "s3", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "s2", DownloadStatus.Running))

            val snapshot = repository.snapshotAfter(listOf(repository.downloadedEntry(1)))

            assertEquals(2, snapshot.size)
            assertEquals(listOf("s1", "s3"), resolveAll(snapshot))
        }

    @Test
    fun starredEntryResolvesStarredTracksNewestFirst() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertSongs(
                listOf(
                    song("s1", "al1", track = 1, starred = 100),
                    song("s2", "al1", track = 2, starred = 300),
                    song("s3", "al1", track = 3, starred = 200),
                ),
            )

            val snapshot = repository.snapshotAfter(listOf(repository.starredEntry(1)))

            assertEquals(3, snapshot.size)
            assertEquals(listOf("s2", "s3", "s1"), resolveAll(snapshot))
        }

    @Test
    fun librarySongsEntryFollowsTheSongsListSortFiltersAndSearch() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertSongs(
                listOf(
                    song("s1", "al1", track = 1, album = "First Album", title = "Delta", starred = 1, created = 10),
                    song("s2", "al1", track = 2, album = "First Album", title = "alpha", created = 30),
                    song("s3", "al1", track = 3, album = "First Album", title = "Charlie", starred = 2, created = 20),
                    song("s4", "al2", track = 1, album = "Second Album", title = "bravo", created = 40),
                    song("s5", "al2", track = 2, album = "Second Album", title = "Echo", created = 50),
                ),
            )
            db.downloadDao().upsert(SongDownload(1, "s4", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "s5", DownloadStatus.Running))

            val cases =
                mapOf(
                    SongListSpec() to listOf("s2", "s4", "s3", "s1", "s5"),
                    SongListSpec(descending = true) to listOf("s5", "s1", "s3", "s4", "s2"),
                    SongListSpec(sort = SongSort.Added) to listOf("s5", "s4", "s2", "s3", "s1"),
                    SongListSpec(sort = SongSort.Added, descending = true) to listOf("s1", "s3", "s2", "s4", "s5"),
                    SongListSpec(sort = SongSort.Album) to listOf("s1", "s2", "s3", "s4", "s5"),
                    SongListSpec(starred = StarredFilter.Starred) to listOf("s3", "s1"),
                    SongListSpec(starred = StarredFilter.NotStarred) to listOf("s2", "s4", "s5"),
                    SongListSpec(downloaded = true) to listOf("s4"),
                    SongListSpec(search = "ha") to listOf("s2", "s3"),
                )
            cases.forEach { (spec, expected) ->
                val snapshot = repository.snapshotAfter(listOf(repository.libraryEntry(1, spec)))
                assertEquals(spec.toString(), expected, resolveAll(snapshot))
                // The list reads the same rows in the same order, so a tapped row's index is where the queue starts.
                val listed = db.queueDao().songItems(songsPageQuery(1, spec)).map { it.song.id }
                assertEquals(spec.toString(), expected, listed)
            }
        }

    @Test
    fun aLibrarySongsEntryThatDoesNotDecodePlaysNothing() =
        runTest {
            seedLibrary()
            val entry = QueueEntry(position = 0, sourceId = 1, kind = QueueKind.Library, refId = "not a spec")

            assertEquals(0, repository.snapshotAfter(listOf(entry)).size)
        }

    @Test
    fun rangeLimitsAnEntryToASlice() =
        runTest {
            seedLibrary()

            val entry =
                QueueEntry(
                    position = 0,
                    sourceId = 1,
                    kind = QueueKind.Album,
                    refId = "al1",
                    rangeStart = 1,
                    rangeEnd = 2,
                )
            val snapshot = repository.snapshotAfter(listOf(entry))

            assertEquals(2, snapshot.size)
            assertEquals(listOf("s2", "s3"), resolveAll(snapshot))
        }

    @Test
    fun windowSpansEntryBoundaries() =
        runTest {
            seedLibrary()

            val entries =
                listOf(
                    repository.albumEntry(1, "al1"),
                    repository.songEntry(1, "s4"),
                )
            val snapshot = repository.snapshotAfter(entries)

            assertEquals(4, snapshot.size)
            val window = repository.window(snapshot, center = 3, radius = 1)
            assertEquals(listOf(2L, 3L), window.map { it.position })
            assertEquals(listOf("s3", "s4"), window.map { it.item.song.id })
        }

    @Test
    fun locateIdentifiesTheEntryBehindEachPosition() =
        runTest {
            seedLibrary()
            val entries = listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4"))
            val snapshot = repository.snapshotAfter(entries)

            val first = snapshot.locate(0)!!
            assertEquals(QueueKind.Album, first.first.kind)
            assertEquals("al1", first.first.refId)
            assertEquals(0L, first.second)

            val last = snapshot.locate(3)!!
            assertEquals(QueueKind.Song, last.first.kind)
            assertEquals("s4", last.first.refId)
            assertEquals(0L, last.second)
        }

    @Test
    fun aRangePastTheEndIsClamped() =
        runTest {
            seedLibrary()

            val entry =
                QueueEntry(
                    position = 0,
                    sourceId = 1,
                    kind = QueueKind.Album,
                    refId = "al1",
                    rangeStart = 1,
                    rangeEnd = 99,
                )
            val snapshot = repository.snapshotAfter(listOf(entry))

            assertEquals(2, snapshot.size)
            assertEquals(listOf("s2", "s3"), resolveAll(snapshot))
        }

    @Test
    fun anInvertedRangeIsEmpty() =
        runTest {
            seedLibrary()

            val entry =
                QueueEntry(
                    position = 0,
                    sourceId = 1,
                    kind = QueueKind.Album,
                    refId = "al1",
                    rangeStart = 2,
                    rangeEnd = 1,
                )
            val snapshot = repository.snapshotAfter(listOf(entry))

            assertEquals(0, snapshot.size)
            assertEquals(null, repository.itemAt(snapshot, 0))
        }

    @Test
    fun positionsPastTheEndResolveNothing() =
        runTest {
            seedLibrary()

            val snapshot = repository.snapshotAfter(listOf(repository.songEntry(1, "s4")))

            assertEquals(null, snapshot.locate(1))
            assertEquals(null, repository.itemAt(snapshot, 1))
        }

    @Test
    fun playlistIgnoresRowsWithoutABackingSong() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    PlaylistSong(1, "p1", "s1", 0),
                    PlaylistSong(1, "p1", "ghost", 1),
                ),
            )

            val snapshot = repository.snapshotAfter(listOf(repository.playlistEntry(1, "p1")))

            assertEquals(1, snapshot.size)
            assertEquals(listOf("s1"), resolveAll(snapshot))
        }

    @Test
    fun anEntryForAMissingSongIsEmpty() =
        runTest {
            seedLibrary()

            val snapshot = repository.snapshotAfter(listOf(repository.songEntry(1, "ghost")))

            assertEquals(0, snapshot.size)
        }

    @Test
    fun playlistEntryFollowsThePlaylistOrder() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    PlaylistSong(1, "p1", "s3", 0),
                    PlaylistSong(1, "p1", "s1", 1),
                ),
            )

            val snapshot = repository.snapshotAfter(listOf(repository.playlistEntry(1, "p1")))

            assertEquals(2, snapshot.size)
            assertEquals(listOf("s3", "s1"), resolveAll(snapshot))
        }

    @Test
    fun replaceDropsThePreviousQueue() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            repository.replace(listOf(repository.songEntry(1, "s4")))

            val snapshot = repository.snapshot()

            assertEquals(1, snapshot.size)
            assertEquals(listOf("s4"), resolveAll(snapshot))
        }

    @Test
    fun rangeResolvesASliceSpanningEntries() =
        runTest {
            seedLibrary()
            val entries = listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4"))
            val snapshot = repository.snapshotAfter(entries)

            val range = repository.range(snapshot, first = 1, last = 3)

            assertEquals(listOf(1L, 2L, 3L), range.map { it.position })
            assertEquals(listOf("s2", "s3", "s4"), range.map { it.item.song.id })
        }

    @Test
    fun removingInsideAnEntrySplitsItIntoTwoRanges() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            repository.removeAt(snapshot, 1)

            assertEquals(listOf("s1", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingTheFirstOrLastTrackKeepsTheRemainingRange() =
        runTest {
            seedLibrary()
            val first = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.removeAt(first, 0)
            assertEquals(listOf("s2", "s3"), resolveAll(repository.snapshot()))

            val last = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.removeAt(last, 2)
            assertEquals(listOf("s1", "s2"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingASongEntryDropsIt() =
        runTest {
            seedLibrary()
            val snapshot =
                repository.snapshotAfter(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))

            repository.removeAt(snapshot, 3)

            assertEquals(listOf("s1", "s2", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun movingWithinAnEntryReordersTheQueue() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            repository.move(repository.snapshot(), from = 0, to = 2)

            assertEquals(listOf("s2", "s3", "s1"), resolveAll(repository.snapshot()))
        }

    @Test
    fun movingAcrossEntriesKeepsTheRestInOrder() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))

            repository.move(repository.snapshot(), from = 0, to = 3)

            assertEquals(listOf("s2", "s3", "s4", "s1"), resolveAll(repository.snapshot()))
        }

    @Test
    fun movingFromOutsideTheQueueChangesNothing() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            assertFalse(repository.move(repository.snapshot(), from = 5, to = 0))
            assertTrue(repository.move(repository.snapshot(), from = 0, to = 2))
            assertEquals(listOf("s2", "s3", "s1"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingASongBetweenContiguousRangesMergesThem() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(
                    albumRange(0, 0),
                    repository.songEntry(1, "s4"),
                    albumRange(1, 2),
                ),
            )
            assertEquals(listOf("s1", "s4", "s2", "s3"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 1)

            val compacted = repository.snapshot()
            assertEquals(1, compacted.entries.size)
            assertEquals(listOf("s1", "s2", "s3"), resolveAll(compacted))
        }

    @Test
    fun rangesSeparatedByAGapAreNotMerged() =
        runTest {
            seedLibrary()
            repository.replace(
                listOf(
                    albumRange(0, 0),
                    repository.songEntry(1, "s4"),
                    albumRange(2, 2),
                ),
            )
            assertEquals(listOf("s1", "s4", "s3"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 1)

            val kept = repository.snapshot()
            assertEquals(2, kept.entries.size)
            assertEquals(listOf("s1", "s3"), resolveAll(kept))
        }

    @Test
    fun movingATrackOutAndBackCompactsTheQueue() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))

            repository.move(repository.snapshot(), from = 3, to = 1)
            assertEquals(listOf("s1", "s4", "s2", "s3"), resolveAll(repository.snapshot()))

            repository.move(repository.snapshot(), from = 1, to = 3)

            val compacted = repository.snapshot()
            assertEquals(listOf("s1", "s2", "s3", "s4"), resolveAll(compacted))
            assertEquals(2, compacted.entries.size)
        }

    @Test
    fun resolvedLengthsSurviveQueueEditsUntilTheLibraryIsInvalidated() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s5")))
            assertEquals(4, repository.snapshot().size)

            repository.removeAt(repository.snapshot(), 3)
            assertEquals(3, repository.snapshot().size)

            db.libraryDao().upsertSongs(listOf(song("s6", "al1", track = 4, album = "First Album")))
            assertEquals(3, repository.snapshot().size)

            repository.invalidateLibraryCache()
            assertEquals(4, repository.snapshot().size)
        }

    @Test
    fun upNextResolvesAfterTheCurrentTrack() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            val updated = repository.snapshot()
            assertEquals(4, updated.size)
            assertEquals(listOf("s1", "s4", "s2", "s3"), resolveAll(updated))
        }

    @Test
    fun playNextGoesToTheFrontOfTheUpNextQueue() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            repository.addUpNext(
                repository.snapshot(),
                currentPosition = 0,
                entry = repository.songEntry(1, "s5"),
                playNext = true,
            )

            assertEquals(listOf("s1", "s5", "s4", "s2", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingAQueuedTrackDropsItFromTheCombinedQueue() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            repository.removeAt(repository.snapshot(), 1)

            val updated = repository.snapshot()
            assertEquals(3, updated.size)
            assertEquals(listOf("s1", "s2", "s3"), resolveAll(updated))
        }

    @Test
    fun movingAQueuedTrackReordersTheUpNextQueue() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)
            repository.addUpNext(
                repository.snapshot(),
                currentPosition = 0,
                entry = repository.songEntry(1, "s5"),
                playNext = false,
            )
            assertEquals(listOf("s1", "s4", "s5", "s2", "s3"), resolveAll(repository.snapshot()))

            repository.move(repository.snapshot(), from = 2, to = 1)

            assertEquals(listOf("s1", "s5", "s4", "s2", "s3"), resolveAll(repository.snapshot()))
            val (entry, _) = repository.snapshot().locate(1)!!
            assertEquals(QueueKind.Song, entry.kind)
        }

    @Test
    fun movingAQueuedAlbumTrackKeepsItsAlbumEntry() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.albumEntry(1, "al2"), playNext = false)

            repository.move(repository.snapshot(), from = 2, to = 1)

            val (entry, _) = repository.snapshot().locate(1)!!
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals("al2", entry.refId)
            assertEquals(1L, entry.rangeStart)
            assertEquals(1L, entry.rangeEnd)
        }

    @Test
    fun movingARangedAlbumTrackInUpNextKeepsTheAbsoluteOffsets() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertSongs(
                listOf(
                    song("s6", "al1", track = 4, album = "First Album"),
                    song("s7", "al1", track = 5, album = "First Album"),
                    song("x1", "al3", track = 1),
                ),
            )
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al2")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = albumRange(1, 4), playNext = false)
            repository.addUpNext(
                repository.snapshot(),
                currentPosition = 0,
                entry = repository.songEntry(1, "x1"),
                playNext = false,
            )
            assertEquals(listOf("s4", "s2", "s3", "s6", "s7", "x1", "s5"), resolveAll(repository.snapshot()))

            repository.move(repository.snapshot(), from = 4, to = 5)

            var updated = repository.snapshot()
            assertEquals(listOf("s4", "s2", "s3", "s6", "x1", "s7", "s5"), resolveAll(updated))
            val moved = updated.locate(5)!!.first
            assertEquals(QueueKind.Album, moved.kind)
            assertEquals(4L, moved.rangeStart)
            assertEquals(4L, moved.rangeEnd)

            repository.move(repository.snapshot(), from = 5, to = 4)

            updated = repository.snapshot()
            assertEquals(listOf("s4", "s2", "s3", "s6", "s7", "x1", "s5"), resolveAll(updated))
            assertTrue(updated.isAlbumContext(4))
        }

    @Test
    fun movingAPlaylistTrackKeepsItAPinnedSong() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    PlaylistSong(1, "p1", "s1", 0),
                    PlaylistSong(1, "p1", "s4", 1),
                ),
            )
            repository.snapshotAfter(listOf(repository.playlistEntry(1, "p1")))

            repository.move(repository.snapshot(), from = 0, to = 1)

            val (entry, _) = repository.snapshot().locate(1)!!
            assertEquals(QueueKind.Song, entry.kind)
            assertEquals("s1", entry.refId)
        }

    @Test
    fun queuedTracksNeverJoinAShuffledContext() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(12345L)
            val shuffled = repository.snapshot()
            val contextBefore = (0 until shuffled.contextSize).mapNotNull { repository.itemAt(shuffled, it)?.song?.id }

            repository.addUpNext(shuffled, currentPosition = 0, entry = repository.songEntry(1, "s5"), playNext = false)

            val updated = repository.snapshot()
            assertEquals(4, updated.size)
            assertEquals("s5", repository.itemAt(updated, updated.anchorPlay + 1)?.song?.id)
            val contextAfter =
                (0 until updated.size)
                    .filterNot { updated.isUpNext(it) }
                    .mapNotNull { repository.itemAt(updated, it)?.song?.id }
            assertEquals(contextBefore, contextAfter)
            assertTrue(updated.shuffled)
        }

    @Test
    fun consumingASliceOfAQueuedAlbumRemovesTheRightTrack() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.albumEntry(1, "al2"), playNext = false)
            assertEquals(listOf("s1", "s4", "s5", "s2", "s3"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 2)

            assertEquals(listOf("s1", "s4", "s2", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingTheAnchorTrackPutsTheQueueFirst() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)
            assertEquals(listOf("s1", "s4", "s2", "s3"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 0)

            assertEquals(listOf("s4", "s2", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun removingTheLastAnchorTrackKeepsTheQueueResolvable() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 2, entry = repository.songEntry(1, "s4"), playNext = false)
            assertEquals(listOf("s1", "s2", "s3", "s4"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 2)

            val updated = repository.snapshot()
            assertEquals(3, updated.size)
            assertEquals(listOf("s1", "s2", "s4"), resolveAll(updated))
        }

    @Test
    fun removingAContextTrackBeforeTheAnchorKeepsTheQueueAttached() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 1, entry = repository.songEntry(1, "s4"), playNext = false)
            assertEquals(listOf("s1", "s2", "s4", "s3"), resolveAll(repository.snapshot()))

            repository.removeAt(repository.snapshot(), 0)

            assertEquals(listOf("s2", "s4", "s3"), resolveAll(repository.snapshot()))
        }

    @Test
    fun replacingTheContextClearsTheUpNextQueue() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            repository.replace(listOf(repository.songEntry(1, "s5")))

            val updated = repository.snapshot()
            assertTrue(updated.upNext.isEmpty())
            assertEquals(1, updated.size)
            assertEquals(listOf("s5"), resolveAll(updated))
        }

    private fun albumRange(
        start: Long,
        end: Long,
    ) = QueueEntry(position = 0, sourceId = 1, kind = QueueKind.Album, refId = "al1", rangeStart = start, rangeEnd = end)

    private suspend fun QueueRepository.snapshotAfter(entries: List<QueueEntry>): QueueSnapshot {
        replace(entries)
        return snapshot()
    }

    private suspend fun resolveAll(snapshot: QueueSnapshot): List<String> =
        (0 until snapshot.size).mapNotNull { repository.itemAt(snapshot, it)?.song?.id }

    @Test
    fun aStaleShuffleSizeIsIgnoredWhenTheQueueLengthChanges() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(1234L)
            assertTrue(repository.snapshot().shuffled)

            db.libraryDao().upsertSongs(listOf(song("s6", "al1", track = 4, album = "First Album")))
            repository.invalidateLibraryCache()

            assertFalse(repository.snapshot().shuffled)
        }

    @Test
    fun enablingShuffleStoresNoPerTrackRows() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            val entriesBefore = db.queueDao().entries().size

            repository.setShuffle(5678L)
            val snapshot = repository.snapshot()

            assertTrue(snapshot.shuffled)
            assertEquals(3L, snapshot.contextSize)
            assertEquals(entriesBefore, db.queueDao().entries().size)
            assertFalse(shuffleOrderTableExists())
        }

    private suspend fun shuffleOrderTableExists(): Boolean =
        db.useReaderConnection { transactor ->
            transactor.usePrepared(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'shuffle_order'",
            ) { statement ->
                if (statement.step()) statement.getLong(0) > 0 else false
            }
        }

    @Test
    fun shuffledPositionsResolveToEveryTrackExactlyOnce() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(42L)
            val shuffled = repository.snapshot()

            val resolved = (0 until shuffled.size).mapNotNull { repository.itemAt(shuffled, it)?.song?.id }

            assertEquals(3, resolved.distinct().size)
            assertEquals(listOf("s1", "s2", "s3"), resolved.sorted())
            val flatPositions = (0 until shuffled.size).map { shuffled.flatContext(it)!! }
            assertEquals((0L until shuffled.size).toList(), flatPositions.sorted())
        }

    @Test
    fun togglingShuffleOffRestoresTheOriginalOrder() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(99L)
            assertTrue(repository.snapshot().shuffled)

            repository.setShuffle(null)

            val restored = repository.snapshot()
            assertFalse(restored.shuffled)
            assertEquals(listOf("s1", "s2", "s3"), resolveAll(restored))
        }

    @Test
    fun shuffledLargeContextWindowOnlyResolvesRequestedRows() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylists(listOf(playlist("huge", "Huge")))
            db.useWriterConnection { transactor ->
                transactor.usePrepared(
                    "WITH RECURSIVE n(x) AS (VALUES(0) UNION ALL SELECT x + 1 FROM n WHERE x < 199999) " +
                        "INSERT INTO playlist_songs SELECT 1, 'huge', 's1', x FROM n",
                ) { statement ->
                    statement.step()
                }
            }
            repository.snapshotAfter(listOf(repository.playlistEntry(1, "huge")))
            repository.setShuffle(42L)
            val snapshot = repository.snapshot()

            val window = repository.window(snapshot, center = 100_000, radius = 25)

            assertEquals(200_000L, snapshot.contextSize)
            assertEquals(51, window.size)
            assertEquals(51L, repository.resolvedRowCount)
            assertEquals((99_975L..100_025L).toList(), window.map { it.position })
            assertTrue(window.all { it.item.song.id == "s1" })
        }

    @Test
    fun shuffledRangeResolvesEntriesInPositionOrder() =
        runTest {
            seedLibrary()
            repository.snapshotAfter(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))
            repository.setShuffle(42L)
            val snapshot = repository.snapshot()

            val range = repository.range(snapshot, 0, snapshot.size - 1)

            assertEquals((0L until snapshot.size).toList(), range.map { it.position })
            assertEquals(setOf("s1", "s2", "s3", "s4"), range.map { it.item.song.id }.toSet())
        }

    @Test
    fun shuffledRangeResolvesASplitEntryWithoutRepeatingOrSkipping() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.removeAt(snapshot, 1)
            repository.setShuffle(7L)
            val shuffled = repository.snapshot()

            val range = repository.range(shuffled, 0, shuffled.size - 1)

            assertEquals(listOf(0L, 1L), range.map { it.position })
            assertEquals(setOf("s1", "s3"), range.map { it.item.song.id }.toSet())
        }

    @Test
    fun shuffledRemovalsSurviveRepositoryRecreation() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))
            repository.setShuffle(9L)
            val before = repository.snapshot()
            val expected = resolveAll(before).filterIndexed { index, _ -> index != 1 }
            repository.removeAt(before, 1)
            repository = QueueRepository(db)
            val restored = repository.snapshot()
            assertTrue(restored.shuffled)
            assertEquals(expected, resolveAll(restored))
            (0 until restored.contextSize).forEach { play ->
                assertEquals(play, restored.playContext(restored.flatContext(play)!!))
            }
            repository.removeAt(restored, 0)
            repository = QueueRepository(db)
            assertEquals(expected.drop(1), resolveAll(repository.snapshot()))
            repository.setShuffle(null)
            repository.setShuffle(42)
            repository = QueueRepository(db)
            val fresh = repository.snapshot()
            assertTrue(fresh.shuffled)
            assertEquals(fresh.contextSize, fresh.shuffle!!.domain)
            assertEquals("", fresh.shuffle.encodeRemoved())
        }

    @Test
    fun removingAContextTrackWhileShuffledKeepsTheRemainingOrder() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(42L)
            val before = repository.snapshot()
            val orderBefore = (0 until before.contextSize).mapNotNull { repository.itemAt(before, it)?.song?.id }

            repository.removeAt(before, 1)

            val after = repository.snapshot()
            assertTrue(after.shuffled)
            assertEquals(2L, after.contextSize)
            val orderAfter = (0 until after.contextSize).mapNotNull { repository.itemAt(after, it)?.song?.id }
            assertEquals(orderBefore.filterIndexed { index, _ -> index != 1 }, orderAfter)
        }

    @Test
    fun severalRemovalsWhileShuffledKeepTheRemainingOrder() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.setShuffle(42L)
            val before = repository.snapshot()
            val orderBefore = (0 until before.contextSize).mapNotNull { repository.itemAt(before, it)?.song?.id }

            repository.removeAt(before, 0)
            repository.removeAt(repository.snapshot(), 0)

            val after = repository.snapshot()
            assertTrue(after.shuffled)
            assertEquals(1L, after.contextSize)
            val orderAfter = (0 until after.contextSize).mapNotNull { repository.itemAt(after, it)?.song?.id }
            assertEquals(orderBefore.drop(2), orderAfter)
        }

    @Test
    fun removingWhileShuffledKeepsTheOrderInverse() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1"), repository.songEntry(1, "s4")))
            repository.setShuffle(9L)
            repository.removeAt(repository.snapshot(), 2)

            val after = repository.snapshot()
            assertEquals(3L, after.contextSize)
            for (play in 0 until after.contextSize) {
                assertEquals(play, after.playContext(after.flatContext(play)!!))
            }
            assertEquals((0L until after.contextSize).toList(), (0 until after.contextSize).map { after.flatContext(it)!! }.sorted())
        }

    @Test
    fun removingAnyPositionWhileShuffledKeepsTheCoordinatesConsistent() =
        runTest {
            seedLibrary()
            for (seed in listOf(1L, 42L, 999L)) {
                for (position in 0L..4L) {
                    repository.replace(listOf(repository.albumEntry(1, "al1")))
                    repository.setShuffle(seed)
                    repository.addUpNext(
                        repository.snapshot(),
                        currentPosition = 0,
                        entry = repository.songEntry(1, "s4"),
                        playNext = false,
                    )
                    val before = repository.snapshot()
                    if (position >= before.size) continue

                    repository.removeAt(before, position)

                    val after = repository.snapshot()
                    assertTrue("seed=$seed position=$position stopped being shuffled", after.shuffled)
                    assertEquals(before.size - 1, after.size)
                    val flats = (0 until after.contextSize).map { after.flatContext(it)!! }
                    assertEquals((0L until after.contextSize).toList(), flats.sorted())
                    for (play in 0 until after.contextSize) {
                        assertEquals(play, after.playContext(after.flatContext(play)!!))
                    }
                    for (p in 0 until after.size) {
                        if (after.isUpNext(p)) continue
                        assertEquals(p, after.combined(after.contextPlay(p)))
                    }
                    val block = (0 until after.size).filter { after.isUpNext(it) }
                    assertEquals(
                        (after.anchorPlay + 1..after.anchorPlay + after.upNextSize).toList(),
                        block,
                    )
                    val ids = (0 until after.size).mapNotNull { repository.itemAt(after, it)?.song?.id }
                    assertEquals(ids.distinct().size, ids.size)
                }
            }
        }

    @Test
    fun contextIndexUsesTheFirstDuplicateOrdinalAcrossSparsePositions() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylists(listOf(playlist("duplicates", "Duplicates")))
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    PlaylistSong(1, "duplicates", "s2", 10),
                    PlaylistSong(1, "duplicates", "s1", 20),
                    PlaylistSong(1, "duplicates", "s2", 30),
                ),
            )
            val snapshot = repository.snapshotAfter(listOf(repository.playlistEntry(1, "duplicates")))

            assertEquals(0L, repository.combinedContextIndexOf(snapshot, "s2"))
            assertEquals(1L, repository.combinedContextIndexOf(snapshot, "s1"))
        }

    @Test
    fun contextIndexFindsADuplicateInsideTheEntryRange() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylists(listOf(playlist("duplicates", "Duplicates")))
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    PlaylistSong(1, "duplicates", "s2", 10),
                    PlaylistSong(1, "duplicates", "s1", 20),
                    PlaylistSong(1, "duplicates", "s2", 30),
                ),
            )
            val entry = repository.playlistEntry(1, "duplicates").copy(rangeStart = 1, rangeEnd = 2)
            val snapshot = repository.snapshotAfter(listOf(entry))

            assertEquals(1L, repository.combinedContextIndexOf(snapshot, "s2"))
        }

    @Test
    fun contextIndexFallsBackToBoundedRowsForGenre() =
        runTest {
            seedLibrary()
            insertGenre("s1", "Rock")
            insertGenre("s3", "Rock")
            insertGenre("s4", "Rock")
            val snapshot = repository.snapshotAfter(listOf(repository.genreEntry(1, "Rock")))

            assertEquals(2L, repository.combinedContextIndexOf(snapshot, "s4"))
            assertTrue(repository.resolvedRowCount <= QUEUE_CHUNK)
        }

    @Test
    fun upNextCombinedIndexOfCountsFromTheAnchorIntoTheBlock() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            repository.addUpNext(snapshot, currentPosition = 1, entry = repository.songEntry(1, "s4"), playNext = false)

            val updated = repository.snapshot()
            assertEquals(1L, updated.anchorPlay)
            assertEquals(1L, updated.upNextSize)
            assertEquals(2L, repository.combinedIndexOf(updated, "s4"))
            assertEquals(1L, repository.combinedContextIndexOf(updated, "s2"))
            assertEquals(3L, repository.combinedContextIndexOf(updated, "s3"))
        }

    @Test
    fun combinedContextIndexOfTracksTheAnchorAsItMoves() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            val before = repository.snapshot()
            assertEquals(0L, before.anchorPlay)
            assertEquals(3L, repository.combinedContextIndexOf(before, "s3"))

            repository.setUpNextAnchor(2)

            val after = repository.snapshot()
            assertEquals(2L, after.anchorPlay)
            assertEquals(2L, repository.combinedContextIndexOf(after, "s3"))
            assertEquals(3L, repository.combinedIndexOf(after, "s4"))
        }

    @Test
    fun combinedIndexOfPrefersTheUpNextCopyOverTheContextCopy() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.songEntry(1, "s4")))
            repository.addUpNext(snapshot, currentPosition = 0, entry = repository.songEntry(1, "s4"), playNext = false)

            val updated = repository.snapshot()
            assertEquals(0L, repository.combinedContextIndexOf(updated, "s4"))
            assertEquals(1L, repository.combinedIndexOf(updated, "s4"))
        }

    @Test
    fun sourceNameReadsTheAlbumPlaylistOrSongAlbum() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertPlaylists(listOf(playlist("p1", "Road Trip")))

            assertEquals("First Album", repository.sourceName(QueueKind.Album, 1, "al1"))
            assertEquals("Road Trip", repository.sourceName(QueueKind.Playlist, 1, "p1"))
            assertEquals("Second Album", repository.sourceName(QueueKind.Song, 1, "s4"))
            assertEquals(null, repository.sourceName(QueueKind.Song, 1, "ghost"))
        }

    @Test
    fun aReorderedAlbumTrackKeepsItsAlbumAsSourceName() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))

            repository.move(repository.snapshot(), from = 0, to = 2)

            val (entry, _) = repository.snapshot().locate(2)!!
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals("al1", entry.refId)
            assertEquals(0L, entry.rangeStart)
            assertEquals(0L, entry.rangeEnd)
            assertEquals("First Album", repository.sourceName(entry.kind, entry.sourceId, entry.refId))
        }

    @Test
    fun movingWithinARangedAlbumKeepsTheAbsoluteOffsets() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertSongs(
                listOf(
                    song("s6", "al1", track = 4, album = "First Album"),
                    song("s7", "al1", track = 5, album = "First Album"),
                ),
            )
            repository.snapshotAfter(listOf(albumRange(1, 4)))
            assertEquals(listOf("s2", "s3", "s6", "s7"), resolveAll(repository.snapshot()))

            repository.move(repository.snapshot(), from = 0, to = 2)

            val snapshot = repository.snapshot()
            assertEquals(listOf("s3", "s6", "s2", "s7"), resolveAll(snapshot))
            val (entry, _) = snapshot.locate(2)!!
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals(1L, entry.rangeStart)
            assertEquals(1L, entry.rangeEnd)
        }

    @Test
    fun movingBackwardWithinARangedAlbumKeepsTheAbsoluteOffsets() =
        runTest {
            seedLibrary()
            db.libraryDao().upsertSongs(
                listOf(
                    song("s6", "al1", track = 4, album = "First Album"),
                    song("s7", "al1", track = 5, album = "First Album"),
                ),
            )
            repository.snapshotAfter(listOf(albumRange(1, 4)))

            repository.move(repository.snapshot(), from = 3, to = 0)

            val snapshot = repository.snapshot()
            assertEquals(listOf("s7", "s2", "s3", "s6"), resolveAll(snapshot))
            val (entry, _) = snapshot.locate(0)!!
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals(4L, entry.rangeStart)
            assertEquals(4L, entry.rangeEnd)
        }

    @Test
    fun movingAnAlbumTrackBackCompactsTheAlbumEntry() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))

            repository.move(repository.snapshot(), from = 0, to = 2)
            repository.move(repository.snapshot(), from = 2, to = 0)

            val snapshot = repository.snapshot()
            assertEquals(listOf("s1", "s2", "s3"), resolveAll(snapshot))
            val entry = snapshot.entries.single().entry
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals(0L, entry.rangeStart)
            assertEquals(2L, entry.rangeEnd)
        }

    @Test
    fun anAlbumSplitByRemovalIsStillLabeledByItsAlbum() =
        runTest {
            seedLibrary()
            val snapshot = repository.snapshotAfter(listOf(repository.albumEntry(1, "al1")))

            repository.removeAt(snapshot, 1)

            val (entry, _) = repository.snapshot().locate(1)!!
            assertEquals(QueueKind.Album, entry.kind)
            assertEquals("First Album", repository.sourceName(entry.kind, entry.sourceId, entry.refId))
        }

    @Test
    fun queueModesRoundTrip() =
        runTest {
            seedLibrary()
            repository.replace(listOf(repository.albumEntry(1, "al1")))
            assertEquals(QueueModes(shuffle = false, repeat = 0), repository.modes())

            repository.setShuffle(4321L)
            repository.setRepeat(2)
            assertEquals(QueueModes(shuffle = true, repeat = 2), repository.modes())

            repository.setShuffle(null)
            repository.setRepeat(0)
            assertEquals(QueueModes(shuffle = false, repeat = 0), repository.modes())
        }

    private fun playlist(
        id: String,
        name: String,
    ) = Playlist(
        sourceId = 1,
        id = id,
        name = name,
        comment = null,
        coverArt = null,
        songCount = 0,
        created = 0,
    )

    private suspend fun seedLibrary() {
        db.sourcesDao().upsertSource(
            Source(id = 1, name = "test", address = "http://localhost", isActive = true, createdAt = 0),
        )
        db.libraryDao().upsertAlbums(
            listOf(
                album("al1", "First Album", "Artist One"),
                album("al2", "Second Album", "Artist Two"),
            ),
        )
        db.libraryDao().upsertSongs(
            listOf(
                song("s1", "al1", track = 1, album = "First Album"),
                song("s2", "al1", track = 2, album = "First Album"),
                song("s3", "al1", track = 3, album = "First Album"),
                song("s4", "al2", track = 1, album = "Second Album"),
                song("s5", "al2", track = 2, album = "Second Album"),
            ),
        )
    }

    private fun album(
        id: String,
        name: String,
        albumArtist: String,
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar1",
        name = name,
        albumArtist = albumArtist,
        created = 0,
        coverArt = null,
        genre = null,
        year = null,
        starred = null,
        songCount = 1,
    )

    private fun song(
        id: String,
        albumId: String,
        track: Long,
        album: String = "Album",
        title: String = "Song $id",
        starred: Long? = null,
        created: Long = 0,
    ) = Song(
        sourceId = 1,
        id = id,
        albumId = albumId,
        artistId = "ar1",
        title = title,
        album = album,
        artist = "Artist",
        duration = 100,
        track = track,
        disc = 1,
        starred = starred,
        genre = null,
        created = created,
    )

    private suspend fun insertGenre(
        songId: String,
        genre: String,
    ) {
        db.useWriterConnection { connection ->
            connection.usePrepared(
                "INSERT INTO song_genres (sourceId, songId, position, genre) VALUES (1, '$songId', 0, '$genre')",
            ) { it.step() }
        }
    }
}
