package com.subtracks.data.recommendations

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.CachedSimilarArtists
import com.subtracks.data.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimilarArtistsCacheTest {
    private lateinit var db: SubtracksDatabase
    private var time = 100L
    private var calls = 0
    private var factories = 0
    private var allowed = true
    private var result = listOf(SimilarArtist("a", "A", "cover", "mbid"), SimilarArtist(null, "External"))
    private var fetch: suspend () -> List<SimilarArtist> = { result }
    private lateinit var cache: SimilarArtistsCache

    @Before fun setUp() {
        db = createTestDatabase()
        cache =
            SimilarArtistsCache(db.similarArtistsCacheDao(), {
                factories++
                SimilarArtists { _, _ ->
                    calls++
                    fetch()
                }
            }, { time })
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
    }

    @Test fun persistsMetadataScopesBySourceAndArtistAndReadsWithoutNetworking() =
        runTest {
            seed()
            cache.refreshIfStale(1, "seed") { allowed }
            val saved = result
            result = listOf(SimilarArtist("b", "B"))
            cache.refreshIfStale(2, "seed") { allowed }
            cache.refreshIfStale(1, "other") { allowed }
            assertEquals(saved, cache.read(1, "seed"))
            assertEquals(result, cache.read(2, "seed"))
            assertEquals(result, cache.read(1, "other"))
            assertEquals(emptyList<SimilarArtist>(), cache.read(2, "other"))
            assertEquals(3, calls)
        }

    @Test fun offlineNeverConstructsProviderAndExpiredFailuresKeepOldTimestampAndResult() =
        runTest {
            seed()
            allowed = false
            cache.refreshIfStale(1, "seed") { allowed }
            assertEquals(0, factories)
            allowed = true
            cache.refreshIfStale(1, "seed") { allowed }
            val saved = result
            time += SimilarArtistsCache.TTL_MS
            fetch = { throw java.io.IOException("offline") }
            allowed = false
            assertEquals(saved, cache.refreshIfStale(1, "seed") { allowed })
            assertEquals(1, factories)
            allowed = true
            assertEquals(saved, cache.refreshIfStale(1, "seed") { allowed })
            assertEquals(100L, db.similarArtistsCacheDao().get(1, "seed")!!.fetchedAt)
            assertEquals(saved, cache.read(1, "seed"))
        }

    @Test fun successfulEmptyResultsAreCachedUntilTtlAndRefreshReplacesTheAnswer() =
        runTest {
            seed()
            result = emptyList()
            cache.refreshIfStale(1, "seed") { true }
            result = listOf(SimilarArtist("b", "B"))
            time += SimilarArtistsCache.TTL_MS - 1
            assertEquals(emptyList<SimilarArtist>(), cache.refreshIfStale(1, "seed") { true })
            assertEquals(1, calls)
            time++
            assertEquals(result, cache.refreshIfStale(1, "seed") { true })
            assertEquals(2, calls)
        }

    @Test fun serverSwitchOrConnectivityLossDiscardsTheReplyAndCancellationPropagates() =
        runTest {
            seed()
            fetch = {
                allowed = false
                result
            }
            assertEquals(emptyList<SimilarArtist>(), cache.refreshIfStale(1, "seed") { allowed })
            assertEquals(null, db.similarArtistsCacheDao().get(1, "seed"))
            allowed = true
            fetch = { throw CancellationException("cancelled") }
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking { cache.refreshIfStale(1, "seed") { allowed } }
            }
            assertEquals(null, db.similarArtistsCacheDao().get(1, "seed"))
        }

    @Test fun concurrentRefreshesForTheSameSeedOnlyFetchOnce() =
        runTest {
            seed()
            val replies = (1..3).map { async { cache.refreshIfStale(1, "seed") { true } } }.awaitAll()
            assertEquals(listOf(result, result, result), replies)
            assertEquals(1, calls)
        }

    @Test fun corruptPayloadIsIgnoredAndRefreshedWhileOversizedProvidersAreBounded() =
        runTest {
            seed()
            db.similarArtistsCacheDao().put(CachedSimilarArtists(1, "seed", "{broken", time))
            assertEquals(emptyList<SimilarArtist>(), cache.read(1, "seed"))
            result = (1..100).map { SimilarArtist("$it", "Artist $it") }
            assertEquals(50, cache.refreshIfStale(1, "seed") { true }.size)
            assertEquals(result.take(50), cache.read(1, "seed"))
        }
}
