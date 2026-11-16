package com.subtracks.playback

import com.subtracks.data.mix.QueueBuilder
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.log.Log

/**
 * Builds a Queue Builder queue on the active server (downloaded songs only when offline) and plays it.
 * The UI calls [play] and explains an empty result.
 */
class QueueBuilderPlayer internal constructor(
    private val builder: QueueBuilder,
    private val activeSourceId: suspend () -> Long?,
    private val downloadedOnly: () -> Boolean,
    private val start: (sourceId: Long, songIds: List<String>) -> Unit,
) {
    /** False when nothing matches: no server, no matching songs, or none downloaded while offline. */
    suspend fun play(
        selection: QueueBuilderSelection,
        length: Int,
    ): Boolean {
        val sourceId = activeSourceId() ?: return false
        val offline = downloadedOnly()
        val songIds = builder.build(sourceId, selection, length, downloadedOnly = offline)
        Log.i("mix", "queue builder: ${songIds.size} songs${if (offline) " (downloads only)" else ""}")
        if (songIds.isEmpty() || activeSourceId() != sourceId) return false
        start(sourceId, songIds)
        return true
    }

    companion object {
        internal fun of(
            builder: QueueBuilder,
            activeSourceId: suspend () -> Long?,
            downloadedOnly: () -> Boolean,
            controller: PlaybackController,
        ) = QueueBuilderPlayer(builder, activeSourceId, downloadedOnly) { sourceId, songIds ->
            controller.playMix(sourceId, songIds, QueueCollection.QueueBuilder)
        }
    }
}
