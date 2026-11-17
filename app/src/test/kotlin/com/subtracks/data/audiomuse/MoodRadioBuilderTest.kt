package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class MoodRadioBuilderTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var configuration: AudioMuseConfiguration
    private val credentials = FakeCredentialStore()
    private val access = MutableStateFlow(AudioMuseScoreAccess(1, false))
    private val searches = mutableListOf<Pair<String, Int>>()
    private var warmups = 0
    private var factories = 0
    private var results: suspend () -> List<AudioMuseTrack> = { emptyList() }

    private val api =
        object : AudioMuseClient {
            override suspend fun moodScores(itemId: String): AudioMuseMoodScores = error("Unexpected scores")

            override suspend fun warmup(): Boolean {
                warmups++
                return true
            }

            override suspend fun search(
                query: String,
                limit: Int,
            ): List<AudioMuseTrack> {
                searches += query to limit
                return results()
            }

            override suspend fun findPath(
                start: AudioMusePathEnd,
                end: AudioMusePathEnd,
                length: Int,
            ): List<AudioMuseTrack> = error("Unexpected path")
        }

    private fun builder(random: Random = Random(7)) =
        MoodRadioBuilder(configuration, AudioMuseTrackResolver(db.audioMuseTrackDao(), access), access, {
            factories++
            assertEquals("https://muse/", it.url)
            assertEquals("audio-secret", it.token)
            api
        }, random)

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            configuration = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://music", id == 1L, id))
            configuration.save(1, "https://muse", "audio-secret")
        }

    @After fun tearDown() = db.close()

    private suspend fun seed(vararg songs: Song) = db.libraryDao().upsertSongs(songs.toList())

    private fun song(
        id: String,
        title: String,
        sourceId: Long = 1,
    ) = Song(sourceId, id, null, null, title, "Album", "Artist", null, null, null, null)

    @Test fun searchesTheMoodQueryAndPlaysDistinctLocalMatchesOnly() =
        runBlocking {
            seed(song("a", "Alpha"), song("b", "Beta"), song("other", "Other", sourceId = 2))
            results = {
                listOf(
                    AudioMuseTrack("b"),
                    AudioMuseTrack("fp_1", "alpha", "ARTIST", "album"),
                    AudioMuseTrack("missing", "Nowhere"),
                    AudioMuseTrack("other"),
                    AudioMuseTrack("b"),
                )
            }
            val result = builder().build(1, RadioMood.NIGHT)
            assertTrue(result is MoodRadioResult.Ready)
            assertEquals(setOf("a", "b"), (result as MoodRadioResult.Ready).songIds.toSet())
            assertEquals(2, result.songIds.size)
            assertEquals(listOf("late night calm atmospheric" to MoodRadioBuilder.POOL_SIZE), searches)
            assertEquals(1, warmups)
        }

    @Test fun anEmptyAnswerAndAnswersOutsideTheLibraryAreReportedDifferently() =
        runBlocking {
            assertEquals(MoodRadioResult.NoMatches, builder().build(1, RadioMood.CHILL))
            results = { listOf(AudioMuseTrack("missing", "Nowhere"), AudioMuseTrack("fp_2")) }
            assertEquals(MoodRadioResult.NotInLibrary, builder().build(1, RadioMood.CHILL))
        }

    @Test fun unusableAccessOrConfigurationNeverContactsAudioMuse() =
        runBlocking {
            seed(song("a", "Alpha"))
            results = { listOf(AudioMuseTrack("a")) }
            access.value = AudioMuseScoreAccess(1, offline = true)
            assertEquals(MoodRadioResult.Unavailable, builder().build(1, RadioMood.FOCUS))
            access.value = AudioMuseScoreAccess(2, false)
            assertEquals(MoodRadioResult.Unavailable, builder().build(1, RadioMood.FOCUS))
            assertEquals(MoodRadioResult.Unavailable, builder().build(2, RadioMood.FOCUS))
            access.value = AudioMuseScoreAccess(1, false)
            credentials.loseKey()
            assertEquals(MoodRadioResult.Unavailable, builder().build(1, RadioMood.FOCUS))
            assertEquals(0, factories)
            assertTrue(searches.isEmpty())
        }

    @Test fun searchFailuresPropagate() =
        runBlocking<Unit> {
            results = { throw IOException("down") }
            val failure = runCatching { builder().build(1, RadioMood.ENERGETIC) }.exceptionOrNull()
            assertTrue(failure is IOException)
        }

    @Test fun goingOfflineOrRemovingAudioMuseCancelsAnInFlightSearch() =
        runBlocking {
            seed(song("a", "Alpha"))
            for (change in listOf<suspend () -> Unit>(
                { access.value = AudioMuseScoreAccess(1, offline = true) },
                { configuration.disconnect(1) },
            )) {
                access.value = AudioMuseScoreAccess(1, false)
                if (configuration.connection(1) == null) configuration.save(1, "https://muse", "audio-secret")
                val searching = CompletableDeferred<Unit>()
                results = {
                    searching.complete(Unit)
                    CompletableDeferred<List<AudioMuseTrack>>().await()
                }
                val outcome =
                    coroutineScope {
                        val running = async { runCatching { builder().build(1, RadioMood.WORKOUT) } }
                        searching.await()
                        change()
                        withTimeout(5_000) { running.await() }
                    }
                assertTrue(outcome.exceptionOrNull() is CancellationException)
            }
        }

    @Test fun theShuffleIsFreshEachTimeButTheBestMatchesTendToLead() {
        val ranked = (0 until 10).toList()
        val random = Random(42)
        val firstCounts = IntArray(10)
        val orders = mutableSetOf<List<Int>>()
        repeat(2_000) {
            val order = MoodRadioBuilder.rankWeightedShuffle(ranked, random)
            assertEquals(ranked.toSet(), order.toSet())
            assertEquals(ranked.size, order.size)
            firstCounts[order.first()]++
            orders += order
        }
        assertTrue(orders.size > 100)
        assertTrue(firstCounts[0] > firstCounts[9] * 5)
        assertTrue(firstCounts[0] > firstCounts[4])
        assertNotEquals(0, firstCounts[9])
    }
}
