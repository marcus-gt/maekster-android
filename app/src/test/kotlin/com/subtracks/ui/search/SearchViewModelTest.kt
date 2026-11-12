package com.subtracks.ui.search

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.awaitUntil
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinRef
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Song
import com.subtracks.ui.ViewModelRepositoryFixture
import com.subtracks.ui.components.MenuTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {
    private lateinit var fixture: ViewModelRepositoryFixture
    private val db get() = fixture.db
    private val sourceRepository get() = fixture.sourceRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        fixture = ViewModelRepositoryFixture()
        viewModel = SearchViewModel(fixture.libraryRepository, sourceRepository, fixture.controller)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        fixture.close()
    }

    @Test
    fun searchFindsEveryEntityTypeAndBlankClearsTheResults() {
        runBlocking {
            val id = sourceRepository.addSource("nav", "http://localhost/", "u", "p", false)
            db.libraryDao().upsertAlbums(listOf(album(id, "al-1", "Road Album")))
            db.libraryDao().upsertArtists(listOf(artist(id, "ar-1", "Road Artist")))
            db.libraryDao().upsertPlaylists(listOf(playlist(id, "pl-1", "Road Trip")))
            db.libraryDao().upsertSongs(listOf(song(id, "s1", "Road Song")))
        }

        viewModel.setQuery("oad")
        val results = runBlocking { withTimeout(5_000) { viewModel.results.first { !it.isEmpty } } }

        assertEquals(listOf("Road Album"), results.albums.map { it.name })
        assertEquals(listOf("ar-1"), results.artists.map { it.id })
        assertEquals(listOf("pl-1"), results.playlists.map { it.id })
        assertEquals(listOf("s1"), results.songs.map { it.song.id })

        viewModel.setQuery("oa")
        val short = runBlocking { withTimeout(5_000) { viewModel.results.first { it.isEmpty } } }

        assertTrue(short.isEmpty)

        viewModel.setQuery("")
        val blank = runBlocking { withTimeout(5_000) { viewModel.results.first { it.isEmpty } } }

        assertTrue(blank.isEmpty)
    }

    @Test
    fun searchReadyCountsCodePointsRatherThanUtf16Units() {
        assertTrue(searchReady("road"))
        assertFalse(searchReady("ro"))
        assertFalse(searchReady("a\uD83D\uDE00"))
    }

    @Test
    fun playingASearchResultPlaysItsAlbumInsteadOfASearchQueue() {
        val sourceId =
            runBlocking {
                val id = sourceRepository.addSource("nav", "http://localhost/", "u", "p", false)
                db.libraryDao().upsertAlbums(listOf(album(id, "al-1", "Road Album")))
                db.libraryDao().upsertSongs(
                    listOf(
                        song(id, "s1", "Road Song"),
                        song(id, "s2", "Another").copy(track = 2),
                    ),
                )
                id
            }

        viewModel.play(song(sourceId, "s1", "Road Song"))
        awaitUntil("the queue is built") { runBlocking { db.queueDao().entries().isNotEmpty() } }

        val entries = runBlocking { db.queueDao().entries() }
        assertEquals(1, entries.size)
        assertEquals(QueueKind.Album, entries.first().kind)
        assertEquals("al-1", entries.first().refId)
    }

    @Test
    fun openedResultsBecomeRecentAndReopeningOneOpensItAndMovesItUp() {
        val sourceId =
            runBlocking {
                val id = sourceRepository.addSource("nav", "http://localhost/", "u", "p", false)
                db.libraryDao().upsertAlbums(listOf(album(id, "al-1", "Road Album")))
                db.libraryDao().upsertArtists(listOf(artist(id, "ar-1", "Road Artist")))
                id
            }

        viewModel.recordVisit(PinRef(sourceId, PinKind.Album, "al-1"))
        runBlocking { withTimeout(5_000) { viewModel.history.first { it.size == 1 } } }
        viewModel.recordVisit(PinRef(sourceId, PinKind.Artist, "ar-1"))
        val recent = runBlocking { withTimeout(5_000) { viewModel.history.first { it.size == 2 } } }
        assertEquals(listOf("Road Artist", "Road Album"), recent.map { it.name })

        var opened: MenuTarget? = null
        viewModel.openRecent(recent.last()) { opened = it }
        awaitUntil("the album opens") { opened != null }
        assertEquals("al-1", (opened as MenuTarget.Album).album.id)
        val reordered = runBlocking { withTimeout(5_000) { viewModel.history.first { it.first().refId == "al-1" } } }
        assertEquals(listOf("Road Album", "Road Artist"), reordered.map { it.name })

        viewModel.clearHistory()
        runBlocking { withTimeout(5_000) { viewModel.history.first { it.isEmpty() } } }
    }

    @Test
    fun offlineSearchAndHistoryFollowCompletedDownloadsAndModeChanges() =
        runBlocking {
            val id = sourceRepository.addSource("nav", "http://localhost/", "u", "p", false)
            db.libraryDao().upsertAlbums(listOf(album(id, "al-1", "Road Album")))
            db.libraryDao().upsertArtists(listOf(artist(id, "ar-1", "Road Artist")))
            db.libraryDao().upsertPlaylists(listOf(playlist(id, "pl-1", "Road Playlist")))
            db.libraryDao().upsertSongs(listOf(song(id, "s1", "Road Song")))
            db.libraryDao().upsertPlaylistSongs(
                listOf(
                    com.subtracks.data.model
                        .PlaylistSong(id, "pl-1", "s1", 0),
                ),
            )
            for ((kind, ref) in listOf(PinKind.Album to "al-1", PinKind.Artist to "ar-1", PinKind.Playlist to "pl-1")) {
                fixture.libraryRepository.recordSearchVisit(PinRef(id, kind, ref))
            }
            sourceRepository.setOfflineMode(true)
            withTimeout(5_000) { sourceRepository.offline.first { it } }
            val library = fixture.libraryRepository
            assertTrue(library.searchAlbums(id, "road").first().isEmpty())
            assertTrue(library.searchArtists(id, "road").first().isEmpty())
            assertTrue(library.searchPlaylists(id, "road").first().isEmpty())
            assertTrue(library.searchSongs(id, "road").first().isEmpty())
            assertTrue(library.searchHistory(id).first().isEmpty())
            db.downloadDao().upsert(
                com.subtracks.data.model
                    .SongDownload(id, "s1", com.subtracks.data.model.DownloadStatus.Queued),
            )
            assertTrue(library.searchSongs(id, "road").first().isEmpty())
            db.downloadDao().upsert(
                com.subtracks.data.model
                    .SongDownload(id, "s1", com.subtracks.data.model.DownloadStatus.Completed),
            )
            assertEquals(1, library.searchAlbums(id, "road").first().size)
            assertEquals(1, library.searchArtists(id, "road").first().size)
            assertEquals(1, library.searchPlaylists(id, "road").first().size)
            assertEquals(1, library.searchSongs(id, "road").first().size)
            assertEquals(3, library.searchHistory(id).first().size)
            db.downloadDao().upsert(
                com.subtracks.data.model
                    .SongDownload(id, "s1", com.subtracks.data.model.DownloadStatus.Failed),
            )
            viewModel.setQuery("road")
            withTimeout(5_000) { viewModel.results.first { !it.loading && it.isEmpty } }
            sourceRepository.setOfflineMode(false)
            withTimeout(5_000) { viewModel.results.first { it.songs.size == 1 } }
            assertEquals(3, library.searchHistory(id).first().size)
        }

    private fun album(
        sourceId: Long,
        id: String,
        name: String,
    ) = Album(
        sourceId = sourceId,
        id = id,
        artistId = "ar-1",
        name = name,
        albumArtist = "Artist",
        created = 0,
        coverArt = null,
        genre = null,
        year = null,
        starred = null,
        songCount = 1,
    )

    private fun artist(
        sourceId: Long,
        id: String,
        name: String,
    ) = Artist(
        sourceId = sourceId,
        id = id,
        name = name,
        albumCount = 1,
        starred = null,
        coverArt = null,
    )

    private fun playlist(
        sourceId: Long,
        id: String,
        name: String,
    ) = Playlist(
        sourceId = sourceId,
        id = id,
        name = name,
        comment = null,
        coverArt = null,
        songCount = 1,
        created = 0,
        changed = 0,
        duration = 100,
    )

    private fun song(
        sourceId: Long,
        id: String,
        title: String,
    ) = Song(
        sourceId = sourceId,
        id = id,
        albumId = "al-1",
        artistId = "ar-1",
        title = title,
        album = "Album",
        artist = "Artist",
        duration = 100,
        track = 1,
        disc = 1,
        starred = null,
    )
}
