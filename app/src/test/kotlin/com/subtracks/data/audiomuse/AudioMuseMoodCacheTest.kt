package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.CachedAudioMuseScore
import com.subtracks.data.model.Source
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioMuseMoodCacheTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var configuration: AudioMuseConfiguration
    private lateinit var cache: AudioMuseMoodCache
    private val scores = AudioMuseMoodScores(mapOf("happy" to 0.8, "party" to 0.6), listOf(AudioMuseStyle("jazz", 0.7)))

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            configuration = AudioMuseConfiguration(db.audioMuseConfigDao(), FakeCredentialStore())
            cache = AudioMuseMoodCache(db.audioMuseScoreDao())
            for (id in 1L..2L) {
                db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
                configuration.save(id, "https://muse")
            }
        }

    @After fun tearDown() = db.close()

    @Test fun persistsScoresBySourceAndReadsOnlyRequestedIdsWithoutAClient() =
        runBlocking {
            cache.store(cache.scope(1)!!, "song", scores)
            cache.store(cache.scope(1)!!, "other", AudioMuseMoodScores(emptyMap(), emptyList()))
            assertEquals(mapOf("song" to scores), AudioMuseMoodCache(db.audioMuseScoreDao()).read(1, listOf("song", "missing")))
            assertEquals(emptyMap<String, AudioMuseMoodScores>(), cache.read(2, listOf("song")))
            assertEquals(mapOf("other" to AudioMuseMoodScores(emptyMap(), emptyList())), cache.read(1, listOf("other")))
            assertEquals(emptyMap<String, AudioMuseMoodScores>(), cache.read(1, emptyList()))
        }

    @Test fun changedConfigurationHidesOldScoresAndRejectsLateWrites() =
        runBlocking {
            val old = cache.scope(1)!!
            cache.store(old, "song", scores)
            configuration.save(1, "https://different", "new-token")
            assertEquals(emptyMap<String, AudioMuseMoodScores>(), cache.read(1, listOf("song")))
            assertFalse(cache.store(old, "song", scores))
            assertTrue(cache.store(cache.scope(1)!!, "song", scores))
            configuration.save(1, "https://different", "replacement-token")
            assertEquals(emptyMap<String, AudioMuseMoodScores>(), cache.read(1, listOf("song")))
            assertEquals(emptyList<CachedAudioMuseScore>(), db.audioMuseScoreDao().readIfCurrent(old.config, old.key, listOf("song")))
        }

    @Test fun removingConfigurationOrSourceDeletesScoresAndInvalidatesScopes() =
        runBlocking {
            val first = cache.scope(1)!!
            val second = cache.scope(2)!!
            cache.store(first, "song", scores)
            cache.store(second, "song", scores)
            configuration.disconnect(1)
            assertNull(cache.scope(1))
            assertFalse(cache.store(first, "song", scores))
            assertTrue(db.audioMuseScoreDao().read(1, first.key, listOf("song")).isEmpty())
            assertEquals(mapOf("song" to scores), cache.read(2, listOf("song")))
            db.sourcesDao().deleteSource(2)
            assertTrue(db.audioMuseScoreDao().read(2, second.key, listOf("song")).isEmpty())
        }

    @Test fun malformedOrUnknownPayloadsAreMissesAndInvalidScoresAreNotStored() =
        runBlocking<Unit> {
            val scope = cache.scope(1)!!
            for (payload in listOf(
                "bad",
                "{}",
                """{"version":2,"values":{},"styles":[]}""",
                """{"version":1,"values":{"happy":"bad"},"styles":[]}""",
                """{"version":1,"values":{"happy":2},"styles":[]}""",
            )) {
                db.audioMuseScoreDao().put(CachedAudioMuseScore(1, "song", scope.key, payload))
                assertTrue(cache.read(1, listOf("song")).isEmpty())
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    cache.store(scope, "song", AudioMuseMoodScores(mapOf("happy" to Double.NaN), emptyList()))
                }
            }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { cache.read(1, List(501) { "$it" }) } }
        }

    @Test fun ranksByAllPickedMoodsWithStableTiesAndNoThreshold() {
        fun value(
            happy: Double,
            party: Double?,
        ) = AudioMuseMoodScores(
            buildMap {
                put("happy", happy)
                if (party != null) put("party", party)
            },
            emptyList(),
        )
        val all = listOf("low", "high", "tie", "partial", "missing")
        val map = mapOf("low" to value(0.2, 0.3), "high" to value(0.9, 0.7), "tie" to value(0.8, 0.8), "partial" to value(1.0, null))
        assertEquals(listOf("high", "tie", "low"), rankMoodCandidates(all, map, setOf(QueueBuilderMood.HAPPY, QueueBuilderMood.PARTY), 5))
        assertEquals(listOf("high", "tie"), rankMoodCandidates(all, map, setOf(QueueBuilderMood.HAPPY, QueueBuilderMood.PARTY), 2))
        assertEquals(listOf("low", "high"), rankMoodCandidates(all, emptyMap(), emptySet(), 2))
        assertTrue(rankMoodCandidates(all, map, setOf(QueueBuilderMood.HAPPY), 0).isEmpty())
    }
}
