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

    @Test
    fun albumGenreChipsAreItsSongsTagsMostTaggedFirstAndEachOpensACollectionHoldingTheAlbum() =
        runTest {
            val source = seed()
            val other = seed()
            val dao = db.libraryDao()
            // "new" holds two Rock songs, one of them also Indie and one blank tag; blanks are skipped.
            assertEquals(listOf("Rock", "Indie"), dao.albumGenres(source, "new", false, 3).first())
            assertEquals(listOf("Rock"), dao.albumGenres(source, "new", false, 1).first())
            assertEquals(listOf("rock"), dao.albumGenres(other, "old", false, 3).first())
            for (genre in dao.albumGenres(source, "new", false, 3).first()) {
                assertTrue("$genre should list the album", "new" in albums(source, AlbumCollectionKind.Genre, genre))
            }
            assertEquals(listOf("Rock", "rock", "Indie"), dao.albumLabels(source, "new").first())
        }

    @Test
    fun offlineAlbumGenreChipsCountOnlyDownloadedSongs() =
        runTest {
            val source = seed()
            val dao = db.libraryDao()
            assertTrue(dao.albumGenres(source, "new", true, 3).first().isEmpty())
            db.downloadDao().upsert(SongDownload(source, "new", DownloadStatus.Completed))
            // Indie is only on the song that is not downloaded, and offline its collection would not hold the album.
            assertEquals(listOf("Rock"), dao.albumGenres(source, "new", true, 3).first())
            assertTrue(albums(source, AlbumCollectionKind.Genre, "indie", downloaded = true).isEmpty())
            db.downloadDao().upsert(SongDownload(source, "extra", DownloadStatus.Completed))
            assertEquals(listOf("Rock", "Indie"), dao.albumGenres(source, "new", true, 3).first())
        }

    @Test
    fun genreHierarchyAndQueueUseDistinctSourceScopedAlbums() =
        runTest {
            val source = seed()
            val other = seed()
            db.downloadDao().upsert(SongDownload(other, "extra", DownloadStatus.Completed))
            val dao = db.libraryDao()
            val mapping = genreMapping(dao.genreTags(source).first())
            val main =
                dao
                    .albumCollectionsPaging(
                        genreGroupsQuery(source, mapping, CollectionSort.Name, false, "Indie", false),
                    ).page()
                    .single()
            assertEquals("Rock", main.name)
            assertEquals(3L, main.albumCount)
            val style =
                dao
                    .albumCollectionsPaging(
                        genreGroupsQuery(source, mapping, CollectionSort.AlbumCount, false, "", false, main.name),
                    ).page()
                    .single()
            assertEquals("Indie Rock", style.name)
            assertEquals(1L, style.albumCount)
            val tags = mapping.filter { it.main == main.name && it.style == style.name }.map { it.tag }
            assertEquals(listOf("new"), dao.collectionAlbumsPaging(groupedGenreAlbumsQuery(source, tags, false)).page().map { it.id })
            assertTrue(dao.collectionAlbumsPaging(groupedGenreAlbumsQuery(source, tags, true)).page().isEmpty())

            val queue = db.queueDao()
            val spec = SongListSpec(albumGenreTags = tags)
            val online = spec.encode()
            assertEquals(2L, queue.libraryLength(source, online))
            assertEquals(listOf("extra", "new"), queue.librarySongs(source, online, 0, 2).map { it.song.id })
            val offline = spec.copy(downloaded = true).encode()
            assertEquals(0L, queue.libraryLength(source, offline))
            db.downloadDao().upsert(SongDownload(source, "new", DownloadStatus.Completed))
            assertEquals(listOf("new"), dao.collectionAlbumsPaging(groupedGenreAlbumsQuery(source, tags, true)).page().map { it.id })
            assertEquals(listOf("new"), queue.librarySongs(source, offline, 0, 2).map { it.song.id })

            val all = mapping.filter { it.main == main.name }.map { it.tag }
            assertEquals(
                listOf("new", "old", "unknown"),
                dao.collectionAlbumsPaging(groupedGenreAlbumsQuery(source, all, false)).page().map { it.id },
            )
            assertEquals(
                listOf("extra", "new", "old", "unknown"),
                queue.librarySongs(source, SongListSpec(albumGenreTags = all).encode(), 0, 4).map { it.song.id },
            )
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
