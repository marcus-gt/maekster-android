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

/** The playlist to rename and the name it had when the dialog opened. */
data class RenamePlaylistRequest(
    val sourceId: Long,
    val playlistId: String,
    val currentName: String,
)

data class RenamePlaylistUi(
    val name: String,
    val currentName: String,
    val busy: Boolean = false,
    val error: UiMessage? = null,
) {
    /** A name that is blank or the same once trimmed would change nothing. */
    val canRename: Boolean get() = !busy && name.trim().let { it.isNotEmpty() && it != currentName }
}

/** Renames one playlist. A failure keeps the dialog and the typed name so the user can retry. */
class RenamePlaylistViewModel(
    private val request: RenamePlaylistRequest,
    private val playlists: PlaylistRepository,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(RenamePlaylistUi(name = request.currentName, currentName = request.currentName))
    val ui: StateFlow<RenamePlaylistUi> = state.asStateFlow()

    private val finished = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the playlist is renamed and the mirror shows the new name. */
    val done: Flow<Unit> = finished.receiveAsFlow()

    fun setName(name: String) = state.update { it.copy(name = name, error = null) }

    fun rename() {
        val current = state.value
        if (!current.canRename) return
        val name = current.name.trim()
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                playlists.rename(request.sourceId, request.playlistId, name)
                showMessage(UiMessage(R.string.playlist_renamed, listOf(name)))
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
