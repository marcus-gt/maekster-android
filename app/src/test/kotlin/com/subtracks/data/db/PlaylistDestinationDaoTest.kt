package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDestinationDaoTest {
    private lateinit var db: SubtracksDatabase
    private val dao get() = db.playlistDestinationDao()

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "one", "http://one", true, 1))
            db.sourcesDao().upsertSource(Source(2, "two", "http://two", false, 1))
            db.libraryDao().upsertPlaylists(
                (1..7).map { Playlist(1, "p$it", "List $it", null, null, 0, 1) } + Playlist(2, "p1", "Other", null, null, 0, 1),
            )
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun recent(sourceId: Long = 1) = dao.recent(sourceId).first().map { it.id }

    @Test
    fun theLatestUseLeadsAndOnlyFiveAreKept() =
        runTest {
            (1..7).forEach { dao.record(1, "p$it", usedAt = 100) }
            assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), recent())
            dao.record(1, "p5", usedAt = 1)
            assertEquals(listOf("p5", "p7", "p6", "p4", "p3"), recent())
        }

    @Test
    fun eachServerKeepsItsOwnAndDeletingAServerClearsThem() =
        runTest {
            dao.record(1, "p1", 10)
            dao.record(2, "p1", 10)
            assertEquals(listOf("p1"), recent(2))
            assertEquals(
                "Other",
                dao
                    .recent(2)
                    .first()
                    .single()
                    .name,
            )
            db.sourcesDao().deleteSource(2)
            assertEquals(emptyList<String>(), recent(2))
            assertEquals(listOf("p1"), recent(1))
        }

    @Test
    fun aPlaylistGoneFromTheMirrorIsHiddenButReturnsWhenMirroredAgain() =
        runTest {
            dao.record(1, "p1", 10)
            dao.record(1, "p2", 20)
            db.libraryDao().removePlaylist(1, "p1")
            assertEquals(listOf("p2"), recent())
            db.libraryDao().upsertPlaylists(listOf(Playlist(1, "p1", "List 1", null, null, 0, 1)))
            assertEquals(listOf("p2", "p1"), recent())
        }
}
