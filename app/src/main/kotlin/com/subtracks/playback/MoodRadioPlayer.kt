package com.subtracks.playback

import com.subtracks.data.audiomuse.MoodRadioBuilder
import com.subtracks.data.audiomuse.MoodRadioResult
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException

/**
 * Starts a Discover mood radio on the active server. The radio is labelled by its mood and stays endless: once its
 * matches run low, autoplay (when on) carries on with similar songs.
 */
class MoodRadioPlayer internal constructor(
    private val build: suspend (Long, RadioMood) -> MoodRadioResult,
    private val activeSourceId: suspend () -> Long?,
    private val start: (sourceId: Long, songIds: List<String>, mood: RadioMood) -> Unit,
) {
    /** Starts playback only for [MoodRadioResult.Ready]; every other result explains why nothing started. */
    suspend fun play(mood: RadioMood): MoodRadioResult {
        val sourceId = activeSourceId() ?: return MoodRadioResult.Unavailable
        val result = build(sourceId, mood)
        if (result is MoodRadioResult.Ready) {
            if (activeSourceId() != sourceId) throw CancellationException("Active source changed")
            Log.i("mix", "mood radio ${mood.key}: ${result.songIds.size} songs")
            start(sourceId, result.songIds, mood)
        }
        return result
    }

    companion object {
        internal fun of(
            builder: MoodRadioBuilder,
            activeSourceId: suspend () -> Long?,
            controller: PlaybackController,
        ) = MoodRadioPlayer(builder::build, activeSourceId) { sourceId, songIds, mood ->
            controller.playMix(sourceId, songIds, QueueCollection.forMood(mood))
        }
    }
}
