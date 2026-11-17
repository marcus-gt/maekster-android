package com.subtracks.playback

import com.subtracks.data.audiomuse.AudioMusePathEnd
import com.subtracks.data.audiomuse.SongPathBuilder
import com.subtracks.data.audiomuse.SongPathResult
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException

/** Plays a Song Path on the active server as a finite queue: neither autoplay nor any refill extends it. */
class SongPathPlayer internal constructor(
    private val build: suspend (Long, AudioMusePathEnd, AudioMusePathEnd, Int) -> SongPathResult,
    private val activeSourceId: suspend () -> Long?,
    private val playbackIntent: () -> Long = { 0L },
    private val start: (sourceId: Long, songIds: List<String>) -> Unit,
) {
    /** Starts playback only for [SongPathResult.Ready]; a source switch while building starts nothing. */
    suspend fun play(
        from: AudioMusePathEnd,
        to: AudioMusePathEnd,
        length: Int,
        expectedSourceId: Long? = null,
    ): SongPathResult {
        val intent = playbackIntent()
        val sourceId = activeSourceId() ?: return SongPathResult.Unavailable
        if (expectedSourceId != null && sourceId != expectedSourceId) return SongPathResult.Unavailable
        val result = build(sourceId, from, to, length)
        if (result is SongPathResult.Ready) {
            if (activeSourceId() != sourceId || playbackIntent() != intent) {
                throw CancellationException("Song Path request superseded")
            }
            Log.i("mix", "song path: ${result.songIds.size} songs")
            start(sourceId, result.songIds)
        }
        return result
    }

    companion object {
        internal fun of(
            builder: SongPathBuilder,
            activeSourceId: suspend () -> Long?,
            controller: PlaybackController,
        ) = SongPathPlayer(builder::build, activeSourceId, controller::currentPlaybackIntent) { sourceId, songIds ->
            controller.playMix(sourceId, songIds, QueueCollection.SongPath)
        }
    }
}
