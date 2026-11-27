package com.subtracks.playback

import com.subtracks.data.mix.MoodShortfall
import com.subtracks.data.mix.QueueBuilderBuild
import com.subtracks.data.mix.QueueBuilderMoodBuilder
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.log.Log

/** What a Queue Builder build did: [started] with its songs, and why picked moods fell short, if they did. */
data class QueueBuilderOutcome(
    val started: Boolean,
    val moodShortfall: MoodShortfall? = null,
)

/**
 * Builds a Queue Builder queue on the active server (downloaded songs only when offline) and plays it.
 * The UI calls [play] and explains an empty or mood-limited result.
 */
class QueueBuilderPlayer internal constructor(
    private val build: suspend (Long, QueueBuilderSelection, Int, Boolean) -> QueueBuilderBuild,
    private val activeSourceId: suspend () -> Long?,
    private val downloadedOnly: () -> Boolean,
    private val start: (sourceId: Long, songIds: List<String>) -> Unit,
    /** Remembers what a started queue was built from, so the widget's Play can continue it while the picks match. */
    private val rememberBuilt: suspend (sourceId: Long, built: QueueBuilderPicks) -> Unit = { _, _ -> },
) {
    /** Not started when nothing matches: no server, no matching songs, none downloaded offline, or no mood scores. */
    suspend fun play(
        selection: QueueBuilderSelection,
        length: Int,
    ): QueueBuilderOutcome {
        val sourceId = activeSourceId() ?: return QueueBuilderOutcome(false)
        val offline = downloadedOnly()
        val result = build(sourceId, selection, length, offline)
        Log.i("mix", "queue builder: ${result.songIds.size} songs${if (offline) " (downloads only)" else ""}")
        if (result.songIds.isEmpty() || activeSourceId() != sourceId) return QueueBuilderOutcome(false, result.moodShortfall)
        start(sourceId, result.songIds)
        rememberBuilt(sourceId, QueueBuilderPicks(selection, length))
        return QueueBuilderOutcome(true, result.moodShortfall)
    }

    companion object {
        internal fun of(
            builder: QueueBuilderMoodBuilder,
            activeSourceId: suspend () -> Long?,
            downloadedOnly: () -> Boolean,
            controller: PlaybackController,
            rememberBuilt: suspend (sourceId: Long, built: QueueBuilderPicks) -> Unit = { _, _ -> },
        ) = QueueBuilderPlayer(
            { sourceId, selection, count, offline -> builder.build(sourceId, selection, count, offline) },
            activeSourceId,
            downloadedOnly,
            { sourceId, songIds -> controller.playMix(sourceId, songIds, QueueCollection.QueueBuilder) },
            rememberBuilt,
        )
    }
}
