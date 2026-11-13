package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.QuickAddOutcome
import com.subtracks.data.repo.QuickPlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The heart's long-press menu for one song: its server's quick playlists, in the chosen order. */
class QuickPlaylistMenuViewModel(
    private val song: Song,
    private val quick: QuickPlaylistRepository,
    private val showMessage: (UiMessage) -> Unit,
    private val work: CoroutineScope,
) : ViewModel() {
    /** Null until loaded, so the menu doesn't flash "Choose…" for a list that exists. */
    val playlists: StateFlow<List<Playlist>?> =
        quick.quickPlaylists(song.sourceId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Adds the song in the background: the menu closes at once and a snackbar reports the result, as on iOS. */
    fun add(playlist: Playlist) {
        work.launch {
            try {
                val message =
                    when (quick.add(song.sourceId, playlist.id, song.id)) {
                        QuickAddOutcome.Added -> R.string.playlist_songs_added
                        QuickAddOutcome.AlreadyThere -> R.string.playlist_already_has_songs
                    }
                showMessage(UiMessage(message, listOf(playlist.name)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showMessage(playlistEditMessage(failure))
            }
        }
    }
}

/** Every playlist the user can add to on [sourceId], with the quick ones ticked; a tap toggles one. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickPlaylistPickerViewModel(
    private val sourceId: Long,
    playlists: PlaylistRepository,
    private val quick: QuickPlaylistRepository,
    owner: suspend () -> String?,
) : ViewModel() {
    val destinations: Flow<PagingData<Playlist>> =
        flow { emit(owner()) }
            .flatMapLatest { name -> playlists.destinations(sourceId, name, "", emptyList()) }
            .cachedIn(viewModelScope)

    val chosen: StateFlow<Set<String>> =
        quick
            .quickPlaylists(sourceId)
            .map { list -> list.mapTo(mutableSetOf()) { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(playlist: Playlist) {
        viewModelScope.launch { quick.toggle(sourceId, playlist.id) }
    }
}
