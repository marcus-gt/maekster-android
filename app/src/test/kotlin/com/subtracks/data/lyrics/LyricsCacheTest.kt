package com.subtracks.data.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.CachedLyrics
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsCacheTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var cache: LyricsCache

    @Before
    fun setUp() {
        db = createTestDatabase()
        cache = LyricsCache(db.lyricsCacheDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun sources() {
        for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
    }

    private fun lyrics(text: String) = listOf(Lyrics(listOf(LyricsLine(text)), synced = false))

    @Test
    fun cacheIsScopedByServerSongAndProviderAndUpdatesOnlyItsKey() =
        runTest {
            sources()
            for (source in 1L..2L) {
                for (song in listOf("a", "b")) {
                    for (provider in LyricsProviderId.entries) {
                        cache.put(source, song, provider, lyrics("$source-$song-${provider.key}"), 100)
                    }
                }
            }
            cache.put(1, "a", LyricsProviderId.Server, lyrics("updated"), 200)
            for (source in 1L..2L) {
                for (song in listOf("a", "b")) {
                    for (provider in LyricsProviderId.entries) {
                        val expected =
                            if (source == 1L && song == "a" &&
                                provider == LyricsProviderId.Server
                            ) {
                                "updated"
                            } else {
                                "$source-$song-${provider.key}"
                            }
                        assertEquals(lyrics(expected), cache.read(source, song, provider))
                    }
                }
            }
            assertEquals(200L, db.lyricsCacheDao().get(1, "a", "server")!!.fetchedAt)
            cache.remove(1, "a", LyricsProviderId.Server)
            assertNull(cache.read(1, "a", LyricsProviderId.Server))
            assertEquals(lyrics("1-a-lrclib"), cache.read(1, "a", LyricsProviderId.Lrclib))
            db.sourcesDao().deleteSource(1)
            for (provider in LyricsProviderId.entries) {
                assertNull(cache.read(1, "b", provider))
                assertEquals(lyrics("2-b-${provider.key}"), cache.read(2, "b", provider))
            }
        }

    @Test
    fun timedAndPlainLanguageVariantsRoundTripWithOffsetsUnicodeAndDuplicates() =
        runTest {
            sources()
            val tracks =
                listOf(
                    Lyrics(listOf(LyricsLine("夜 🎵", 0), LyricsLine("Again", 1000), LyricsLine("Translation", 1000)), true, "ja", -250),
                    Lyrics(listOf(LyricsLine("Blåbær\n\"quoted\"")), false, "nb", 125),
                )
            cache.put(1, "a", LyricsProviderId.Server, tracks, 100)
            assertEquals(tracks, LyricsCache(db.lyricsCacheDao()).read(1, "a", LyricsProviderId.Server))
            cache.put(1, "empty", LyricsProviderId.Server, emptyList(), 100)
            assertEquals(emptyList<Lyrics>(), cache.read(1, "empty", LyricsProviderId.Server))
            assertNull(cache.read(1, "missing", LyricsProviderId.Server))
        }

    @Test
    fun corruptOrUnknownPayloadIsACacheMiss() =
        runTest {
            sources()
            for (payload in listOf(
                "invalid",
                """{"version":2,"tracks":[]}""",
                """{"version":1}""",
                """{"version":1,"tracks":[{"synced":true,"language":"en","offsetMs":0,"lines":[{"text":"Line","startMs":null}]}]}""",
                """{"version":1,"tracks":[{"synced":true,"language":"en","offsetMs":0,"lines":[{"text":"Line","startMs":-1}]}]}""",
                """{"version":1,"tracks":[{"synced":true,"language":"en","offsetMs":0,"lines":[{"text":"Line","startMs":1.5}]}]}""",
                """{"version":1,"tracks":[{"synced":false,"language":"en","offsetMs":0,"lines":[]}]}""",
            )) {
                db.lyricsCacheDao().put(CachedLyrics(1, "a", "server", payload, 100))
                assertNull(payload, cache.read(1, "a", LyricsProviderId.Server))
            }
        }
}
