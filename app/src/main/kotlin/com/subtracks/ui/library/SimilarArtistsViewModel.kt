package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.recommendations.ResolvedSimilarArtist
import com.subtracks.data.recommendations.SimilarArtist
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimilarArtistsState(
    val sourceId: Long? = null,
    val offline: Boolean = true,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val checked: Boolean = false,
    val artists: List<ResolvedSimilarArtist> = emptyList(),
    /** Photo URLs for recommendations outside the library, only while the opt-in is on. */
    val photos: Map<SimilarArtist, String> = emptyMap(),
)

class SimilarArtistsViewModel(
    private val artistId: String,
    sources: Flow<Long?>,
    offline: Flow<Boolean>,
    private val read: suspend (Long, String) -> List<ResolvedSimilarArtist>,
    private val refresh: suspend (Long, String) -> List<ResolvedSimilarArtist>,
    photosEnabled: Flow<Boolean> = flowOf(false),
    private val photo: suspend (SimilarArtist) -> String? = { null },
) : ViewModel() {
    private val mutableState = MutableStateFlow(SimilarArtistsState())
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var request: Job? = null

    init {
        viewModelScope.launch {
            combine(sources, offline) { id, off -> id to off }.collectLatest { (id, off) ->
                generation++
                request?.cancel()
                mutableState.value = SimilarArtistsState(sourceId = id, offline = off)
                val artists = if (id == null) emptyList() else read(id, artistId)
                mutableState.value = mutableState.value.copy(loading = false, artists = artists)
            }
        }
        viewModelScope.launch {
            val shown = state.map { Triple(it.sourceId, it.offline, it.artists) }.distinctUntilChanged()
            combine(shown, photosEnabled) { key, enabled -> key to enabled }.collectLatest { (key, enabled) ->
                val (id, off, artists) = key
                if (!enabled) {
                    mutableState.update { it.copy(photos = emptyMap()) }
                    return@collectLatest
                }
                if (off || id == null) return@collectLatest
                for (artist in artists) {
                    val recommendation = artist.recommendation
                    if (artist.libraryArtist != null || recommendation in state.value.photos) continue
                    val url = photo(recommendation) ?: continue
                    mutableState.update {
                        if (it.sourceId == id && !it.offline) it.copy(photos = it.photos + (recommendation to url)) else it
                    }
                }
            }
        }
    }

    fun refresh() {
        val before = state.value
        val id = before.sourceId ?: return
        if (before.offline || before.loading || before.busy) return
        val token = generation
        mutableState.value = before.copy(busy = true)
        request =
            viewModelScope.launch {
                try {
                    val artists = refresh(id, artistId)
                    if (generation == token) mutableState.value = state.value.copy(artists = artists, checked = true)
                } finally {
                    if (generation == token) mutableState.value = state.value.copy(busy = false)
                }
            }
    }
}
