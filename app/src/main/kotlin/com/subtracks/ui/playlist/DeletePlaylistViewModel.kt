package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.repo.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The playlist to delete; [hasDownloads] offers to remove its downloaded songs too. */
data class DeletePlaylistRequest(
    val sourceId: Long,
    val playlistId: String,
    val name: String,
    val hasDownloads: Boolean,
)

data class DeletePlaylistUi(
    val busy: Boolean = false,
    val error: UiMessage? = null,
)

/**
 * Deletes one playlist from the server. Downloads go only when asked, and only after the server confirmed; a
 * failure keeps the dialog so the user can try again.
 */
class DeletePlaylistViewModel(
    private val request: DeletePlaylistRequest,
    private val playlists: PlaylistRepository,
    private val purgeDownloads: suspend (sourceId: Long, playlistId: String) -> Unit,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(DeletePlaylistUi())
    val ui: StateFlow<DeletePlaylistUi> = state.asStateFlow()

    private val finished = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the playlist is gone from the server and the mirror. */
    val done: Flow<Unit> = finished.receiveAsFlow()

    fun delete(withDownloads: Boolean) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                playlists.delete(request.sourceId, request.playlistId) {
                    if (withDownloads) purgeDownloads(request.sourceId, request.playlistId)
                }
                showMessage(UiMessage(R.string.playlist_deleted, listOf(request.name)))
                finished.send(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state.update { it.copy(error = playlistEditMessage(failure)) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
