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
    private val retryMove: (suspend () -> Unit)? = null,
) {
    private val moving = MutableStateFlow(false)
    val busy = moving.asStateFlow()
    private val failure = MutableStateFlow<UiMessage?>(null)
    val error = failure.asStateFlow()
    private var retained: (suspend () -> Unit)? = null

    fun dismissFailure() {
        if (moving.value) return
        retained = null
        failure.value = null
    }

    fun retry() {
        if (failure.value == null) return
        val action = retryMove ?: retained ?: return
        execute(action)
    }

    fun move(
        position: Long,
        songId: String,
        destination: Int,
    ) {
        if (failure.value != null) return
        val action: suspend () -> Unit = { move.invoke(position, songId, destination) }
        execute(action)
    }

    private fun execute(action: suspend () -> Unit) {
        if (moving.value) return
        retained = action
        moving.value = true
        scope.launch {
            try {
                action()
                failure.value = null
                retained = null
                showMessage(UiMessage(R.string.playlist_song_moved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                this@PlaylistEntryMover.failure.value = playlistEditMessage(failure)
                showMessage(playlistEditMessage(failure))
            } finally {
                moving.value = false
            }
        }
    }
}
