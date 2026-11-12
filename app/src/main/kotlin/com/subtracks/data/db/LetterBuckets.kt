package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.data.prefs.ListQuery
import com.subtracks.data.prefs.SortNameMode

data class LetterBucket(
    val letter: String,
    val position: Int,
)

/** At most 27 rows; positions use the exact filtered name order of the paged list. */
internal fun letterBucketsQuery(
    sourceId: Long,
    tab: LibraryListTab,
    query: ListQuery,
    search: String,
    mode: SortNameMode,
): RoomRawQuery {
    val key: String
    val filter: String
    val id: String
    when (tab) {
        LibraryListTab.Albums -> {
            key = albumNameSortKey(mode)
            filter = if (query.downloaded) ALBUMS_FILTER_DOWNLOADED else ALBUMS_FILTER
            id = "id"
        }

        LibraryListTab.Artists -> {
            key = artistNameSortKey(mode)
            filter = if (query.downloaded) ARTISTS_FILTER_DOWNLOADED else ARTISTS_FILTER
            id = "id"
        }

        LibraryListTab.Songs -> {
            val spec = SongListSpec(starred = query.starred, downloaded = query.downloaded, search = search, sortNameMode = mode)
            key = songNameKey(mode)
            filter = spec.where()
            id = "songs.id"
        }

        else -> {
            error("No letter index for $tab")
        }
    }
    val direction = if (query.descending) " DESC" else ""
    val initial = "upper(substr(trim($key), 1, 1))"
    val accents =
        mapOf(
            "A" to "ÀÁÂÃÄÅĀĂĄ",
            "C" to "ÇĆĈĊČ",
            "D" to "ĎĐ",
            "E" to "ÈÉÊËĒĔĖĘĚ",
            "G" to "ĜĞĠĢ",
            "H" to "ĤĦ",
            "I" to "ÌÍÎÏĨĪĬĮİ",
            "J" to "Ĵ",
            "K" to "Ķ",
            "L" to "ĹĻĽĿŁ",
            "N" to "ÑŃŅŇ",
            "O" to "ÒÓÔÕÖØŌŎŐ",
            "R" to "ŔŖŘ",
            "S" to "ŚŜŞŠ",
            "T" to "ŢŤŦ",
            "U" to "ÙÚÛÜŨŪŬŮŰŲ",
            "W" to "Ŵ",
            "Y" to "ÝŶŸ",
            "Z" to "ŹŻŽ",
        )
    // SQLite upper() folds ASCII only; include both cases explicitly for accented initials.
    val folding =
        accents.entries.joinToString(" ") { (letter, values) ->
            "WHEN instr('$values${values.lowercase(java.util.Locale.ROOT)}', initial) > 0 THEN '$letter'"
        }
    return rawQuery(
        sql =
            "WITH ordered AS (SELECT $initial AS initial, " +
                "row_number() OVER (ORDER BY $key$direction, $id$direction) - 1 AS position $filter), " +
                "bucketed AS (SELECT CASE WHEN initial GLOB '[A-Z]' THEN initial $folding ELSE '#' END AS letter, position FROM ordered) " +
                "SELECT letter, MIN(position) AS position FROM bucketed GROUP BY letter ORDER BY position",
        bindings = mapOf("sourceId" to sourceId, "starredFilter" to query.starred.ordinal, "search" to search),
    )
}
