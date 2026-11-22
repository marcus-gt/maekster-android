package com.subtracks.data.recommendations

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TopSongsRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var repository: TopSongsRepository
    private var activeSource = 1L
    private val requests = mutableListOf<Triple<Long, String, Int>>()
    private var answer: suspend () -> List<String> = { listOf("b", "missing", "a", "b", "") }

    @Before fun setUp() {
        db = createTestDatabase()
        repository =
            TopSongsRepository(
                db.topSongsDao(),
                { source, name, count ->
                    requests += Triple(source, name, count)
                    answer()
                },
                { activeSource },
            )
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        db.sourcesDao().upsertSource(Source(1, "server", "https://server", true, 1))
        db.sourcesDao().upsertSource(Source(2, "other", "https://other", false, 2))
        db.libraryDao().upsertSongs(
            listOf(
                Song(1, "a", null, "artist", "A", null, "Artist", 60, null, null, 100),
                Song(1, "b", null, "artist", "B", null, "Artist", 60, null, null, null),
                Song(2, "missing", null, "artist", "Other source", null, "Artist", 60, null, null, null),
            ),
        )
    }

    @Test fun fetchUsesArtistNameAndResolvesInServerOrderFromTheSameSource() =
        runTest {
            seed()
            assertTrue(repository.refresh(1, "artist", "Artist & Band"))
            assertEquals(listOf(Triple(1L, "Artist & Band", 25)), requests)
            val resolved = repository.songs(1, "artist", false).first()
            assertEquals(listOf("b", "a"), resolved.map { it.song.id })
            assertEquals(listOf(null, 100L), resolved.map { it.song.starred })
            assertTrue(repository.songs(2, "artist", false).first().isEmpty())
            assertTrue(repository.songs(1, "other", false).first().isEmpty())
        }

    @Test fun offlineCacheOnlyIncludesCompletedDownloads() =
        runTest {
            seed()
            repository.refresh(1, "artist", "Artist")
            db.downloadDao().upsert(SongDownload(1, "a", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "b", DownloadStatus.Queued))
            assertEquals(listOf("a"), repository.songs(1, "artist", true).first().map { it.song.id })
            assertEquals(1, requests.size)
        }

    @Test fun failedRefreshPreservesCacheAndSuccessfulEmptyAnswerClearsIt() =
        runTest {
            seed()
            assertFalse(repository.fetched(1, "artist").first())
            repository.refresh(1, "artist", "Artist")
            answer = { throw IOException("unavailable") }
            assertFalse(repository.refresh(1, "artist", "Artist"))
            assertEquals(listOf("b", "a"), repository.songs(1, "artist", false).first().map { it.song.id })
            answer = { emptyList() }
            assertTrue(repository.refresh(1, "artist", "Artist"))
            assertTrue(repository.fetched(1, "artist").first())
            assertTrue(repository.songs(1, "artist", false).first().isEmpty())
        }

    @Test fun aReplyFromAnInactiveSourceIsDiscardedAndRemovingTheSourceClearsCache() =
        runTest {
            seed()
            repository.refresh(1, "artist", "Artist")
            answer = {
                activeSource = 2
                emptyList()
            }
            assertFalse(repository.refresh(1, "artist", "Artist"))
            assertEquals(listOf("b", "a"), repository.songs(1, "artist", false).first().map { it.song.id })
            db.sourcesDao().deleteSource(1)
            assertFalse(repository.fetched(1, "artist").first())
        }
}
