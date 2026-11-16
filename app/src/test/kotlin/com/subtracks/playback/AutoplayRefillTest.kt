package com.subtracks.playback

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.repo.QueueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoplayRefillTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var queue: QueueRepository
    private var sourceId = 0L
    private var enabled = true
    private var active: Long? = null
    private var calls = 0
    private var generate: suspend () -> List<String> = { (0..90).map { "s$it" } }

    @Before fun setUp() =
        runTest {
            db = createTestDatabase()
            sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
            active = sourceId
            db.libraryDao().upsertSongs(
                (0..90).map { Song(sourceId, "s$it", "al", "ar$it", "Song $it", "Album", "Artist", 100, 1, 1, null) },
            )
            queue = QueueRepository(db)
            queue.replace((0..16).map { queue.songEntry(sourceId, "s$it") })
        }

    @After fun tearDown() = db.close()

    private fun refill() =
        AutoplayRefill(queue, db.mixDao(), { _, _, _ ->
            calls++
            generate()
        }, { enabled }, { active })

    @Test fun thresholdAndModesPreventGeneration() =
        runTest {
            val refill = refill()
            assertFalse(refill.extend(queue.snapshot(), 0, false))
            enabled = false
            assertFalse(refill.extend(queue.snapshot(), 1, false))
            enabled = true
            queue.setRepeat(1)
            assertFalse(refill.extend(queue.snapshot(), 1, false))
            queue.setRepeat(0)
            queue.setShuffle(42)
            assertFalse(refill.extend(queue.snapshot(), 1, false))
            assertEquals(0, calls)
            queue.setShuffle(null)
            assertTrue(refill.extend(queue.snapshot(), 1, false))
            assertEquals(1, calls)
            assertEquals(
                (17..49).map { "s$it" },
                queue
                    .snapshot()
                    .entries
                    .drop(17)
                    .map { it.entry.refId },
            )
            assertEquals(17L, queue.extensionStart())
        }

    @Test fun recentPlaysAreExcludedEvenOutsideTheQueue() =
        runTest {
            db.useWriterConnection { connection ->
                connection.usePrepared(
                    "INSERT INTO play_events (sourceId, songId, playedAt, title) VALUES ($sourceId, 's20', 10, 'Recent')",
                ) {
                    it.step()
                }
            }
            generate = { listOf("s20", "s21") }
            assertTrue(refill().extend(queue.snapshot(), 1, false))
            assertEquals(
                listOf("s21"),
                queue
                    .snapshot()
                    .entries
                    .drop(17)
                    .map { it.entry.refId },
            )
        }

    @Test fun offlineCandidatesMustBeDownloadedAndNearbySongsAreExcluded() =
        runTest {
            db.downloadDao().upsert(SongDownload(sourceId, "s20", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(sourceId, "s1", DownloadStatus.Completed))
            assertTrue(refill().extend(queue.snapshot(), 1, true))
            assertEquals(
                listOf("s20"),
                queue
                    .snapshot()
                    .entries
                    .drop(17)
                    .map { it.entry.refId },
            )
        }

    @Test fun inFlightWorkIsCoalescedAndDisablingDiscardsItsResult() =
        runTest {
            val waiting = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            generate = {
                waiting.complete(Unit)
                release.await()
                listOf("s20")
            }
            val refill = refill()
            val snapshot = queue.snapshot()
            val first = async { refill.extend(snapshot, 1, false) }
            waiting.await()
            assertFalse(refill.extend(snapshot, 1, false))
            enabled = false
            release.complete(Unit)
            assertFalse(first.await())
            assertEquals(17L, queue.snapshot().size)
            assertEquals(1, calls)
        }

    @Test fun replacementOrSourceSwitchDuringGenerationDiscardsItsResult() =
        runTest {
            generate = {
                queue.replace(listOf(queue.songEntry(sourceId, "s1")))
                listOf("s20")
            }
            assertFalse(refill().extend(queue.snapshot(), 1, false))
            assertEquals(1L, queue.snapshot().size)
            queue.replace((0..16).map { queue.songEntry(sourceId, "s$it") })
            generate = {
                active = null
                listOf("s20")
            }
            assertFalse(refill().extend(queue.snapshot(), 1, false))
            assertEquals(17L, queue.snapshot().size)
        }
}
