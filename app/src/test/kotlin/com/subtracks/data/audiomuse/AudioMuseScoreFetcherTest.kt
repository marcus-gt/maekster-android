package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AudioMuseScoreFetcherTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var cache: AudioMuseMoodCache
    private lateinit var configuration: AudioMuseConfiguration
    private val credentials = FakeCredentialStore()
    private val access = MutableStateFlow(AudioMuseScoreAccess(1, false))
    private val calls = mutableListOf<String>()
    private var factories = 0
    private val scores = AudioMuseMoodScores(mapOf("happy" to 0.8), emptyList())
    private var fetch: suspend (String) -> AudioMuseMoodScores = { scores }

    private val api =
        object : AudioMuseClient {
            override suspend fun moodScores(itemId: String): AudioMuseMoodScores {
                calls += itemId
                return fetch(itemId)
            }

            override suspend fun warmup(): Boolean = error("Unexpected warmup")

            override suspend fun search(
                query: String,
                limit: Int,
            ): List<AudioMuseTrack> = error("Unexpected search")

            override suspend fun findPath(
                start: AudioMusePathEnd,
                end: AudioMusePathEnd,
                length: Int,
            ): List<AudioMuseTrack> = error("Unexpected path")
        }

    private fun create() =
        AudioMuseScoreFetcher(cache, credentials, access) {
            factories++
            assertEquals("https://muse/", it.url)
            assertEquals("audio-secret", it.token)
            api
        }

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            cache = AudioMuseMoodCache(db.audioMuseScoreDao())
            configuration = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            for (id in 1L..2L) {
                db.sourcesDao().upsertSource(Source(id, "server", "https://music", id == 1L, id))
                configuration.save(id, "https://muse", "audio-secret")
            }
        }

    @After fun tearDown() = db.close()

    @Test fun onlyMissingUniqueIdsAreFetchedAndSuccessPersistsAcrossFetchers() =
        runBlocking {
            cache.store(cache.scope(1)!!, "cached", scores)
            val first = create().fetch(1, listOf("cached", "new", "new"))
            assertEquals(mapOf("cached" to scores, "new" to scores), first.scores)
            assertEquals(listOf("new"), calls)
            assertTrue(first.failed.isEmpty())
            assertEquals(mapOf("new" to scores), create().fetch(1, listOf("new")).scores)
            assertEquals(1, factories)
            access.value = AudioMuseScoreAccess(2, false)
            create().fetch(2, listOf("new"))
            assertEquals(listOf("new", "new"), calls)
        }

    @Test fun offlineMissingConfigurationAndUnreadableTokenMakeNoRequests() =
        runBlocking {
            cache.store(cache.scope(1)!!, "cached", scores)
            val engine = create()
            access.value = AudioMuseScoreAccess(1, true)
            val offline = engine.fetch(1, listOf("cached", "new"))
            assertEquals(mapOf("cached" to scores), offline.scores)
            assertEquals(setOf("new"), offline.unavailable)
            access.value = AudioMuseScoreAccess(2, false)
            assertEquals(setOf("cached"), engine.fetch(1, listOf("cached")).unavailable)
            configuration.disconnect(2)
            assertEquals(setOf("new"), engine.fetch(2, listOf("new")).unavailable)
            access.value = AudioMuseScoreAccess(1, false)
            credentials.loseKey()
            assertEquals(setOf("cached"), engine.fetch(1, listOf("cached")).unavailable)
            assertEquals(0, factories)
            assertTrue(calls.isEmpty())
        }

    @Test fun only404MissesAreRememberedForThisSessionAndFailuresStayRetryable() =
        runBlocking {
            fetch = { id ->
                when (id) {
                    "missing" -> throw AudioMuseHttpException(404, AudioMuseFailure.NOT_FOUND)
                    "failed" -> throw AudioMuseHttpException(503, AudioMuseFailure.NOT_ANALYSED)
                    else -> scores
                }
            }
            val engine = create()
            val first = engine.fetch(1, listOf("ok", "missing", "failed"))
            assertEquals(mapOf("ok" to scores), first.scores)
            assertEquals(setOf("missing"), first.notAnalysed)
            assertEquals(setOf("failed"), first.failed)
            engine.fetch(1, listOf("missing", "failed"))
            assertEquals(1, calls.count { it == "missing" })
            assertEquals(2, calls.count { it == "failed" })
            create().fetch(1, listOf("missing"))
            assertEquals(2, calls.count { it == "missing" })
            fetch = { scores }
            assertEquals(mapOf("failed" to scores), engine.fetch(1, listOf("failed")).scores)
        }

    @Test fun aCachedSuccessSupersedesAnEarlierSessionMiss() =
        runBlocking {
            fetch = { throw AudioMuseHttpException(404, AudioMuseFailure.NOT_FOUND) }
            val engine = create()
            assertEquals(setOf("a"), engine.fetch(1, listOf("a")).notAnalysed)
            cache.store(cache.scope(1)!!, "a", scores)
            val result = engine.fetch(1, listOf("a"))
            assertEquals(mapOf("a" to scores), result.scores)
            assertTrue(result.notAnalysed.isEmpty())
            assertEquals(1, calls.size)
        }

    @Test fun aTotalTransportFailureIsExplicitAndNotCached() =
        runBlocking {
            fetch = { throw IOException("test failure") }
            val engine = create()
            val result = engine.fetch(1, listOf("a", "b"))
            assertTrue(result.scores.isEmpty())
            assertEquals(setOf("a", "b"), result.failed)
            assertTrue(cache.read(1, listOf("a", "b")).isEmpty())
            fetch = { scores }
            assertEquals(2, engine.fetch(1, listOf("a", "b")).scores.size)
        }

    @Test(timeout = 15_000)
    fun requestsAreBoundedAndOverlappingBatchesReuseResults() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val running = AtomicInteger()
            val maximum = AtomicInteger()
            fetch = {
                val count = running.incrementAndGet()
                maximum.updateAndGet { before -> maxOf(before, count) }
                if (count == 5) started.complete(Unit)
                try {
                    release.await()
                    scores
                } finally {
                    running.decrementAndGet()
                }
            }
            val engine = create()
            val first = async { engine.fetch(1, List(12) { "$it" }) }
            withTimeout(5_000) { started.await() }
            val second = async { engine.fetch(1, listOf("0", "1")) }
            assertEquals(5, running.get())
            release.complete(Unit)
            assertEquals(12, first.await().scores.size)
            assertEquals(2, second.await().scores.size)
            assertEquals(5, maximum.get())
            assertEquals(12, calls.size)
            assertEquals(1, factories)
        }

    private suspend fun cancellation(change: suspend () -> Unit) =
        coroutineScope {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            fetch = {
                started.complete(Unit)
                try {
                    CompletableDeferred<AudioMuseMoodScores>().await()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            val request = async { create().fetch(1, listOf("a")) }
            withTimeout(5_000) { started.await() }
            change()
            withTimeout(5_000) { cancelled.await() }
            try {
                request.await()
                error("Expected cancellation")
            } catch (_: CancellationException) {
                assertTrue(request.isCancelled)
            }
            assertTrue(cache.read(1, listOf("a")).isEmpty())
        }

    @Test(timeout = 15_000)
    fun sourceSwitchCancelsAnInFlightRequest() =
        runBlocking {
            cancellation { access.value = AudioMuseScoreAccess(2, false) }
        }

    @Test(timeout = 15_000)
    fun goingOfflineCancelsAnInFlightRequest() =
        runBlocking {
            cancellation { access.value = AudioMuseScoreAccess(1, true) }
        }

    @Test(timeout = 15_000)
    fun changingConfigurationCancelsAnInFlightRequest() =
        runBlocking {
            cancellation { configuration.save(1, "https://muse", "replacement") }
        }

    @Test fun rejectsOversizedOrBlankInputsBeforeCreatingAClient() =
        runBlocking<Unit> {
            val engine = create()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.fetch(1, List(501) { "$it" }) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { engine.fetch(1, listOf(" ")) } }
            assertTrue(engine.fetch(1, emptyList()).scores.isEmpty())
            assertEquals(0, factories)
        }
}
