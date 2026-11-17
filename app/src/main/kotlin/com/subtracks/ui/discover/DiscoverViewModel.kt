package com.subtracks.ui.discover

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.audiomuse.MoodRadioResult
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.db.MixDao
import com.subtracks.data.mix.MixSeed
import com.subtracks.data.model.Artist
import com.subtracks.data.prefs.DiscoverLayout
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.InstantMixPlayer
import com.subtracks.playback.SmartShufflePlayer
import com.subtracks.ui.components.artistArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a mood radio did not start, or null when it is playing. Matches outside the library are not an empty radio. */
@StringRes
internal fun moodRadioMessage(result: MoodRadioResult): Int? =
    when (result) {
        is MoodRadioResult.Ready -> null
        MoodRadioResult.NoMatches -> R.string.mood_radio_empty
        MoodRadioResult.NotInLibrary -> R.string.mood_radio_not_in_library
        MoodRadioResult.Unavailable -> R.string.mood_radio_unavailable
    }

data class DiscoverState(
    val offline: Boolean = false,
    val artists: List<Artist> = emptyList(),
    /** AudioMuse is configured and readable for the active server, and the app is online. */
    val moods: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    sources: SourceRepository,
    dao: MixDao,
    offline: Flow<Boolean>,
    private val shuffle: SmartShufflePlayer,
    private val station: InstantMixPlayer,
    private val showMessage: (UiMessage) -> Unit,
    private val preferences: UserPreferences,
    moodsAvailable: (Long) -> Flow<Boolean> = { flowOf(false) },
    private val playMood: suspend (RadioMood) -> MoodRadioResult = { MoodRadioResult.Unavailable },
) : ViewModel() {
    val state =
        combine(sources.activeSourceId(), offline) { id, off -> id to off }
            .flatMapLatest { (id, off) ->
                if (id == null || off) {
                    flowOf(DiscoverState(off))
                } else {
                    combine(dao.stationArtists(id), moodsAvailable(id)) { artists, moods -> DiscoverState(false, artists, moods) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverState())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _startingMood = MutableStateFlow<RadioMood?>(null)
    val startingMood = _startingMood.asStateFlow()

    val layout = preferences.discoverLayout().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverLayout())

    fun smartShuffle() = start { if (shuffle.play()) null else R.string.mix_empty }

    fun station(artist: Artist) = start { if (station.play(MixSeed.Artist(artist.id), artist.sourceId)) null else R.string.mix_empty }

    fun moodRadio(mood: RadioMood) = start(mood, failure = R.string.mood_radio_failed) { moodRadioMessage(playMood(mood)) }

    fun art(artist: Artist) = sourcesForArt.artistArt(artist, forIcon = true)

    private val sourcesForArt = sources

    /** Runs one Discover action at a time; [play] returns the message explaining why nothing started, if any. */
    private fun start(
        mood: RadioMood? = null,
        failure: Int = R.string.mix_failed,
        play: suspend () -> Int?,
    ) {
        if (_busy.value) return
        _busy.value = true
        _startingMood.value = mood
        viewModelScope.launch {
            try {
                play()?.let { showMessage(UiMessage(it)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(UiMessage(failure))
            } finally {
                _startingMood.value = null
                _busy.value = false
            }
        }
    }
}
