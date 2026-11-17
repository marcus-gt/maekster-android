package com.subtracks.data.mix

import com.subtracks.data.audiomuse.AudioMuseScoreBatch
import com.subtracks.data.audiomuse.rankMoodCandidates
import kotlin.random.Random

/** Why picked moods could not be fully honoured. Moods are never silently dropped. */
enum class MoodShortfall {
    /** No song could be ranked because AudioMuse scores were unavailable (offline cache misses, AudioMuse unreachable or not set up). */
    SCORES_UNAVAILABLE,

    /** Scores were there, but none of the candidates had a score for every picked mood (not analysed). */
    NO_MOOD_MATCH,

    /** A queue was built, but shorter than asked because some candidates could not be scored. */
    PARTIAL,
}

data class QueueBuilderBuild(
    val songIds: List<String>,
    val moodShortfall: MoodShortfall? = null,
)

/**
 * The Queue Builder build used by both Build and refill, so a mood queue refills with the same picks. Without moods
 * it is [QueueBuilder.build]. With moods it draws a random pool of genre/decade matches, ranks it by the mean of the
 * picked mood scores (songs missing any picked score are left out), keeps the best [count] and shuffles them.
 * [scores] is the guarded AudioMuse fetcher: cached scores are reused, and offline only cached scores count.
 */
class QueueBuilderMoodBuilder(
    internal val builder: QueueBuilder,
    private val scores: suspend (Long, List<String>) -> AudioMuseScoreBatch,
    private val random: Random = Random.Default,
) {
    suspend fun build(
        sourceId: Long,
        selection: QueueBuilderSelection,
        count: Int,
        downloadedOnly: Boolean,
        excluding: Collection<String> = emptyList(),
    ): QueueBuilderBuild {
        if (selection.moods.isEmpty()) {
            return QueueBuilderBuild(builder.build(sourceId, selection, count, downloadedOnly, excluding))
        }
        val pool = builder.build(sourceId, selection, poolSize(count), downloadedOnly, excluding)
        if (pool.isEmpty()) return QueueBuilderBuild(emptyList())
        val batch = scores(sourceId, pool)
        val ranked = rankMoodCandidates(pool, batch.scores, selection.moods, count).shuffled(random)
        val unscored = batch.unavailable.isNotEmpty() || batch.failed.isNotEmpty()
        val shortfall =
            when {
                ranked.isEmpty() && unscored -> MoodShortfall.SCORES_UNAVAILABLE
                ranked.isEmpty() -> MoodShortfall.NO_MOOD_MATCH
                unscored && ranked.size < minOf(count, pool.size) -> MoodShortfall.PARTIAL
                else -> null
            }
        return QueueBuilderBuild(ranked, shortfall)
    }

    /**
     * More songs for a playing Queue Builder queue: the server's saved picks as the picker currently shows them
     * (limited to what the library offers; moods only while AudioMuse is usable), built exactly as Build does.
     */
    class Refill(
        private val moodBuilder: QueueBuilderMoodBuilder,
        private val picks: suspend (Long) -> QueueBuilderPicks,
        private val moodsAvailable: suspend (Long) -> Boolean,
    ) {
        suspend operator fun invoke(
            sourceId: Long,
            excluded: Set<String>,
            count: Int,
            downloadedOnly: Boolean,
        ): List<String> {
            val saved = picks(sourceId).selection
            val selection = moodBuilder.builder.index(sourceId, downloadedOnly).available(saved, moodsAvailable(sourceId))
            return moodBuilder.build(sourceId, selection, count, downloadedOnly, excluding = excluded).songIds
        }
    }

    companion object {
        /** Following iOS: four times the queue, at least 120 and at most 400 candidates. */
        fun poolSize(count: Int): Int = (count * 4).coerceIn(120, QueueBuilder.MAX_COUNT)
    }
}
