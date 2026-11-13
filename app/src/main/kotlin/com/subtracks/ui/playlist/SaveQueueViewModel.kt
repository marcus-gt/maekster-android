package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.repo.PlaylistEditException
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.QueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaveQueueUi(
    val name: String = "",
    val busy: Boolean = false,
    val error: UiMessage? = null,
    /** The queue has been captured and a retry finishes that capture; the name is fixed until it is discarded. */
    val retained: Boolean = false,
) {
    val canSave: Boolean get() = !busy && (retained || name.isNotBlank())
}

/**
 * Saves the current queue as a new playlist. The queue is captured when Save is first pressed; after a failure the
 * capture is kept, so Retry finishes the same playlist even if playback has moved on. Closing the dialog discards it.
 */
class SaveQueueViewModel(
    private val queue: QueueRepository,
    private val playlists: PlaylistRepository,
    private val showMessage: (UiMessage) -> Unit,
    private val cleanup: CoroutineScope,
) : ViewModel() {
    private val state = MutableStateFlow(SaveQueueUi())
    val ui: StateFlow<SaveQueueUi> = state.asStateFlow()

    private val finished = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the playlist holds the whole queue and the mirror shows it. */
    val done: Flow<Unit> = finished.receiveAsFlow()

    private var draftId: Long? = null

    fun setName(name: String) = state.update { if (it.retained) it else it.copy(name = name, error = null) }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        val name = current.name.trim()
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val draft = draftId ?: capture(name) ?: return@launch
                draftId = draft
                state.update { it.copy(retained = true) }
                playlists.saveDraft(draft)
                draftId = null
                showMessage(UiMessage(R.string.queue_saved_as_playlist, listOf(name)))
                finished.send(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PlaylistEditException) {
                if (failure.reason == PlaylistEditException.Reason.Changed) {
                    discard()
                    state.update { it.copy(retained = false, error = UiMessage(R.string.save_queue_changed)) }
                } else {
                    state.update { it.copy(error = playlistEditMessage(failure)) }
                }
            } catch (failure: Exception) {
                state.update { it.copy(error = playlistEditMessage(failure)) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    /** Captures the queue as a draft, or reports why it cannot be saved and returns null. */
    private suspend fun capture(name: String): Long? {
        val snapshot = queue.snapshot()
        val sources = (snapshot.entries + snapshot.upNext).map { it.entry.sourceId }.toSet()
        val problem =
            when {
                snapshot.size == 0L -> R.string.save_queue_empty
                sources.size > 1 -> R.string.save_queue_several_servers
                else -> null
            }
        if (problem != null) {
            state.update { it.copy(error = UiMessage(problem)) }
            return null
        }
        val sourceId = sources.single()
        return try {
            playlists.stageSave(sourceId, name, queue.playlistBatches(snapshot, sourceId))
        } catch (failure: RuntimeException) {
            // The export's checks fail when the queue or library changes mid-capture; cancellation is an ISE too.
            if (failure is CancellationException || (failure !is IllegalStateException && failure !is IllegalArgumentException)) {
                throw failure
            }
            state.update { it.copy(error = UiMessage(R.string.save_queue_capture_failed)) }
            null
        }
    }

    private fun discard() {
        val draft = draftId ?: return
        draftId = null
        cleanup.launch { playlists.discardSave(draft) }
    }

    override fun onCleared() = discard()
}
