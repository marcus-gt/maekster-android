package com.subtracks.data.db

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.data.prefs.ListQuery
import com.subtracks.data.prefs.SortNameMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LetterBucketsTest {
    @Test
    fun bucketsMatchFilteredPagePositionsInBothDirectionsAndNameModes() =
        runTest {
            val db = createTestDatabase()
            try {
                val source =
                    db.sourcesDao().insertSource(
                        Source(name = "test", address = "http://localhost", isActive = true, createdAt = 0),
                    )
                val dao = db.libraryDao()
                val names = listOf("123", "Alpha", "Beta", "Bravo", "éclair", "東京")
                dao.upsertAlbums(
                    names.mapIndexed { index, name ->
                        Album(
                            sourceId = source,
                            id = "$index",
                            artistId = "$index",
                            name = name,
                            albumArtist = name,
                            created = 0,
                            coverArt = null,
                            genre = null,
                            year = null,
                            starred = if (index % 2 == 0) 1L else null,
                            songCount = 1,
                            sortName = if (name == "東京") "Tokyo" else name,
                        )
                    },
                )
                dao.upsertArtists(
                    names.mapIndexed { index, name ->
                        Artist(
                            sourceId = source,
                            id = "$index",
                            name = name,
                            albumCount = 1,
                            coverArt = null,
                            starred = if (index % 2 == 0) 1L else null,
                            sortName = if (name == "東京") "Tokyo" else name,
                        )
                    },
                )
                dao.upsertSongs(
                    names.mapIndexed { index, name ->
                        Song(
                            sourceId = source,
                            id = "$index",
                            title = name,
                            albumId = "$index",
                            artistId = "$index",
                            album = name,
                            artist = name,
                            duration = 1,
                            track = 1,
                            disc = 1,
                            starred = if (index % 2 == 0) 1L else null,
                            sortName = if (name == "東京") "Tokyo" else name,
                        )
                    },
                )
                db.downloadDao().upsert(SongDownload(source, "2", DownloadStatus.Completed))
                for (tab in listOf(LibraryListTab.Albums, LibraryListTab.Artists, LibraryListTab.Songs)) {
                    for (mode in SortNameMode.entries) {
                        for (reverse in listOf(false, true)) {
                            val q = ListQuery("Name", descending = reverse)
                            val rows =
                                when (tab) {
                                    LibraryListTab.Albums -> {
                                        dao.albumPages(source, descending = reverse, sortNameMode = mode).page().map { it.id }
                                    }

                                    LibraryListTab.Artists -> {
                                        dao
                                            .artistPages(
                                                source,
                                                descending = reverse,
                                                sortNameMode = mode,
                                            ).page()
                                            .map { it.id }
                                    }

                                    else -> {
                                        dao
                                            .songsPaging(
                                                songsPageQuery(source, SongListSpec(descending = reverse, sortNameMode = mode)),
                                            ).page()
                                            .map { it.song.id }
                                    }
                                }
                            val buckets = dao.letterBuckets(letterBucketsQuery(source, tab, q, "", mode)).first()
                            assertEquals(if (mode == SortNameMode.Name) 4 else 5, buckets.size)
                            assertEquals(rows.indexOf("1"), buckets.first { it.letter == "A" }.position)
                            assertEquals(minOf(rows.indexOf("2"), rows.indexOf("3")), buckets.first { it.letter == "B" }.position)
                            assertEquals(rows.indexOf("4"), buckets.first { it.letter == "E" }.position)
                            assertEquals(
                                minOf(rows.indexOf("0"), if (mode == SortNameMode.Name) rows.indexOf("5") else Int.MAX_VALUE),
                                buckets.first { it.letter == "#" }.position,
                            )
                        }
                    }
                    assertEquals(
                        listOf(LetterBucket("B", 0)),
                        dao
                            .letterBuckets(
                                letterBucketsQuery(source, tab, ListQuery("Name", downloaded = true), "", SortNameMode.Name),
                            ).first(),
                    )
                    assertEquals(
                        listOf(LetterBucket("B", 0)),
                        dao.letterBuckets(letterBucketsQuery(source, tab, ListQuery("Name"), "Bravo", SortNameMode.Name)).first(),
                    )
                    assertTrue(
                        dao.letterBuckets(letterBucketsQuery(source + 1, tab, ListQuery("Name"), "", SortNameMode.Name)).first().isEmpty(),
                    )
                }
            } finally {
                db.close()
            }
        }

    private suspend fun <T : Any> PagingSource<Int, T>.page() =
        (load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = true)) as PagingSource.LoadResult.Page).data
}
