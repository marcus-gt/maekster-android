package com.subtracks.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.mix.SmartShuffle
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartShufflePlayerTest {
    private lateinit var db: SubtracksDatabase
    private var started: Pair<Long, List<String>>? = null

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun player(
        sourceId: Long?,
        offline: Boolean,
    ) = SmartShufflePlayer(SmartShuffle(db.mixDao()), { sourceId }, { offline }) { id, songs -> started = id to songs }

    @Test
    fun noServerOrAnEmptyLibraryPlaysNothing() =
        runTest {
            assertFalse(player(sourceId = null, offline = false).play())
            val sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
            assertFalse(player(sourceId, offline = false).play())
            assertNull(started)
        }

    @Test
    fun offlineOnlyDownloadedSongsAreStarted() =
        runTest {
            val sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
            db.libraryDao().upsertSongs(
                listOf("a", "b", "c").map {
                    Song(sourceId, it, "al", "ar-$it", it, "Album", "Artist", 100, 1, 1, null)
                },
            )
            db.downloadDao().upsert(SongDownload(sourceId, "b", DownloadStatus.Completed))

            assertTrue(player(sourceId, offline = true).play())
            assertEquals(sourceId to listOf("b"), started)

            assertTrue(player(sourceId, offline = false).play())
            assertEquals(setOf("a", "b", "c"), started!!.second.toSet())
        }
}
