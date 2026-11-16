package com.subtracks.data.mix

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueBuilderTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var builder: QueueBuilder
    private var sourceId = 0L

    @Before
    fun setUp() {
        db = createTestDatabase()
        builder = QueueBuilder(db.mixDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun spellingsThatDifferInCaseOrSpaceAreOneGenreNamedByTheMostUsed() =
        runTest {
            seed(
                "a" to listOf("Techno"),
                "b" to listOf("techno "),
                "c" to listOf("Techno"),
                "d" to listOf("Jazz"),
            )

            val index = builder.index(sourceId, downloadedOnly = false)

            assertEquals(listOf(QueueBuilderGenre("techno", "Techno", 3), QueueBuilderGenre("jazz", "Jazz", 1)), index.genres)
            assertEquals(3L, builder.matchCount(sourceId, QueueBuilderSelection(genres = setOf("techno")), false))
        }

    @Test
    fun aSongWithTwoGenresMatchesEitherAndCountsOnceWhenBothArePicked() =
        runTest {
            seed("a" to listOf("Jazz", "Funk"), "b" to listOf("Funk"), "c" to listOf("House"))

            assertEquals(1L, builder.matchCount(sourceId, QueueBuilderSelection(genres = setOf("jazz")), false))
            assertEquals(2L, builder.matchCount(sourceId, QueueBuilderSelection(genres = setOf("funk")), false))
            assertEquals(2L, builder.matchCount(sourceId, QueueBuilderSelection(genres = setOf("jazz", "funk")), false))
            assertEquals(
                setOf("a", "b"),
                builder.build(sourceId, QueueBuilderSelection(genres = setOf("jazz", "funk")), 10, false).toSet(),
            )
        }

    @Test
    fun decadesFilterByAlbumYearAndCombineWithGenres() =
        runTest {
            seed(
                "a" to listOf("Jazz"),
                "b" to listOf("Jazz"),
                "c" to listOf("Techno"),
                "d" to listOf("Jazz"),
                years = mapOf("a" to 1959L, "b" to 1972L, "c" to 1992L, "d" to null),
            )

            assertEquals(mapOf(1950 to 1L, 1970 to 1L, 1990 to 1L), builder.index(sourceId, false).decades)
            assertEquals(
                setOf("a", "c"),
                builder.build(sourceId, QueueBuilderSelection(decades = setOf(1950, 1990)), 10, false).toSet(),
            )
            assertEquals(
                listOf("b"),
                builder.build(sourceId, QueueBuilderSelection(genres = setOf("jazz"), decades = setOf(1970)), 10, false),
            )
        }

    @Test
    fun anEmptySelectionMatchesEverythingIncludingUntaggedSongs() =
        runTest {
            seed("a" to listOf("Jazz"), "b" to emptyList())

            assertEquals(2L, builder.matchCount(sourceId, QueueBuilderSelection(), false))
        }

    @Test
    fun aGenreNoLongerInTheLibraryMatchesNothing() =
        runTest {
            seed("a" to listOf("Jazz"))

            assertEquals(0L, builder.matchCount(sourceId, QueueBuilderSelection(genres = setOf("polka")), false))
            assertTrue(builder.build(sourceId, QueueBuilderSelection(genres = setOf("polka")), 10, false).isEmpty())
        }

    @Test
    fun aBuildRespectsTheCountAndSkipsQueuedSongs() =
        runTest {
            seed(*Array(7) { "s$it" to listOf("Techno") })
            val techno = QueueBuilderSelection(genres = setOf("techno"))

            assertEquals(3, builder.build(sourceId, techno, 3, false).toSet().size)
            // Seven songs, two already queued: asking for more than are left gives exactly the other five.
            val queue = builder.build(sourceId, techno, 6, false, excluding = listOf("s1", "s2"))
            assertEquals(listOf("s0", "s3", "s4", "s5", "s6"), queue.sorted())
        }

    @Test
    fun offlineCountsAndBuildsUseOnlyFinishedDownloads() =
        runTest {
            seed(
                "a" to listOf("Jazz"),
                "b" to listOf("Jazz"),
                "c" to listOf("Techno"),
                years = mapOf("a" to 1965L, "b" to 1975L, "c" to 1995L),
            )
            db.downloadDao().upsert(SongDownload(sourceId, "a", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(sourceId, "c", DownloadStatus.Running))

            val index = builder.index(sourceId, downloadedOnly = true)

            assertEquals(listOf(QueueBuilderGenre("jazz", "Jazz", 1)), index.genres)
            assertEquals(mapOf(1960 to 1L), index.decades)
            assertEquals(listOf("a"), builder.build(sourceId, QueueBuilderSelection(), 10, downloadedOnly = true))
        }

    private suspend fun seed(
        vararg songs: Pair<String, List<String>>,
        years: Map<String, Long?> = emptyMap(),
    ) {
        sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
        val dao = db.libraryDao()
        dao.upsertAlbums(
            songs.map { (id, _) ->
                Album(sourceId, "al-$id", null, "Album $id", null, 0, null, null, years[id], starred = null, songCount = 1)
            },
        )
        dao.upsertSongs(
            songs.map { (id, _) ->
                Song(sourceId, id, "al-$id", null, id, "Album $id", null, 200, 1, 1, null)
            },
        )
        db.useWriterConnection { connection ->
            songs.forEach { (id, genres) ->
                genres.forEachIndexed { position, genre ->
                    connection.usePrepared(
                        "INSERT INTO song_genres (sourceId, songId, position, genre) VALUES (?, ?, ?, ?)",
                    ) {
                        it.bindLong(1, sourceId)
                        it.bindText(2, id)
                        it.bindLong(3, position.toLong())
                        it.bindText(4, genre)
                        it.step()
                    }
                }
            }
        }
    }
}
