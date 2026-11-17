package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.recommendations.ResolvedSimilarArtist
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SimilarArtistsState(
    val sourceId: Long? = null,
    val offline: Boolean = true,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val checked: Boolean = false,
    val artists: List<ResolvedSimilarArtist> = emptyList(),
)

class SimilarArtistsViewModel(
    private val artistId: String,
    sources: Flow<Long?>,
    offline: Flow<Boolean>,
    private val read: suspend (Long, String) -> List<ResolvedSimilarArtist>,
    private val refresh: suspend (Long, String) -> List<ResolvedSimilarArtist>,
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
