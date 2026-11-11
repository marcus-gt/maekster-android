package com.subtracks

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The app-wide queue of short messages for the user (errors, confirmations). The app shell shows each one
 * as a snackbar. Messages sent while no screen is collecting wait in the queue and appear once one does,
 * so a repository can report from any thread without knowing whether the UI is up.
 */
class UiMessages {
    private val queue = Channel<UiMessage>(capacity = MAX_PENDING, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each message is delivered once, to one collector, in the order it was sent. */
    val messages: Flow<UiMessage> = queue.receiveAsFlow()

    fun show(message: UiMessage) {
        queue.trySend(message)
    }

    private companion object {
        const val MAX_PENDING = 16
    }
}
