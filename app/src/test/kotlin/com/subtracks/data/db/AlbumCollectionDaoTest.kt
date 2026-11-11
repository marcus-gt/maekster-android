package com.subtracks.data.db

import androidx.paging.PagingSource
import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.prefs.CollectionSort
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
class AlbumCollectionDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun groupsDistinctAlbumsAcrossAllTagsAndLabelsWithoutCrossingSources() =
        runTest {
            val source = seed()
            seed()
            for (kind in AlbumCollectionKind.entries) {
                val rows = collections(source, kind)
                assertEquals(listOf("indie", "rock"), rows.map { it.name.lowercase() })
                assertEquals(listOf(1L, 3L), rows.map { it.albumCount })
                assertEquals(listOf("rock", "indie"), collections(source, kind, descending = true).map { it.name.lowercase() })
                assertEquals(listOf(3L, 1L), collections(source, kind, sort = CollectionSort.AlbumCount).map { it.albumCount })
                assertEquals(
                    listOf(1L, 3L),
                    collections(source, kind, sort = CollectionSort.AlbumCount, descending = true).map { it.albumCount },
                )
                assertEquals(listOf(3L), collections(source, kind, search = "OC").map { it.albumCount })
                assertTrue(collections(source, kind, search = "missing").isEmpty())
                assertEquals(listOf("new", "old", "unknown"), albums(source, kind, "rOcK"))
                assertTrue(albums(source, kind, "missing").isEmpty())
            }
        }

    @Test
    fun offlineCollectionsRequireCompletedDownloadsOfMatchingSongs() =
        runTest {
            val source = seed()
            val other = seed()
            db.downloadDao().upsert(SongDownload(other, "new", DownloadStatus.Completed))
            for (kind in AlbumCollectionKind.entries) {
                assertTrue(collections(source, kind, downloaded = true).isEmpty())
                assertFalse(db.libraryDao().hasDownloadedCollections(hasDownloadedCollectionsQuery(source, kind)).first())
            }
            db.downloadDao().upsert(SongDownload(source, "new", DownloadStatus.Queued))
            assertTrue(collections(source, AlbumCollectionKind.Genre, downloaded = true).isEmpty())
            db.downloadDao().upsert(SongDownload(source, "new", DownloadStatus.Completed))
            for (kind in AlbumCollectionKind.entries) {
                assertEquals(
                    if (kind ==
                        AlbumCollectionKind.Label
                    ) {
                        listOf(1L, 1L)
                    } else {
                        listOf(1L)
                    },
                    collections(source, kind, downloaded = true).map { it.albumCount },
                )
                assertEquals(listOf("new"), albums(source, kind, "rock", downloaded = true))
                assertTrue(db.libraryDao().hasDownloadedCollections(hasDownloadedCollectionsQuery(source, kind)).first())
            }
            // Indie belongs only to another song on the downloaded album, but labels apply to the whole album.
            assertTrue(albums(source, AlbumCollectionKind.Genre, "indie", downloaded = true).isEmpty())
            assertEquals(listOf("new"), albums(source, AlbumCollectionKind.Label, "indie", downloaded = true))
        }

    private suspend fun seed(): Long {
        val source = db.sourcesDao().insertSource(Source(name = "test", address = "http://localhost", isActive = false, createdAt = 0))
        db.libraryDao().upsertAlbums(
            listOf("new" to 2024L, "old" to 2000L, "unknown" to null).map { (id, year) ->
                Album(
                    sourceId = source,
                    id = id,
                    artistId = null,
                    name = id,
                    albumArtist = null,
                    created = 0,
                    coverArt = id,
                    genre = null,
                    year = year,
                    starred = null,
                    songCount = 2,
                )
            },
        )
        db.libraryDao().upsertSongs(
            listOf("new", "old", "unknown", "extra").map { id ->
                Song(
                    sourceId = source,
                    id = id,
                    albumId = if (id == "extra") "new" else id,
                    artistId = null,
                    title = id,
                    album = null,
                    artist = null,
                    duration = 1,
                    track = 1,
                    disc = 1,
                    starred = null,
                )
            },
        )
        db.useWriterConnection { connection ->
            listOf(
                "INSERT INTO song_genres VALUES ($source, 'new', 0, 'Rock')",
                "INSERT INTO song_genres VALUES ($source, 'old', 0, 'rock')",
                "INSERT INTO song_genres VALUES ($source, 'unknown', 0, 'Rock')",
                "INSERT INTO song_genres VALUES ($source, 'extra', 0, 'Rock')",
                "INSERT INTO song_genres VALUES ($source, 'extra', 1, 'Indie')",
                "INSERT INTO song_genres VALUES ($source, 'extra', 2, '   ')",
                "INSERT INTO album_labels VALUES ($source, 'new', 0, 'Rock')",
                "INSERT INTO album_labels VALUES ($source, 'new', 1, 'rock')",
                "INSERT INTO album_labels VALUES ($source, 'old', 0, 'rock')",
                "INSERT INTO album_labels VALUES ($source, 'unknown', 0, 'Rock')",
                "INSERT INTO album_labels VALUES ($source, 'new', 2, 'Indie')",
                "INSERT INTO album_labels VALUES ($source, 'old', 1, '   ')",
            ).forEach { sql -> connection.usePrepared(sql) { it.step() } }
        }
        return source
    }

    private suspend fun collections(
        source: Long,
        kind: AlbumCollectionKind,
        sort: CollectionSort = CollectionSort.Name,
        descending: Boolean = false,
        search: String = "",
        downloaded: Boolean = false,
    ) = db.libraryDao().albumCollectionsPaging(albumCollectionsPageQuery(source, kind, sort, descending, search, downloaded)).page()

    private suspend fun albums(
        source: Long,
        kind: AlbumCollectionKind,
        name: String,
        downloaded: Boolean = false,
    ) = db
        .libraryDao()
        .collectionAlbumsPaging(collectionAlbumsQuery(source, kind, name, downloaded))
        .page()
        .map { it.id }

    private suspend fun <T : Any> PagingSource<Int, T>.page(): List<T> =
        (
            load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page
        ).data
}
