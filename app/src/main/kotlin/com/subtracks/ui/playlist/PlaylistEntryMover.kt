package com.subtracks.ui.playlist

import com.subtracks.R
import com.subtracks.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Serializes moves until the server write and mirror refresh finish. Owned by the playlist ViewModel. */
internal class PlaylistEntryMover(
    private val scope: CoroutineScope,
    private val move: suspend (Long, String, Int) -> Unit,
    private val showMessage: (UiMessage) -> Unit,
) {
    private val moving = MutableStateFlow(false)
    val busy = moving.asStateFlow()

    fun move(
        position: Long,
        songId: String,
        destination: Int,
    ) {
        if (moving.value) return
        moving.value = true
        scope.launch {
            try {
                move.invoke(position, songId, destination)
                showMessage(UiMessage(R.string.playlist_song_moved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showMessage(playlistEditMessage(failure))
            } finally {
                moving.value = false
            }
        }
    }
}
