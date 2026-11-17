package com.subtracks.data.mix

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.audiomuse.AudioMuseClient
import com.subtracks.data.audiomuse.AudioMuseConfiguration
import com.subtracks.data.audiomuse.AudioMuseMoodCache
import com.subtracks.data.audiomuse.AudioMuseMoodScores
import com.subtracks.data.audiomuse.AudioMusePathEnd
import com.subtracks.data.audiomuse.AudioMuseScoreAccess
import com.subtracks.data.audiomuse.AudioMuseScoreBatch
import com.subtracks.data.audiomuse.AudioMuseScoreFetcher
import com.subtracks.data.audiomuse.AudioMuseTrack
import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class QueueBuilderMoodBuilderTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var builder: QueueBuilder
    private val scored = mutableListOf<List<String>>()
    private var batch: (List<String>) -> AudioMuseScoreBatch = { AudioMuseScoreBatch() }

    // Jazz songs j0..j9 (1990s, happiness rising with the number), one Techno song t (1990s, the happiest of all)
    // and one Jazz song from the 1960s, old.
    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            builder = QueueBuilder(db.mixDao())
            db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
            val songs = (0 until 10).map { Triple("j$it", "Jazz", 1994L) } + Triple("t", "Techno", 1995L) + Triple("old", "Jazz", 1965L)
            db.libraryDao().upsertAlbums(
                songs.map { (id, _, year) ->
                    Album(1, "al-$id", null, "Album $id", null, 0, null, null, year, null, 1)
                },
            )
            db.libraryDao().upsertSongs(songs.map { (id, _, _) -> Song(1, id, "al-$id", null, id, "Album $id", null, 200, 1, 1, null) })
            db.useWriterConnection { connection ->
                songs.forEach { (id, genre, _) ->
                    connection.usePrepared("INSERT INTO song_genres (sourceId, songId, position, genre) VALUES (1, ?, 0, ?)") {
                        it.bindText(1, id)
                        it.bindText(2, genre)
                        it.step()
                    }
                }
            }
        }

    @After fun tearDown() = db.close()

    private fun happy(value: Double) = AudioMuseMoodScores(mapOf("happy" to value, "relaxed" to 0.5), emptyList())

    private fun happiness(id: String) =
        when (id) {
            "t" -> 0.99
            "old" -> 0.98
            else -> id.removePrefix("j").toDouble() / 10
        }

    private fun moodBuilder() =
        QueueBuilderMoodBuilder(builder, { _, ids ->
            scored += ids
            batch(ids)
        }, Random(3))

    private val jazz90sHappy = QueueBuilderSelection(mains = setOf("Jazz"), decades = setOf(1990), moods = setOf(QueueBuilderMood.HAPPY))

    @Test fun withoutMoodsNothingIsScored() =
        runBlocking {
            val result = moodBuilder().build(1, jazz90sHappy.copy(moods = emptySet()), 4, false)
            assertEquals(4, result.songIds.size)
            assertNull(result.moodShortfall)
            assertTrue(scored.isEmpty())
        }

    @Test fun moodsRankOnlyTheGenreAndDecadeMatchesAndKeepTheBestShuffled() =
        runBlocking {
            batch = { ids -> AudioMuseScoreBatch(scores = ids.associateWith { happy(happiness(it)) }) }
            val result = moodBuilder().build(1, jazz90sHappy, 3, false, excluding = listOf("j9"))
            // Techno and the 1960s song score higher but do not match the genre and decade picks; j9 is excluded.
            assertEquals(setOf("j8", "j7", "j6"), result.songIds.toSet())
            assertNull(result.moodShortfall)
            assertEquals(setOf("j0", "j1", "j2", "j3", "j4", "j5", "j6", "j7", "j8"), scored.single().toSet())
            val orders =
                (1..20).map {
                    QueueBuilderMoodBuilder(
                        builder,
                        { _, ids -> batch(ids) },
                        Random(it),
                    ).build(1, jazz90sHappy, 3, false).songIds
                }
            assertTrue("the kept songs are shuffled, not always best first", orders.toSet().size > 1)
        }

    @Test fun moodsThatCannotBeHonouredSayWhyInsteadOfBeingIgnored() =
        runBlocking {
            batch = { ids -> AudioMuseScoreBatch(unavailable = ids.toSet()) }
            assertEquals(QueueBuilderBuild(emptyList(), MoodShortfall.SCORES_UNAVAILABLE), moodBuilder().build(1, jazz90sHappy, 3, false))
            batch = { ids -> AudioMuseScoreBatch(failed = ids.toSet()) }
            assertEquals(MoodShortfall.SCORES_UNAVAILABLE, moodBuilder().build(1, jazz90sHappy, 3, false).moodShortfall)
            batch = { ids ->
                AudioMuseScoreBatch(
                    scores = ids.associateWith { AudioMuseMoodScores(mapOf("sad" to 0.9), emptyList()) },
                    notAnalysed = emptySet(),
                )
            }
            assertEquals(QueueBuilderBuild(emptyList(), MoodShortfall.NO_MOOD_MATCH), moodBuilder().build(1, jazz90sHappy, 3, false))
            batch = { ids ->
                AudioMuseScoreBatch(scores = mapOf(ids.first() to happy(0.5)), unavailable = ids.drop(1).toSet())
            }
            val partial = moodBuilder().build(1, jazz90sHappy, 3, false)
            assertEquals(1, partial.songIds.size)
            assertEquals(MoodShortfall.PARTIAL, partial.moodShortfall)
        }

    @Test fun refillUsesTheSavedPicksIncludingMoodsOnlyWhileAudioMuseIsUsable() =
        runBlocking {
            batch = { ids -> AudioMuseScoreBatch(scores = ids.associateWith { happy(happiness(it)) }) }
            var moodsOn = true
            val saved = QueueBuilderPicks(jazz90sHappy.copy(mains = jazz90sHappy.mains + "Retagged away"), 30)
            val refill = QueueBuilderMoodBuilder.Refill(moodBuilder(), { saved }, { moodsOn })
            assertEquals(setOf("j8", "j7"), refill(1, setOf("j9"), 2, false).toSet())
            assertEquals(1, scored.size)
            moodsOn = false
            val plain = refill(1, emptySet(), 20, false)
            // Without AudioMuse the genre and decade picks still apply, and nothing is scored.
            assertEquals((0 until 10).map { "j$it" }.toSet(), plain.toSet())
            assertEquals(1, scored.size)
        }

    @Test fun thePoolFollowsIosBounds() {
        assertEquals(120, QueueBuilderMoodBuilder.poolSize(15))
        assertEquals(200, QueueBuilderMoodBuilder.poolSize(50))
        assertEquals(400, QueueBuilderMoodBuilder.poolSize(120))
    }

    @Test fun offlineRanksDownloadedSongsByCachedScoresWithoutContactingAudioMuse() =
        runBlocking {
            val credentials = FakeCredentialStore()
            AudioMuseConfiguration(db.audioMuseConfigDao(), credentials).save(1, "https://muse", "secret")
            val cache = AudioMuseMoodCache(db.audioMuseScoreDao())
            val scope = cache.scope(1)!!
            for (id in listOf("j2", "j5", "j7")) cache.store(scope, id, happy(happiness(id)))
            for (id in listOf("j2", "j3", "j5")) db.downloadDao().upsert(SongDownload(1, id, DownloadStatus.Completed))
            var clients = 0
            val fetcher =
                AudioMuseScoreFetcher(cache, credentials, MutableStateFlow(AudioMuseScoreAccess(1, offline = true))) {
                    clients++
                    unusedClient
                }
            val result = QueueBuilderMoodBuilder(builder, fetcher::fetch, Random(1)).build(1, jazz90sHappy, 5, downloadedOnly = true)
            // j7 has a score but is not downloaded; j3 is downloaded but was never scored.
            assertEquals(setOf("j2", "j5"), result.songIds.toSet())
            assertEquals(MoodShortfall.PARTIAL, result.moodShortfall)
            assertEquals(0, clients)
        }

    private val unusedClient =
        object : AudioMuseClient {
            override suspend fun moodScores(itemId: String) = error("Unexpected")

            override suspend fun warmup() = error("Unexpected")

            override suspend fun search(
                query: String,
                limit: Int,
            ): List<AudioMuseTrack> = error("Unexpected")

            override suspend fun findPath(
                start: AudioMusePathEnd,
                end: AudioMusePathEnd,
                length: Int,
            ): List<AudioMuseTrack> = error("Unexpected")
        }
}
