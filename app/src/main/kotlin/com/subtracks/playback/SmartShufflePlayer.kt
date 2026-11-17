package com.subtracks.playback

import com.subtracks.data.mix.SmartShuffle
import com.subtracks.log.Log

/**
 * Starts Smart Shuffle on the active server: builds the mix from the mirror (downloaded songs only when
 * offline) and plays it. The UI calls [play] and explains an empty result.
 */
class SmartShufflePlayer internal constructor(
    private val mix: SmartShuffle,
    private val activeSourceId: suspend () -> Long?,
    private val downloadedOnly: () -> Boolean,
    private val start: (sourceId: Long, songIds: List<String>) -> Unit,
) {
    /** False when there is nothing to play: no server, an empty library, or no downloads while offline. */
    suspend fun play(): Boolean {
        val sourceId = activeSourceId() ?: return false
        val offline = downloadedOnly()
        val songIds = mix.build(sourceId, downloadedOnly = offline)
        Log.i("mix", "smart shuffle: ${songIds.size} songs${if (offline) " (downloads only)" else ""}")
        if (songIds.isEmpty() || activeSourceId() != sourceId) return false
        start(sourceId, songIds)
        return true
    }

    companion object {
        internal fun of(
            mix: SmartShuffle,
            activeSourceId: suspend () -> Long?,
            downloadedOnly: () -> Boolean,
            controller: PlaybackController,
        ) = SmartShufflePlayer(mix, activeSourceId, downloadedOnly) { sourceId, songIds ->
            controller.playMix(sourceId, songIds, QueueCollection.SmartShuffle)
        }
    }
}
