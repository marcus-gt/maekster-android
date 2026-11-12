package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.SearchHistoryEntry
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchHistoryDaoTest {
    private lateinit var db: SubtracksDatabase
    private val dao get() = db.searchHistoryDao()

    @Before fun setUp() {
        db = createTestDatabase()
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun source() = db.sourcesDao().insertSource(Source(name = "Server", address = "http://localhost", createdAt = 0))

    private fun album(
        sourceId: Long,
        id: String,
        name: String = "Album $id",
    ) = Album(sourceId, id, null, name, "Artist", 0, "art-$id", null, null, null, 1)

    private suspend fun visit(
        sourceId: Long,
        kind: PinKind,
        id: String,
        at: Long,
    ) = dao.record(SearchHistoryEntry(sourceId, kind, id, at))

    @Test
    fun opensAreListedNewestFirstAndReopeningMovesAnItemToTheTop() =
        runTest {
            val source = source()
            db.libraryDao().upsertAlbums(listOf(album(source, "a")))
            db.libraryDao().upsertArtists(listOf(Artist(source, "a", "Same id artist", 1, null, "artist-art")))
            db.libraryDao().upsertPlaylists(listOf(Playlist(source, "p", "Playlist", null, "pl-art", 1, 0)))
            visit(source, PinKind.Album, "a", 1)
            visit(source, PinKind.Artist, "a", 2)
            visit(source, PinKind.Playlist, "p", 3)
            assertEquals(listOf("Playlist", "Same id artist", "Album a"), dao.recent(source).first().map { it.name })

            visit(source, PinKind.Album, "a", 4)
            val recent = dao.recent(source).first()
            assertEquals(listOf("Album a", "Playlist", "Same id artist"), recent.map { it.name })
            assertEquals(listOf("art-a", "pl-art", "artist-art"), recent.map { it.coverArt })
            assertEquals("Artist", recent.first().subtitle)
        }

    @Test
    fun theLatestOpenStaysFirstEvenIfTheClockMovedBack() =
        runTest {
            val source = source()
            db.libraryDao().upsertAlbums(listOf(album(source, "a"), album(source, "b")))
            visit(source, PinKind.Album, "a", 10)
            visit(source, PinKind.Album, "b", 20)
            visit(source, PinKind.Album, "a", 5)

            assertEquals(listOf("a", "b"), dao.recent(source).first().map { it.refId })
        }

    @Test
    fun eachServerKeepsOnlyItsNewestFifty() =
        runTest {
            val source = source()
            val other = source()
            db.libraryDao().upsertAlbums((1..55).map { album(source, "$it") } + album(other, "1"))
            visit(other, PinKind.Album, "1", 0)
            (1..55).forEach { visit(source, PinKind.Album, "$it", it.toLong()) }

            val recent = dao.recent(source, limit = 100).first()
            assertEquals(SEARCH_HISTORY_LIMIT, recent.size)
            assertEquals("55", recent.first().refId)
            assertEquals("6", recent.last().refId)
            assertEquals(listOf("1"), dao.recent(other).first().map { it.refId })
        }

    @Test
    fun itemsMissingFromTheMirrorAreHiddenUntilTheyReturn() =
        runTest {
            val source = source()
            visit(source, PinKind.Album, "gone", 1)
            assertTrue(dao.recent(source).first().isEmpty())

            db.libraryDao().upsertAlbums(listOf(album(source, "gone", "Back again")))
            assertEquals(listOf("Back again"), dao.recent(source).first().map { it.name })
        }

    @Test
    fun clearingAndDeletingAServerOnlyAffectThatServer() =
        runTest {
            val first = source()
            val second = source()
            val third = source()
            db.libraryDao().upsertAlbums(listOf(album(first, "a"), album(second, "a"), album(third, "a")))
            listOf(first, second, third).forEach { visit(it, PinKind.Album, "a", 1) }

            dao.clear(first)
            db.sourcesDao().deleteSource(second)

            assertTrue(dao.recent(first).first().isEmpty())
            assertTrue(dao.recent(second).first().isEmpty())
            assertEquals(1, dao.recent(third).first().size)
            visit(first, PinKind.Album, "a", 2)
            assertEquals(1, dao.recent(first).first().size)
        }
}
