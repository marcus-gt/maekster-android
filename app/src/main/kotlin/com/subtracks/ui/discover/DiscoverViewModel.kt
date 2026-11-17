package com.subtracks.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoverState(
    val offline: Boolean = false,
    val artists: List<Artist> = emptyList(),
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
) : ViewModel() {
    val state =
        combine(sources.activeSourceId(), offline) { id, off -> id to off }
            .flatMapLatest { (id, off) ->
                if (id == null || off) {
                    flowOf(DiscoverState(off))
                } else {
                    dao.stationArtists(id).map { DiscoverState(false, it) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverState())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val layout = preferences.discoverLayout().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverLayout())

    fun smartShuffle() = start { shuffle.play() }

    fun station(artist: Artist) = start { station.play(MixSeed.Artist(artist.id), artist.sourceId) }

    fun art(artist: Artist) = sourcesForArt.artistArt(artist, forIcon = true)

    private val sourcesForArt = sources

    private fun start(play: suspend () -> Boolean) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                if (!play()) showMessage(UiMessage(R.string.mix_empty))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage(UiMessage(R.string.mix_failed))
            } finally {
                _busy.value = false
            }
        }
    }
}
