package com.subtracks.playback

import com.subtracks.data.mix.InstantMix
import com.subtracks.data.mix.MixSeed

/**
 * Starts an Instant Mix (or an artist radio: an Instant Mix of the artist) on the active server.
 * Offline it uses the cached answer or the local fallback, from downloaded songs only. An artist radio is
 * labelled "<artist> radio", whether it was started from the artist's menu or from Discover.
 */
class InstantMixPlayer internal constructor(
    private val mix: InstantMix,
    private val activeSourceId: suspend () -> Long?,
    private val downloadedOnly: () -> Boolean,
    private val artistName: suspend (sourceId: Long, artistId: String) -> String? = { _, _ -> null },
    private val start: (sourceId: Long, songIds: List<String>, sourceLabel: String) -> Unit,
) {
    /** False when there is nothing to play: no server, or nothing at all to mix. */
    suspend fun play(
        seed: MixSeed,
        expectedSourceId: Long? = null,
    ): Boolean {
        val sourceId = activeSourceId() ?: return false
        if (expectedSourceId != null && sourceId != expectedSourceId) return false
        val songIds = mix.build(sourceId, seed, online = !downloadedOnly()).songIds
        if (songIds.isEmpty() || activeSourceId() != sourceId) return false
        start(sourceId, songIds, label(sourceId, seed))
        return true
    }

    /** The artist's radio, as Discover's Artist radio tiles start it: the same mix as the artist's menu. */
    suspend fun playArtistRadio(
        sourceId: Long,
        artistId: String,
    ): Boolean = play(MixSeed.Artist(artistId), expectedSourceId = sourceId)

    private suspend fun label(
        sourceId: Long,
        seed: MixSeed,
    ): String =
        when (seed) {
            is MixSeed.Artist -> QueueCollection.artistRadioLabel(artistName(sourceId, seed.id))
            else -> QueueCollection.InstantMix.storedLabel
        }

    companion object {
        internal fun of(
            mix: InstantMix,
            activeSourceId: suspend () -> Long?,
            downloadedOnly: () -> Boolean,
            artistName: suspend (sourceId: Long, artistId: String) -> String?,
            controller: PlaybackController,
        ) = InstantMixPlayer(mix, activeSourceId, downloadedOnly, artistName) { sourceId, songIds, label ->
            val collection = QueueCollection.fromStored(label) ?: QueueCollection.InstantMix
            controller.playMix(sourceId, songIds, collection, label)
        }
    }
}
