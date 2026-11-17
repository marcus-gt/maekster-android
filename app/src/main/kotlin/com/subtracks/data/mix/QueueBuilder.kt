package com.subtracks.data.mix

import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.db.GenreSpelling
import com.subtracks.data.db.MixDao

/**
 * What the listener picked: whole main genres ([mains]), single sub-genres ([tags], by [QueueBuilderPill.key])
 * and [decades] (their first year, 1990 for the 1990s). Any picked genre and any picked decade match; nothing
 * picked in a group matches everything in it. [moods] never widen or narrow that match: they rank the matching
 * songs by their AudioMuse scores (see `QueueBuilderMoodBuilder`).
 */
data class QueueBuilderSelection(
    val mains: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val moods: Set<QueueBuilderMood> = emptySet(),
) {
    val hasGenre: Boolean get() = mains.isNotEmpty() || tags.isNotEmpty()

    val isEmpty: Boolean get() = !hasGenre && decades.isEmpty() && moods.isEmpty()
}

/** The remembered picks and queue length (15 to 120 songs in steps of 5). */
data class QueueBuilderPicks(
    val selection: QueueBuilderSelection = QueueBuilderSelection(),
    val length: Int = DEFAULT_LENGTH,
) {
    companion object {
        const val MIN_LENGTH = 15
        const val MAX_LENGTH = 120
        const val LENGTH_STEP = 5
        const val DEFAULT_LENGTH = 50

        fun clampLength(length: Int): Int {
            val clamped = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
            return MIN_LENGTH + (clamped - MIN_LENGTH + LENGTH_STEP / 2) / LENGTH_STEP * LENGTH_STEP
        }
    }
}

/** A sub-genre pill: every library spelling that resolves to one style or sub-genre, and their songs. */
data class QueueBuilderPill(
    val key: String,
    val name: String,
    val songCount: Long,
)

/** A main genre with the distinct songs carrying any of its tags, and its sub-genres (biggest first). */
data class QueueBuilderColumn(
    val main: String,
    val songCount: Long,
    val pills: List<QueueBuilderPill>,
)

/** The genre columns (biggest first, [GenreTaxonomy.OTHER] last) and decades with song counts. */
data class QueueBuilderIndex(
    val columns: List<QueueBuilderColumn> = emptyList(),
    val decades: Map<Int, Long> = emptyMap(),
) {
    fun column(main: String): QueueBuilderColumn? = columns.firstOrNull { it.main == main }
}

/**
 * [selection] without picks the index no longer offers (a genre retagged away, a decade without songs, or
 * genres without downloads offline), so a remembered pick never silently narrows a build to nothing.
 */
fun QueueBuilderIndex.available(
    selection: QueueBuilderSelection,
    moodsAvailable: Boolean = false,
): QueueBuilderSelection {
    val pills = columns.flatMapTo(mutableSetOf()) { column -> column.pills.map { it.key } }
    return QueueBuilderSelection(
        mains = selection.mains.filterTo(mutableSetOf()) { main -> columns.any { it.main == main } },
        tags = selection.tags.filterTo(mutableSetOf()) { it in pills },
        decades = selection.decades.filterTo(mutableSetOf()) { it in decades },
        moods = if (moodsAvailable) selection.moods else emptySet(),
    )
}

/**
 * Builds Queue Builder queues from the local mirror alone, so it works offline (downloads only). Library genre
 * tags are grouped by [taxonomy] into main genres and sub-genres. Every read is a bounded, aggregated or random
 * query; no table is loaded whole.
 */
class QueueBuilder(
    private val dao: MixDao,
    private val taxonomy: GenreTaxonomy = GenreTaxonomy.DISCOGS,
) {
    suspend fun index(
        sourceId: Long,
        downloadedOnly: Boolean,
    ): QueueBuilderIndex {
        val spellings = resolvedSpellings(sourceId, downloadedOnly)
        val columns =
            spellings
                .flatMap { spelling -> spelling.resolved.mains.map { it to spelling } }
                .groupBy({ it.first }, { it.second })
                .map { (main, members) ->
                    val pills =
                        members
                            .filterNot { it.resolved.isMainTag }
                            .groupBy { it.resolved.key }
                            .map { (key, variants) ->
                                // Summed per spelling: a song tagged with two variants of one pill counts twice here only.
                                QueueBuilderPill(key, pillName(variants), variants.sumOf { it.row.songCount })
                            }.sortedWith(compareByDescending<QueueBuilderPill> { it.songCount }.thenBy { it.name.lowercase() })
                    // Exact: a song with two tags of one main genre counts once for it.
                    val songCount = dao.songsWithGenres(sourceId, members.map { it.row.genre }, downloadedOnly)
                    QueueBuilderColumn(main, songCount, pills)
                }.sortedWith(
                    compareBy<QueueBuilderColumn> { it.main == GenreTaxonomy.OTHER }
                        .thenByDescending { it.songCount }
                        .thenBy { it.main },
                )
        val decades = dao.decadeCounts(sourceId, downloadedOnly).associate { it.decade to it.songCount }
        return QueueBuilderIndex(columns, decades)
    }

    /** How many songs [selection] matches. */
    suspend fun matchCount(
        sourceId: Long,
        selection: QueueBuilderSelection,
        downloadedOnly: Boolean,
    ): Long =
        dao.builderMatchCount(
            sourceId = sourceId,
            anyGenre = !selection.hasGenre,
            genres = matchingSpellings(sourceId, selection, downloadedOnly),
            anyDecade = selection.decades.isEmpty(),
            decades = selection.decades.toList(),
            downloadedOnly = downloadedOnly,
        )

    /** Up to [count] random matching song ids, each once, skipping [excluding] (songs already queued). */
    suspend fun build(
        sourceId: Long,
        selection: QueueBuilderSelection,
        count: Int,
        downloadedOnly: Boolean,
        excluding: Collection<String> = emptyList(),
    ): List<String> {
        require(count in 1..MAX_COUNT) { "A Queue Builder batch holds 1 to $MAX_COUNT songs" }
        require(excluding.size <= MAX_EXCLUDED) { "At most $MAX_EXCLUDED songs can be excluded" }
        return dao.builderSongs(
            sourceId = sourceId,
            anyGenre = !selection.hasGenre,
            genres = matchingSpellings(sourceId, selection, downloadedOnly),
            anyDecade = selection.decades.isEmpty(),
            decades = selection.decades.toList(),
            excluded = excluding.distinct(),
            downloadedOnly = downloadedOnly,
            limit = count,
        )
    }

    private data class Spelling(
        val row: GenreSpelling,
        val resolved: ResolvedGenre,
    )

    private suspend fun resolvedSpellings(
        sourceId: Long,
        downloadedOnly: Boolean,
    ): List<Spelling> =
        dao
            .genreSpellings(sourceId, downloadedOnly)
            .filter { it.genre.isNotBlank() && GenreTaxonomy.normalize(it.genre).isNotEmpty() }
            .map { Spelling(it, taxonomy.resolve(it.genre)) }

    /** The stored spellings behind the picks, so the match can use the genre index. */
    private suspend fun matchingSpellings(
        sourceId: Long,
        selection: QueueBuilderSelection,
        downloadedOnly: Boolean,
    ): List<String> {
        if (!selection.hasGenre) return emptyList()
        return resolvedSpellings(sourceId, downloadedOnly)
            .filter { it.resolved.key in selection.tags || it.resolved.mains.any { main -> main in selection.mains } }
            .map { it.row.genre }
    }

    // The Discogs style name when there is one; otherwise the most used library spelling, ties alphabetical.
    private fun pillName(variants: List<Spelling>): String =
        variants.firstNotNullOfOrNull { it.resolved.styleName }
            ?: variants
                .sortedWith(compareByDescending<Spelling> { it.row.uses }.thenBy { it.row.genre.trim() })
                .first()
                .row.genre
                .trim()

    companion object {
        // Mood builds draw a candidate pool of up to 400 songs to rank (QueueBuilderMoodBuilder.poolSize).
        const val MAX_COUNT = 400
        const val MAX_EXCLUDED = 2_000
    }
}
