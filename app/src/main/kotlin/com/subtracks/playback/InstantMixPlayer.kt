package com.subtracks.playback

import com.subtracks.data.mix.InstantMix
import com.subtracks.data.mix.MixSeed

/**
 * Starts an Instant Mix (or an artist station: an Instant Mix of the artist) on the active server.
 * Offline it uses the cached answer or the local fallback, from downloaded songs only.
 */
class InstantMixPlayer internal constructor(
    private val mix: InstantMix,
    private val activeSourceId: suspend () -> Long?,
    private val downloadedOnly: () -> Boolean,
    private val start: (sourceId: Long, songIds: List<String>) -> Unit,
) {
    /** False when there is nothing to play: no server, or nothing at all to mix. */
    suspend fun play(seed: MixSeed): Boolean {
        val sourceId = activeSourceId() ?: return false
        val songIds = mix.build(sourceId, seed, online = !downloadedOnly()).songIds
        if (songIds.isEmpty()) return false
        start(sourceId, songIds)
        return true
    }

    companion object {
        internal fun of(
            mix: InstantMix,
            activeSourceId: suspend () -> Long?,
            downloadedOnly: () -> Boolean,
            controller: PlaybackController,
        ) = InstantMixPlayer(mix, activeSourceId, downloadedOnly) { sourceId, songIds ->
            controller.playMix(sourceId, songIds, QueueCollection.InstantMix)
        }
    }
}
