package com.subtracks.data.lyrics

import com.subtracks.data.model.Song
import com.subtracks.data.prefs.LyricsPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsLoaderTest {
    private data class Call(
        val songId: String,
        val source: LyricsSource,
        val offline: Boolean,
        val allowLrclib: Boolean,
    )

    private val first = Song(1, "a", null, null, "First", "Album", "Artist", 120, null, null, null)
    private val second = Song(1, "b", null, null, "Second", "Album", "Artist", 120, null, null, null)
    private val timed = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine("Hello", 1000)), synced = true))

    private val song = MutableStateFlow<Song?>(null)
    private val preferences = MutableStateFlow(LyricsPreferences())
    private val offline = MutableStateFlow(false)
    private val attempts = MutableStateFlow(0)
    private val calls = mutableListOf<Call>()
    private var answer: suspend (Song) -> List<ProvidedLyrics> = { listOf(timed) }

    private val loader =
        LyricsLoader { song, source, offline, allowLrclib ->
            calls += Call(song.id, source, offline, allowLrclib)
            answer(song)
        }

    private fun TestScope.collect(): MutableList<LyricsState> {
        val states = mutableListOf<LyricsState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            loader.states(song, preferences, offline, attempts).toList(states)
        }
        return states
    }

    @Test
    fun loadsOncePerSongAndIgnoresUnrelatedChanges() =
        runTest {
            val states = collect()
            assertEquals(listOf(LyricsState.Idle), states)
            song.value = first
            song.value = first.copy(starred = 5)
            preferences.value = preferences.value.copy(timingOffsetMs = 500, showCurrentLine = false)
            assertEquals(listOf(LyricsState.Idle, LyricsState.Loading, LyricsState.Found(listOf(timed))), states)
            assertEquals(listOf(Call("a", LyricsSource.Auto, offline = false, allowLrclib = false)), calls)
        }

    @Test
    fun connectivityAndPermissionReloadTheSameSongWithoutLoading() =
        runTest {
            val states = collect()
            song.value = first
            answer = { emptyList() }
            offline.value = true
            preferences.value = preferences.value.copy(allowLrclib = true)
            assertEquals(
                listOf(
                    LyricsState.Idle,
                    LyricsState.Loading,
                    LyricsState.Found(listOf(timed)),
                    LyricsState.Missing(offline = true),
                    LyricsState.Missing(offline = true),
                ),
                states,
            )
            assertEquals(
                listOf(
                    Call("a", LyricsSource.Auto, offline = false, allowLrclib = false),
                    Call("a", LyricsSource.Auto, offline = true, allowLrclib = false),
                    Call("a", LyricsSource.Auto, offline = true, allowLrclib = true),
                ),
                calls,
            )
        }

    @Test
    fun aNewSongCancelsTheEarlierLookup() =
        runTest {
            val slow = CompletableDeferred<List<ProvidedLyrics>>()
            val other = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine("Second")), synced = false))
            answer = { if (it.id == "a") slow.await() else listOf(other) }
            val states = collect()
            song.value = first
            song.value = second
            slow.complete(listOf(timed))
            assertEquals(
                listOf(LyricsState.Idle, LyricsState.Loading, LyricsState.Loading, LyricsState.Found(listOf(other))),
                states,
            )
        }

    @Test
    fun aFailureCanBeRetried() =
        runTest {
            answer = { throw IOException("down") }
            val states = collect()
            song.value = first
            assertEquals(LyricsState.Failed, states.last())
            answer = { listOf(timed) }
            attempts.value = 1
            assertEquals(LyricsState.Found(listOf(timed)), states.last())
            assertEquals(2, calls.size)
        }

    @Test
    fun stoppingPlaybackReturnsToIdle() =
        runTest {
            val states = collect()
            song.value = first
            song.value = null
            assertEquals(LyricsState.Idle, states.last())
        }

    @Test
    fun displayAppliesTheListenerCorrectionAndLanguageChoice() {
        val english = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine("Hi", 1000)), synced = true, language = "eng"))
        val german = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine("Hallo", 1000)), synced = true, language = "deu"))
        val found = LyricsState.Found(listOf(english, german))

        val shown = found.display(preferredLanguage = null, correctionMs = 500, localeLanguage = "en-US")!!
        assertEquals(english, shown.lyrics)
        assertEquals(listOf("en", "de"), shown.languages)
        assertEquals(null, shown.timing.currentLine(1400))
        assertEquals(0, shown.timing.currentLine(1500))

        assertEquals(german, found.display(preferredLanguage = "de", correctionMs = 0, localeLanguage = "en-US")!!.lyrics)
    }
}
