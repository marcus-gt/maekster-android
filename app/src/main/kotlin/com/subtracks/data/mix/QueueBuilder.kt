package com.subtracks.data.mix

import com.subtracks.data.db.GenreSpelling
import com.subtracks.data.db.MixDao

/**
 * What the listener picked: any of [genres] (by [QueueBuilderGenre.key]) and any of [decades] (their first
 * year, 1990 for the 1990s). Nothing picked in a group matches everything in it.
 */
data class QueueBuilderSelection(
    val genres: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
)

/** One genre as the library tags it, however it is spelled or cased, and the songs carrying it. */
data class QueueBuilderGenre(
    val key: String,
    val name: String,
    val songCount: Long,
)

/** The genres (biggest first) and decades (oldest first) to pick from, with song counts. */
data class QueueBuilderIndex(
    val genres: List<QueueBuilderGenre> = emptyList(),
    val decades: Map<Int, Long> = emptyMap(),
)

/**
 * Builds Queue Builder queues from the local mirror alone, so it works offline (downloads only). Every read is
 * a bounded, aggregated or random query; no table is loaded whole.
 */
class QueueBuilder(
    private val dao: MixDao,
) {
    suspend fun index(
        sourceId: Long,
        downloadedOnly: Boolean,
    ): QueueBuilderIndex {
        val genres =
            dao
                .genreSpellings(sourceId, downloadedOnly)
                .filter { it.genre.isNotBlank() }
                .groupBy { genreKey(it.genre) }
                .map { (key, spellings) ->
                    // The most used spelling names the genre; ties go alphabetically for a stable name.
                    val name =
                        spellings
                            .sortedWith(compareByDescending<GenreSpelling> { it.uses }.thenBy { it.genre.trim() })
                            .first()
                            .genre
                            .trim()
                    // Summed per spelling: a song tagged with two spellings of one genre counts twice here only.
                    QueueBuilderGenre(key, name, spellings.sumOf { it.songCount })
                }.sortedWith(compareByDescending<QueueBuilderGenre> { it.songCount }.thenBy { it.name.lowercase() })
        val decades = dao.decadeCounts(sourceId, downloadedOnly).associate { it.decade to it.songCount }
        return QueueBuilderIndex(genres, decades)
    }

    /** How many songs [selection] matches. */
    suspend fun matchCount(
        sourceId: Long,
        selection: QueueBuilderSelection,
        downloadedOnly: Boolean,
    ): Long {
        val genres = spellings(sourceId, selection, downloadedOnly)
        return dao.builderMatchCount(
            sourceId = sourceId,
            anyGenre = selection.genres.isEmpty(),
            genres = genres,
            anyDecade = selection.decades.isEmpty(),
            decades = selection.decades.toList(),
            downloadedOnly = downloadedOnly,
        )
    }

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
        val genres = spellings(sourceId, selection, downloadedOnly)
        return dao.builderSongs(
            sourceId = sourceId,
            anyGenre = selection.genres.isEmpty(),
            genres = genres,
            anyDecade = selection.decades.isEmpty(),
            decades = selection.decades.toList(),
            excluded = excluding.distinct(),
            downloadedOnly = downloadedOnly,
            limit = count,
        )
    }

    /** The stored spellings behind the picked genre keys, so the match can use the genre index. */
    private suspend fun spellings(
        sourceId: Long,
        selection: QueueBuilderSelection,
        downloadedOnly: Boolean,
    ): List<String> {
        if (selection.genres.isEmpty()) return emptyList()
        return dao
            .genreSpellings(sourceId, downloadedOnly)
            .map { it.genre }
            .filter { genreKey(it) in selection.genres }
    }

    companion object {
        const val MAX_COUNT = 200
        const val MAX_EXCLUDED = 2_000

        /** Spellings that differ only in case or surrounding space are one genre. */
        fun genreKey(genre: String): String = genre.trim().lowercase()
    }
}
