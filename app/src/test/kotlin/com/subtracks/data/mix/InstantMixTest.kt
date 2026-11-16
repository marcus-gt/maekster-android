package com.subtracks.data.mix

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.MixCandidate
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
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
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class InstantMixTest {
    private lateinit var db: SubtracksDatabase
    private var sourceId = 0L
    private var answer: () -> List<MixCandidate> = { emptyList() }
    private val asked = mutableListOf<MixSeed>()

    private fun mix() =
        InstantMix(
            dao = db.mixDao(),
            similar = { _, seed, _ ->
                asked += seed
                answer()
            },
            smartShuffle = SmartShuffle(db.mixDao(), Random(1)),
            now = { 1_000 },
            random = Random(2),
        )

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
            db.libraryDao().upsertSongs(
                listOf(
                    song("seed", "a-seed"),
                    song("x1", "a1"),
                    song("x2", "a1"),
                    song("x3", "a2"),
                    song("same-artist", "a-seed"),
                    song("same-genre", "a9"),
                    song("other", "a8"),
                ),
            )
            db.useWriterConnection { connection ->
                listOf("seed", "same-genre").forEach { id ->
                    connection.usePrepared(
                        "INSERT INTO song_genres (sourceId, songId, position, genre) VALUES ($sourceId, '$id', 0, 'Dub')",
                    ) {
                        it.step()
                    }
                }
            }
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun songSeedsKeepServerOrderAndOtherSeedsInterleaveArtists() {
        val songs = listOf(MixCandidate("1", "a"), MixCandidate("2", "a"), MixCandidate("3", "b"), MixCandidate("4", "a"))

        // Song seed: the server's order, with the third song by "a" dropped.
        assertEquals(listOf("1", "2", "3"), InstantMix.order(MixSeed.Song("s"), songs, maxPerArtist = 2).map { it.id })
        assertEquals(listOf("1", "3", "2"), InstantMix.order(MixSeed.Artist("a"), songs, maxPerArtist = 2).map { it.id })
    }

    @Test
    fun onlineTheServerAnswerPlaysAfterTheSeedAndIsCached() =
        runTest {
            answer =
                {
                    listOf(
                        MixCandidate("x1", "a1"),
                        MixCandidate("not-in-library", "a5"),
                        MixCandidate("seed", "a-seed"),
                        MixCandidate("x3", "a2"),
                    )
                }

            val result = mix().build(sourceId, MixSeed.Song("seed"), online = true)

            assertEquals(InstantMix.From.Server, result.from)
            assertEquals(listOf("seed", "x1", "x3"), result.songIds)
            assertEquals(listOf("x1", "seed", "x3"), db.mixDao().cachedSimilar(sourceId, "song", "seed").map { it.id })
        }

    @Test
    fun whenTheServerFailsTheCachedAnswerIsUsed() =
        runTest {
            answer = { listOf(MixCandidate("x1", "a1"), MixCandidate("x3", "a2")) }
            mix().build(sourceId, MixSeed.Artist("a-seed"), online = true)
            answer = { throw IOException("unreachable") }

            val result = mix().build(sourceId, MixSeed.Artist("a-seed"), online = true)

            assertEquals(InstantMix.From.Cache, result.from)
            assertEquals(listOf("x1", "x3"), result.songIds)
        }

    @Test
    fun offlineTheCacheIsLimitedToDownloadsAndTheServerIsNotAsked() =
        runTest {
            answer = { listOf(MixCandidate("x1", "a1"), MixCandidate("x2", "a1"), MixCandidate("x3", "a2")) }
            mix().build(sourceId, MixSeed.Song("seed"), online = true)
            asked.clear()
            db.downloadDao().upsert(SongDownload(sourceId, "x2", DownloadStatus.Completed))

            val result = mix().build(sourceId, MixSeed.Song("seed"), online = false)

            assertTrue(asked.isEmpty())
            assertEquals(listOf("x2"), result.songIds)
        }

    @Test
    fun withNothingSimilarTheSeedsArtistAndGenreComeFirstThenSmartShuffle() =
        runTest {
            answer = { emptyList() }

            val result = mix().build(sourceId, MixSeed.Song("seed"), online = true)

            assertEquals(InstantMix.From.Local, result.from)
            assertEquals("seed", result.songIds.first())
            assertEquals(
                setOf("same-artist", "same-genre"),
                result.songIds
                    .drop(1)
                    .take(2)
                    .toSet(),
            )
            assertTrue("topped up from the rest of the library", result.songIds.size > 3)
            assertFalse(result.songIds.drop(1).contains("seed"))
        }

    private fun song(
        id: String,
        artistId: String,
    ) = Song(sourceId, id, "al-$id", artistId, id, "Album", artistId, 200, 1, 1, null)
}
