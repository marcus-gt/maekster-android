package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.model.AlbumSongItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TopSongsState(
    val sourceId: Long? = null,
    val songs: List<AlbumSongItem> = emptyList(),
)

/**
 * The artist page's Top songs. It reads the cache, and on page open while online asks the server once per
 * server (D9, as iOS does). Offline it shows only the cached songs that can play offline.
 */
class TopSongsViewModel(
    private val artistId: String,
    private val artistName: (Long) -> Flow<String?>,
    sources: Flow<Long?>,
    offline: Flow<Boolean>,
    private val songs: (Long, String, Boolean) -> Flow<List<AlbumSongItem>>,
    private val refresh: suspend (Long, String, String) -> Boolean,
    private val play: (Long, List<String>, Int, Boolean, String) -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TopSongsState())
    val state = mutableState.asStateFlow()
    private val requested = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            combine(sources, offline) { id, off -> id to off }
                .distinctUntilChanged()
                .collectLatest { (id, off) ->
                    if (id == null) {
                        mutableState.value = TopSongsState()
                        return@collectLatest
                    }
                    if (state.value.sourceId != id || off) mutableState.value = TopSongsState(sourceId = id)
                    coroutineScope {
                        if (!off && id !in requested) {
                            launch {
                                val name = artistName(id).first { !it.isNullOrBlank() }.orEmpty()
                                refresh(id, artistId, name)
                                requested += id
                            }
                        }
                        songs(id, artistId, off).collect { list ->
                            mutableState.value = TopSongsState(id, list)
                        }
                    }
                }
        }
    }

    /** Plays the shown list from [index], or shuffles it. */
    fun play(
        index: Int,
        shuffled: Boolean = false,
    ) {
        val current = state.value
        val id = current.sourceId ?: return
        if (current.songs.isEmpty()) return
        play(id, current.songs.map { it.song.id }, index, shuffled, artistId)
    }
}
