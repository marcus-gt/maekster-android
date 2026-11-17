package com.subtracks.data.audiomuse

import kotlinx.coroutines.flow.StateFlow

sealed interface SongPathResult {
    /** Selected song endpoints with local AudioMuse steps between them, each song once. */
    data class Ready(
        val songIds: List<String>,
    ) : SongPathResult

    /** Both ends are moods, or the same song. */
    data object InvalidEnds : SongPathResult

    /** No usable AudioMuse steps, or a selected endpoint is no longer in the local library. */
    data object NoPath : SongPathResult

    /** Wrong or no active source, offline, AudioMuse not configured, or its token unreadable. */
    data object Unavailable : SongPathResult
}

/**
 * Song Path: AudioMuse's smooth sequence from one song or mood to another, played as a finite queue. Built only on
 * explicit request and not cached: like a mood radio it needs AudioMuse, so it is not offered offline.
 */
class SongPathBuilder(
    private val configuration: AudioMuseConfiguration,
    private val resolver: AudioMuseTrackResolver,
    private val access: StateFlow<AudioMuseScoreAccess>,
    private val client: (AudioMuseConnection) -> AudioMuseClient = { OkHttpAudioMuseClient(it.url, it.token) },
) {
    suspend fun build(
        sourceId: Long,
        start: AudioMusePathEnd,
        end: AudioMusePathEnd,
        length: Int,
    ): SongPathResult {
        if (!canBuild(start, end)) return SongPathResult.InvalidEnds
        val targetLength = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val startId = (start as? AudioMusePathEnd.Song)?.id
        val endId = (end as? AudioMusePathEnd.Song)?.id
        val endpoints = listOfNotNull(startId, endId)
        return guardedAudioMuseRequest(configuration, access, client, sourceId) { api ->
            val steps =
                try {
                    api.findPath(start, end, targetLength)
                } catch (failure: AudioMuseHttpException) {
                    if (failure.failure == AudioMuseFailure.NOT_FOUND) return@guardedAudioMuseRequest SongPathResult.NoPath
                    throw failure
                }
            if (steps.isEmpty()) return@guardedAudioMuseRequest SongPathResult.NoPath
            val songIds = resolver.resolve(sourceId, steps.take(AudioMuseTrackResolver.MAX_BATCH)).songIds
            if (songIds.isEmpty() || !resolver.containsSongs(sourceId, endpoints)) {
                return@guardedAudioMuseRequest SongPathResult.NoPath
            }

            // The picker already identified the exact local endpoint IDs. AudioMuse can omit them or return different tags.
            val middle = songIds.distinct().filterNot { it in endpoints }.take(targetLength - endpoints.size)
            SongPathResult.Ready(listOfNotNull(startId) + middle + listOfNotNull(endId))
        } ?: SongPathResult.Unavailable
    }

    companion object {
        // iOS SongPathService: 5 to 50 songs, 25 by default.
        const val MIN_LENGTH = 5
        const val MAX_LENGTH = 50
        const val DEFAULT_LENGTH = 25

        /** At most one end may be a mood, and the two ends may not be the same song. */
        fun canBuild(
            start: AudioMusePathEnd?,
            end: AudioMusePathEnd?,
        ): Boolean =
            when {
                start == null || end == null -> {
                    false
                }

                start is AudioMusePathEnd.Mood && end is AudioMusePathEnd.Mood -> {
                    false
                }

                start is AudioMusePathEnd.Song && end is AudioMusePathEnd.Song -> {
                    start.id != end.id && start.id.isNotBlank() &&
                        end.id.isNotBlank()
                }

                else -> {
                    (start as? AudioMusePathEnd.Song ?: end as? AudioMusePathEnd.Song)?.id?.isNotBlank() == true
                }
            }
    }
}
