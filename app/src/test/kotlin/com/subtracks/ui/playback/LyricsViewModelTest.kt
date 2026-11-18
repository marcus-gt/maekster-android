package com.subtracks.ui.playback

import androidx.lifecycle.viewModelScope
import com.subtracks.cancelAndJoinBlocking
import com.subtracks.data.lyrics.Lyrics
import com.subtracks.data.lyrics.LyricsLine
import com.subtracks.data.lyrics.LyricsLoader
import com.subtracks.data.lyrics.LyricsProviderId
import com.subtracks.data.lyrics.LyricsState
import com.subtracks.data.lyrics.ProvidedLyrics
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.LyricsPreferences
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val song = Song(1, "a", null, null, "Title", "Album", "Artist", 120, null, null, null)
    private val lyrics = ProvidedLyrics(LyricsProviderId.Server, Lyrics(listOf(LyricsLine("Hello", 1000)), synced = true))
    private val preferences = MutableStateFlow(LyricsPreferences(showCurrentLine = false))
    private val fetched = mutableListOf<String>()
    private var model: LyricsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        cancelAndJoinBlocking(model?.viewModelScope)
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        LyricsViewModel(
            song = flowOf(song),
            preferences = preferences,
            offline = flowOf(false),
            loader =
                LyricsLoader { requested, _, _, _ ->
                    fetched += requested.id
                    listOf(lyrics)
                },
        ).also { model = it }

    @Test
    fun nothingIsLookedUpUntilThePaneOrThePreviewShowsLyrics() =
        runTest {
            val model = viewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            assertEquals(LyricsState.Idle, model.ui.value.state)
            assertEquals(emptyList<String>(), fetched)

            model.setPaneOpen(true)
            assertEquals(LyricsState.Found(listOf(lyrics)), model.ui.value.state)
            assertEquals(listOf("a"), fetched)

            model.setPaneOpen(false)
            preferences.value = preferences.value.copy(showCurrentLine = true)
            assertEquals(LyricsState.Found(listOf(lyrics)), model.ui.value.state)
            assertEquals(true, model.ui.value.showCurrentLine)
        }

    @Test
    fun timingCorrectionMovesTheCurrentLineWithoutReloading() =
        runTest {
            val model = viewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            model.setPaneOpen(true)
            assertEquals(
                0,
                model.ui.value.display!!
                    .timing
                    .currentLine(1000),
            )
            preferences.value = preferences.value.copy(timingOffsetMs = 500)
            assertEquals(
                null,
                model.ui.value.display!!
                    .timing
                    .currentLine(1000),
            )
            assertEquals(listOf("a"), fetched)
        }

    @Test
    fun playingSongFollowsTheItemAndClearsWhenPlaybackEnds() =
        runTest {
            val state = MutableStateFlow(PlaybackState())
            val songs = mutableListOf<Song?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                playingSong(state) { sourceId, id -> flowOf(song.copy(sourceId = sourceId, id = id)) }.toList(songs)
            }
            val playing =
                PlaybackState(
                    item = QueueItem("b", "Title", "Artist", "Album", null),
                    context = QueueContext(QueueKind.Album, 7, "album"),
                )
            state.value = playing
            state.value = playing.copy(isPlaying = true, position = 3)
            state.value = PlaybackState()
            assertEquals(listOf(null, song.copy(sourceId = 7, id = "b"), null), songs)
        }
}
