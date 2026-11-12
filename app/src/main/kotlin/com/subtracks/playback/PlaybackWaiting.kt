package com.subtracks.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Why playback that should be running is not, shown only once the wait outlasts [PLAYBACK_WAITING_DELAY_MS]. */
enum class PlaybackWaiting { Preparing, Buffering, Reconnecting }

/** As on iOS: shorter waits (a track change, a brief rebuffer) show nothing, so the controls do not flicker. */
internal const val PLAYBACK_WAITING_DELAY_MS = 700L

/**
 * Debounces waiting reasons. A wait for one [key] (the playing source and queue position) is one episode: the
 * reason may change within it without restarting the delay, and a new key starts a new delay.
 */
internal class PlaybackWaitingIndicator(
    private val scope: CoroutineScope,
    private val onChange: () -> Unit,
) {
    var value: PlaybackWaiting? = null
        private set
    private var requested: PlaybackWaiting? = null
    private var key: Any? = null
    private var job: Job? = null

    fun update(
        reason: PlaybackWaiting?,
        key: Any?,
    ) {
        if (requested == reason && this.key == key) return
        val newEpisode = requested == null || this.key != key
        requested = reason
        this.key = key
        if (reason == null) {
            job?.cancel()
            job = null
            publish(null)
        } else if (newEpisode) {
            job?.cancel()
            publish(null)
            job =
                scope.launch {
                    delay(PLAYBACK_WAITING_DELAY_MS)
                    publish(requested)
                }
        } else if (value != null) {
            publish(reason)
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        requested = null
        key = null
        value = null
    }

    private fun publish(reason: PlaybackWaiting?) {
        if (value == reason) return
        value = reason
        onChange()
    }
}
