package com.subtracks.data.audiomuse

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ln
import kotlin.random.Random

/** Discover mood radios. CLAP queries stay in English; only the titles shown are localized. */
enum class RadioMood(
    val key: String,
    val query: String,
) {
    NIGHT("night", "late night calm atmospheric"),
    ENERGETIC("energetic", "energetic upbeat high energy"),
    WORKOUT("workout", "intense driving workout rhythm"),
    CHILL("chill", "relaxed mellow laid back"),
    FOCUS("focus", "focus instrumental background"),
}

sealed interface MoodRadioResult {
    /** Distinct local songs, best AudioMuse matches tending to lead. */
    data class Ready(
        val songIds: List<String>,
    ) : MoodRadioResult

    /** AudioMuse answered with no matches at all. */
    data object NoMatches : MoodRadioResult

    /** AudioMuse matched songs, but none of them resolve to a playable song in this library. */
    data object NotInLibrary : MoodRadioResult

    /** Wrong or no active source, offline, AudioMuse not configured, or its token unreadable. */
    data object Unavailable : MoodRadioResult
}

/**
 * Builds a mood radio on explicit request: one AudioMuse sonic search, resolved against the local mirror, then a
 * rank-weighted shuffle so each start differs while the best matches still tend to come first. Nothing is cached:
 * a radio is meant to be fresh each time and is not offered offline. Source, connectivity or configuration
 * changes cancel the request.
 */
class MoodRadioBuilder(
    private val configuration: AudioMuseConfiguration,
    private val resolver: AudioMuseTrackResolver,
    private val access: StateFlow<AudioMuseScoreAccess>,
    private val client: (AudioMuseConnection) -> AudioMuseClient = { OkHttpAudioMuseClient(it.url, it.token) },
    private val random: Random = Random.Default,
) {
    suspend fun build(
        sourceId: Long,
        mood: RadioMood,
    ): MoodRadioResult =
        guardedAudioMuseRequest(configuration, access, client, sourceId) { api ->
            api.warmup()
            val matches = api.search(mood.query, POOL_SIZE).take(AudioMuseTrackResolver.MAX_BATCH)
            if (matches.isEmpty()) {
                MoodRadioResult.NoMatches
            } else {
                val songIds = resolver.resolve(sourceId, matches).songIds.distinct()
                if (songIds.isEmpty()) MoodRadioResult.NotInLibrary else MoodRadioResult.Ready(rankWeightedShuffle(songIds, random))
            }
        } ?: MoodRadioResult.Unavailable

    companion object {
        const val POOL_SIZE = 200

        /**
         * Efraimidis–Spirakis weighted shuffle with weight `size - rank`: earlier (better) matches tend to lead,
         * but any match can turn up anywhere.
         */
        internal fun <T> rankWeightedShuffle(
            ranked: List<T>,
            random: Random,
        ): List<T> =
            ranked
                .mapIndexed { index, item -> item to ln(1.0 - random.nextDouble()) / (ranked.size - index) }
                .sortedByDescending { it.second }
                .map { it.first }
    }
}
