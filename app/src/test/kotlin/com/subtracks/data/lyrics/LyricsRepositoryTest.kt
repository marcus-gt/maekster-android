package com.subtracks.data.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.CachedLyrics
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LyricsRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var cache: LyricsCache
    private lateinit var repository: LyricsRepository
    private val server = FakeProvider()
    private val lrclib = FakeProvider()
    private val song = Song(1, "song", null, null, "Title", "Album", "Artist", 120, null, null, null)

    @Before
    fun setUp() {
        db = createTestDatabase()
        cache = LyricsCache(db.lyricsCacheDao())
        repository = LyricsRepository(cache, server, lrclib) { 99 }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun source() {
        db.sourcesDao().upsertSource(Source(1, "server", "https://server", true, 1))
    }

    private fun plain(text: String) = Lyrics(listOf(LyricsLine(text)), synced = false)

    private fun timed(text: String) = Lyrics(listOf(LyricsLine(text, 1000)), synced = true)

    @Test
    fun syncedServerWinsWithoutExternalLookupAndSubsequentFetchUsesCache() =
        runTest {
            source()
            server.result = listOf(plain("Server plain"), timed("Server timed"))
            lrclib.result = listOf(timed("External"))
            val expected = server.result.map { ProvidedLyrics(LyricsProviderId.Server, it) }
            assertEquals(expected, repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true))
            assertEquals(expected, repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true))
            assertEquals(1, server.calls)
            assertEquals(0, lrclib.calls)
            assertEquals(99L, db.lyricsCacheDao().get(1, "song", "server")!!.fetchedAt)
        }

    @Test
    fun autoPrefersExternalTimingButRetainsServerPlainAndBothRemainAvailableOffline() =
        runTest {
            source()
            server.result = listOf(plain("Server"))
            lrclib.result = listOf(plain("External plain"), timed("External timed"))
            val expected =
                listOf(
                    ProvidedLyrics(LyricsProviderId.Lrclib, timed("External timed")),
                    ProvidedLyrics(LyricsProviderId.Server, plain("Server")),
                )
            assertEquals(expected, repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true))
            assertEquals(expected, repository.fetch(song, LyricsSource.Auto, offline = true, allowLrclib = true))
            assertEquals(1, server.calls)
            assertEquals(1, lrclib.calls)
        }

    @Test
    fun providerChoicesAndOptInNeverLeakDisabledProvidersFromCache() =
        runTest {
            source()
            cache.put(1, "song", LyricsProviderId.Server, listOf(plain("Server")), 1)
            cache.put(1, "song", LyricsProviderId.Lrclib, listOf(timed("External")), 1)
            assertEquals(
                listOf(ProvidedLyrics(LyricsProviderId.Server, plain("Server"))),
                repository.fetch(song, LyricsSource.Server, offline = false, allowLrclib = true),
            )
            assertEquals(
                listOf(ProvidedLyrics(LyricsProviderId.Lrclib, timed("External"))),
                repository.fetch(song, LyricsSource.Lrclib, offline = true, allowLrclib = true),
            )
            assertEquals(emptyList<ProvidedLyrics>(), repository.fetch(song, LyricsSource.Lrclib, offline = false, allowLrclib = false))
            assertEquals(
                listOf(ProvidedLyrics(LyricsProviderId.Server, plain("Server"))),
                repository.fetch(song, LyricsSource.Auto, offline = true, allowLrclib = false),
            )
            assertEquals(0, server.calls)
            assertEquals(0, lrclib.calls)
        }

    @Test
    fun offlineMissesNeverCallProviders() =
        runTest {
            source()
            server.result = listOf(timed("Must not fetch"))
            lrclib.result = listOf(timed("Must not fetch"))
            for (mode in LyricsSource.entries) {
                assertEquals(emptyList<ProvidedLyrics>(), repository.fetch(song, mode, offline = true, allowLrclib = true))
            }
            assertEquals(0, server.calls)
            assertEquals(0, lrclib.calls)
        }

    @Test
    fun providerFailuresFallbackOnlyWhenAllowedAndPlainServerSurvivesExternalError() =
        runTest {
            source()
            server.result = listOf(plain("Keep"))
            lrclib.failure = IOException("External unavailable")
            assertEquals(
                listOf(ProvidedLyrics(LyricsProviderId.Server, plain("Keep"))),
                repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true),
            )
            cache.remove(1, "song", LyricsProviderId.Server)
            server.failure = IOException("Server unavailable")
            assertThrows(IOException::class.java) {
                kotlinx.coroutines.runBlocking { repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = false) }
            }
            assertEquals(1, lrclib.calls)
            lrclib.failure = null
            lrclib.result = listOf(plain("External"))
            assertEquals(
                listOf(ProvidedLyrics(LyricsProviderId.Lrclib, plain("External"))),
                repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true),
            )
        }

    @Test
    fun cancellationNeverFallsBackOrReturnsPlainLyrics() =
        runTest {
            source()
            server.failure = CancellationException("Cancelled")
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking { repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true) }
            }
            assertEquals(0, lrclib.calls)
            assertNull(cache.read(1, "song", LyricsProviderId.Server))
            server.failure = null
            server.result = listOf(plain("Keep only on real errors"))
            lrclib.failure = CancellationException("Cancelled")
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking { repository.fetch(song, LyricsSource.Auto, offline = false, allowLrclib = true) }
            }
            assertNull(cache.read(1, "song", LyricsProviderId.Lrclib))
        }

    @Test
    fun corruptCacheRefetchesAndEmptyResultsAreNotPermanentlyCached() =
        runTest {
            source()
            db.lyricsCacheDao().put(CachedLyrics(1, "song", "server", "invalid", 1))
            server.result = listOf(plain("Refetched"))
            repository.fetch(song, LyricsSource.Server, offline = false, allowLrclib = false)
            assertEquals(server.result, cache.read(1, "song", LyricsProviderId.Server))
            server.result = emptyList()
            repeat(2) { repository.fetch(song.copy(id = "missing"), LyricsSource.Server, offline = false, allowLrclib = false) }
            assertEquals(3, server.calls)
            assertNull(cache.read(1, "missing", LyricsProviderId.Server))
        }

    private class FakeProvider : LyricsProvider {
        var result = emptyList<Lyrics>()
        var failure: Exception? = null
        var calls = 0

        override suspend fun fetch(song: Song): List<Lyrics> {
            calls++
            failure?.let { throw it }
            return result
        }
    }
}
