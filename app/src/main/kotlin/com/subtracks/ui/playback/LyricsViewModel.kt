package com.subtracks.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.lyrics.LyricsDisplay
import com.subtracks.data.lyrics.LyricsLoader
import com.subtracks.data.lyrics.LyricsState
import com.subtracks.data.lyrics.display
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.LyricsPreferences
import com.subtracks.playback.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The mirrored song behind the playing item, re-read when its row changes and replaced when the item does. */
@OptIn(ExperimentalCoroutinesApi::class)
fun playingSong(
    state: Flow<PlaybackState>,
    song: (Long, String) -> Flow<Song?>,
): Flow<Song?> =
    state
        .map { current -> current.context?.sourceId?.let { sourceId -> current.item?.id?.let { sourceId to it } } }
        .distinctUntilChanged()
        .flatMapLatest { key -> key?.let { song(it.first, it.second) } ?: flowOf(null) }

/** What the lyrics pane and the current-line preview render. */
data class LyricsUi(
    val state: LyricsState = LyricsState.Idle,
    val display: LyricsDisplay? = null,
    val showCurrentLine: Boolean = true,
)

class LyricsViewModel(
    song: Flow<Song?>,
    preferences: Flow<LyricsPreferences>,
    offline: Flow<Boolean>,
    loader: LyricsLoader,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val language = MutableStateFlow<String?>(null)
    private val paneOpen = MutableStateFlow(false)

    // As on iOS, lyrics are looked up only while something shows them: the pane, or the current-line preview.
    private val requested =
        combine(song, paneOpen, preferences) { current, open, prefs ->
            current?.takeIf { open || prefs.showCurrentLine }
        }

    val ui: StateFlow<LyricsUi> =
        combine(loader.states(requested, preferences, offline, attempts), preferences, language) { state, prefs, chosen ->
            LyricsUi(
                state = state,
                display = (state as? LyricsState.Found)?.display(chosen, prefs.timingOffsetMs),
                showCurrentLine = prefs.showCurrentLine,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUi())

    fun retry() {
        attempts.update { it + 1 }
    }

    fun selectLanguage(tag: String) {
        language.value = tag
    }

    fun setPaneOpen(open: Boolean) {
        paneOpen.value = open
    }
}
