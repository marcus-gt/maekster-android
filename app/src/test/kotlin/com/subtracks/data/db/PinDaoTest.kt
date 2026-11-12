package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
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

@RunWith(AndroidJUnit4::class)
class PinDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before fun setUp() {
        db = createTestDatabase()
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun source() =
        db.sourcesDao().insertSource(
            Source(name = "Server", address = "http://localhost", createdAt = 0),
        )

    @Test
    fun pinsAreServerScopedTypedOrderedAndBounded() =
        runTest {
            val first = source()
            val second = source()
            val dao = db.pinDao()
            dao.pin(first, PinKind.Album, "same")
            dao.pin(first, PinKind.Playlist, "same")
            dao.pin(first, PinKind.Artist, "same")
            dao.pin(second, PinKind.Album, "same")
            assertEquals(listOf(PinKind.Album, PinKind.Playlist), dao.pins(first, 2).first().map { it.kind })
            assertEquals(listOf(0L, 1L, 2L), dao.pins(first, 10).first().map { it.position })
            assertEquals(1, dao.pins(second, 10).first().size)
            assertEquals(
                0L,
                dao
                    .pins(second, 10)
                    .first()
                    .single()
                    .position,
            )
            assertTrue(dao.isPinned(first, PinKind.Artist, "same").first())
            assertFalse(dao.isPinned(second, PinKind.Artist, "same").first())
        }

    @Test
    fun duplicatePinIsNoOpAndRepinAppendsAfterRemainingPins() =
        runTest {
            val source = source()
            val dao = db.pinDao()
            dao.pin(source, PinKind.Album, "a")
            dao.pin(source, PinKind.Album, "b")
            dao.pin(source, PinKind.Album, "a")
            assertEquals(listOf("a", "b"), dao.pins(source, 10).first().map { it.refId })
            assertEquals(listOf(0L, 1L), dao.pins(source, 10).first().map { it.position })
            dao.unpin(source, PinKind.Album, "a")
            dao.unpin(source, PinKind.Album, "missing")
            assertFalse(dao.isPinned(source, PinKind.Album, "a").first())
            dao.pin(source, PinKind.Album, "a")
            assertEquals(listOf("b", "a"), dao.pins(source, 10).first().map { it.refId })
            assertEquals(listOf(1L, 2L), dao.pins(source, 10).first().map { it.position })
        }

    @Test
    fun resolvedPinsPageLocalMetadataAndRetainMissingReferences() =
        runTest {
            val first = source()
            val second = source()
            val dao = db.pinDao()
            val library = db.libraryDao()
            library.upsertAlbums(
                listOf(
                    com.subtracks.data.model
                        .Album(first, "same", null, "Album", "Artist", 0, "art", null, null, null, 1),
                    com.subtracks.data.model
                        .Album(second, "same", null, "Wrong server", null, 0, null, null, null, null, 1),
                ),
            )
            library.upsertPlaylists(
                listOf(
                    com.subtracks.data.model.Playlist(
                        sourceId = first,
                        id = "same",
                        name = "Playlist",
                        comment = null,
                        coverArt = "plart",
                        songCount = 1,
                        created = 0,
                    ),
                ),
            )
            library.upsertArtists(
                listOf(
                    com.subtracks.data.model.Artist(
                        sourceId = first,
                        id = "same",
                        name = "Artist",
                        albumCount = 1,
                        starred = null,
                        coverArt = "arart",
                    ),
                ),
            )
            dao.pin(first, PinKind.Album, "missing")
            PinKind.entries.forEach { dao.pin(first, it, "same") }

            suspend fun page(): List<com.subtracks.data.model.PinnedItem> {
                val pagingSource = dao.resolvedPins(first)
                val result =
                    pagingSource.load(
                        androidx.paging.PagingSource.LoadParams.Refresh(
                            key = null,
                            loadSize = 2,
                            placeholdersEnabled = false,
                        ),
                    )
                pagingSource.invalidate()
                return (result as androidx.paging.PagingSource.LoadResult.Page).data
            }
            assertEquals(listOf("Album", "Playlist"), page().map { it.name })
            assertEquals(listOf("art", "plart"), page().map { it.coverArt })
            assertEquals(4, dao.pins(first, 10).first().size)
            library.upsertAlbums(
                listOf(
                    com.subtracks.data.model.Album(
                        first,
                        "missing",
                        null,
                        "Returned",
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        1,
                    ),
                ),
            )
            assertEquals(listOf("Returned", "Album"), page().map { it.name })
        }

    @Test
    fun downloadedPinsKeepOnlyPlayableItemsInPinOrder() =
        runTest {
            val source = source()
            val other = source()
            val library = db.libraryDao()
            library.upsertAlbums(
                listOf(
                    Album(source, "a1", "ar1", "Downloaded album", "One", 0, null, null, null, null, 1),
                    Album(source, "a2", "ar2", "Queued album", "Two", 0, null, null, null, null, 1),
                    Album(other, "a2", "ar2", "Other server", "Two", 0, null, null, null, null, 1),
                ),
            )
            library.upsertArtists(listOf(Artist(source, "ar1", "One", 1, null, null), Artist(source, "ar2", "Two", 1, null, null)))
            library.upsertSongs(
                listOf(
                    Song(source, "s1", "a1", "ar1", "Song 1", "Downloaded album", "One", 1, 1, 1, null),
                    Song(source, "s2", "a2", "ar2", "Song 2", "Queued album", "Two", 1, 1, 1, null),
                    Song(other, "s2", "a2", "ar2", "Song 2", "Other server", "Two", 1, 1, 1, null),
                ),
            )
            library.upsertPlaylists(
                listOf(Playlist(source, "p1", "Has download", null, null, 1, 0), Playlist(source, "p2", "Queued only", null, null, 1, 0)),
            )
            library.upsertPlaylistSongs(listOf(PlaylistSong(source, "p1", "s1", 0), PlaylistSong(source, "p2", "s2", 0)))
            db.downloadDao().upsert(SongDownload(source, "s1", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(source, "s2", DownloadStatus.Queued))
            db.downloadDao().upsert(SongDownload(other, "s2", DownloadStatus.Completed))
            val dao = db.pinDao()
            dao.pin(source, PinKind.Playlist, "p1")
            dao.pin(source, PinKind.Album, "a2")
            dao.pin(source, PinKind.Artist, "ar1")
            dao.pin(source, PinKind.Album, "a1")
            dao.pin(source, PinKind.Playlist, "p2")
            dao.pin(source, PinKind.Artist, "ar2")

            suspend fun downloaded(): List<String> {
                val pagingSource = dao.downloadedPins(source)
                val result =
                    pagingSource.load(
                        androidx.paging.PagingSource.LoadParams
                            .Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
                    )
                pagingSource.invalidate()
                return (result as androidx.paging.PagingSource.LoadResult.Page).data.map { it.name }
            }
            assertEquals(listOf("Has download", "One", "Downloaded album"), downloaded())
            db.downloadDao().upsert(SongDownload(source, "s2", DownloadStatus.Completed))
            assertEquals(listOf("Has download", "Queued album", "One", "Downloaded album", "Queued only", "Two"), downloaded())
        }

    @Test
    fun deletingServerCascadesOnlyItsPins() =
        runTest {
            val first = source()
            val second = source()
            val dao = db.pinDao()
            PinKind.entries.forEach { dao.pin(first, it, "same") }
            dao.pin(second, PinKind.Album, "same")
            db.sourcesDao().deleteSource(first)
            assertTrue(dao.pins(first, 10).first().isEmpty())
            assertEquals(1, dao.pins(second, 10).first().size)
        }
}
