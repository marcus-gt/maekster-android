package com.subtracks.data.mix

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.MixCandidate
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class SmartShuffleTest {
    private lateinit var db: SubtracksDatabase
    private var sourceId = 0L

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun candidates(
        prefix: String,
        count: Int,
        artist: (Int) -> String = { "$prefix-artist-$it" },
    ) = List(count) { MixCandidate("$prefix$it", artist(it)) }

    // --- blend (pure) ---

    @Test
    fun theBlendDealsFromEachSourceInTurn() {
        val mix =
            SmartShuffle.blend(
                sources = listOf(candidates("f", 3), candidates("r", 3), candidates("d", 3)),
                excludedIds = emptySet(),
                targetSize = 6,
                maxPerArtist = 3,
                random = Random(1),
            )

        assertEquals(listOf("f", "r", "d", "f", "r", "d"), mix.map { it.id.take(1) })
    }

    @Test
    fun theBlendCapsEachArtistSkipsRecentPlaysAndHandsDryTurnsOn() {
        val oneArtist = candidates("f", 10) { "same" }
        val mix =
            SmartShuffle.blend(
                sources = listOf(oneArtist, emptyList(), candidates("d", 10)),
                excludedIds = setOf("d0", "d1"),
                targetSize = 10,
                maxPerArtist = 3,
                random = Random(2),
            )

        assertEquals(3, mix.count { it.artistId == "same" })
        assertFalse(mix.any { it.id == "d0" || it.id == "d1" })
        assertEquals(10, mix.size)
        assertEquals(mix.size, mix.map { it.id }.toSet().size)
    }

    @Test
    fun aTinyLibraryOfOnlyRecentPlaysStillPlays() {
        val mix =
            SmartShuffle.blend(
                sources = listOf(emptyList(), emptyList(), candidates("d", 2)),
                excludedIds = setOf("d0", "d1"),
                targetSize = 50,
                maxPerArtist = 3,
                random = Random(3),
            )

        assertEquals(setOf("d0", "d1"), mix.map { it.id }.toSet())
    }

    // --- build (mirror) ---

    @Test
    fun theMixDrawsOnFavouritesRecentTasteAndTheRest() =
        runTest {
            seed()

            val mix = SmartShuffle(db.mixDao(), Random(4)).build(sourceId, downloadedOnly = false, targetSize = 6)

            assertEquals(6, mix.size)
            assertTrue("a favourite is in the mix", mix.any { it in setOf("starred", "fav-album-1", "fav-artist-1") })
            assertTrue("recent taste is in the mix", mix.any { it in setOf("taste-1", "taste-2", "genre-1") })
            assertFalse("the song just played is left out", "played" in mix)
        }

    @Test
    fun offlineTheMixUsesOnlyFinishedDownloads() =
        runTest {
            seed()
            db.downloadDao().upsert(SongDownload(sourceId, "random-1", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(sourceId, "starred", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(sourceId, "random-2", DownloadStatus.Running))

            val mix = SmartShuffle(db.mixDao(), Random(5)).build(sourceId, downloadedOnly = true)

            assertEquals(setOf("random-1", "starred"), mix.toSet())
        }

    @Test
    fun anEmptyLibraryGivesAnEmptyMix() =
        runTest {
            sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))

            assertTrue(SmartShuffle(db.mixDao()).build(sourceId, downloadedOnly = false).isEmpty())
        }

    private suspend fun seed() {
        sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
        val dao = db.libraryDao()
        dao.upsertArtists(
            listOf(
                Artist(sourceId, "fav-artist", "Fav", albumCount = 1, starred = 1),
                Artist(sourceId, "taste", "Taste", albumCount = 1, starred = null),
            ),
        )
        dao.upsertAlbums(
            listOf(
                Album(sourceId, "fav-album", "other", "Fav Album", null, 0, null, null, null, starred = 1, songCount = 1),
            ),
        )
        dao.upsertSongs(
            listOf(
                song("starred", artistId = "a1", starred = 1),
                song("fav-album-1", albumId = "fav-album", artistId = "a2"),
                song("fav-artist-1", artistId = "fav-artist"),
                song("played", artistId = "taste"),
                song("taste-1", artistId = "taste"),
                song("taste-2", artistId = "taste"),
                song("genre-1", artistId = "a3"),
                song("random-1", artistId = "a4"),
                song("random-2", artistId = "a5"),
                song("random-3", artistId = "a6"),
            ),
        )
        db.useWriterConnection { connection ->
            listOf("played", "genre-1").forEach { songId ->
                connection.usePrepared(
                    "INSERT INTO song_genres (sourceId, songId, position, genre) VALUES ($sourceId, '$songId', 0, 'Jazz')",
                ) { it.step() }
            }
        }
        // The last play seeds recent taste (artist "taste", genre "Jazz") and is itself left out.
        dao.recordPlay(sourceId, "played", 1_000)
    }

    private fun song(
        id: String,
        albumId: String = "al-$id",
        artistId: String,
        starred: Long? = null,
    ) = Song(
        sourceId = sourceId,
        id = id,
        albumId = albumId,
        artistId = artistId,
        title = id,
        album = "Album",
        artist = artistId,
        duration = 200,
        track = 1,
        disc = 1,
        starred = starred,
    )
}
