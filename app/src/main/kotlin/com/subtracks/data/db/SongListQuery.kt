package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import com.subtracks.data.prefs.SongSort
import com.subtracks.data.prefs.SortNameMode
import com.subtracks.data.prefs.StarredFilter

/**
 * What the Library's Songs list shows: its sort, filters and search. Playing from the list stores this, encoded, as
 * the queue entry's reference, so the queue walks the same rows in the same order without copying them.
 */
data class SongListSpec(
    val sort: SongSort = SongSort.Name,
    val descending: Boolean = false,
    val starred: StarredFilter = StarredFilter.Any,
    val downloaded: Boolean = false,
    val search: String = "",
    val sortNameMode: SortNameMode = SortNameMode.SortName,
) {
    // The search goes last so it may contain the separator.
    fun encode(): String =
        listOf(
            VERSION,
            sort.name,
            if (descending) "1" else "0",
            starred.name,
            if (downloaded) "1" else "0",
            sortNameMode.name,
            search,
        ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "v1"
        private const val SEPARATOR = "|"
        private const val FIELDS = 7

        /** The spec an [encode]d reference names; null for anything else, so a bad reference plays nothing. */
        fun decode(value: String): SongListSpec? {
            val parts = value.split(SEPARATOR, limit = FIELDS)
            if (parts.size != FIELDS || parts[0] != VERSION) return null
            return SongListSpec(
                sort = SongSort.entries.firstOrNull { it.name == parts[1] } ?: return null,
                descending = parts[2] == "1",
                starred = StarredFilter.entries.firstOrNull { it.name == parts[3] } ?: return null,
                downloaded = parts[4] == "1",
                sortNameMode = SortNameMode.entries.firstOrNull { it.name == parts[5] } ?: return null,
                search = parts[6],
            )
        }
    }
}

private const val SONGS_FROM =
    "FROM songs LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
        "WHERE songs.sourceId = :sourceId " +
        "AND (:starredFilter = 0 OR (:starredFilter = 1 AND songs.starred IS NOT NULL) " +
        "OR (:starredFilter = 2 AND songs.starred IS NULL)) " +
        "AND (:search = '' OR $SONG_SEARCH_FILTER " +
        "OR (length(:search) < $SEARCH_MIN_LENGTH AND (instr(lower(songs.title), lower(:search)) > 0 " +
        "OR instr(lower(coalesce(songs.artist, '')), lower(:search)) > 0)))"

private const val SONG_DOWNLOADED =
    " AND EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = songs.sourceId " +
        "AND sd.songId = songs.id AND sd.status = 'Completed')"

private fun SongListSpec.where(): String = if (downloaded) SONGS_FROM + SONG_DOWNLOADED else SONGS_FROM

private fun SongListSpec.bindings(sourceId: Long): Map<String, Any?> =
    mapOf("sourceId" to sourceId, "starredFilter" to starred.ordinal, "search" to search)

/** One page of the Songs list, or of a queue playing it. */
internal fun songsPageQuery(
    sourceId: Long,
    spec: SongListSpec,
    offset: Long? = null,
    limit: Int? = null,
): RoomRawQuery {
    val page = if (offset != null && limit != null) " LIMIT $limit OFFSET $offset" else ""
    return rawQuery(
        sql = "SELECT songs.*, albums.coverArt AS coverArt ${spec.where()} ORDER BY ${songOrder(spec)}$page",
        bindings = spec.bindings(sourceId),
    )
}

internal fun songsCountQuery(
    sourceId: Long,
    spec: SongListSpec,
): RoomRawQuery = rawQuery(sql = "SELECT COUNT(*) ${spec.where()}", bindings = spec.bindings(sourceId))

private fun songNameKey(mode: SortNameMode): String =
    when (mode) {
        SortNameMode.SortName -> "songs.sortName COLLATE NOCASE"
        SortNameMode.Name -> "songs.title COLLATE NOCASE"
        SortNameMode.NonLatin -> "(CASE WHEN songs.title GLOB '*[A-Za-z]*' THEN songs.title ELSE songs.sortName END) COLLATE NOCASE"
    }

private fun songArtistKey(mode: SortNameMode): String =
    when (mode) {
        SortNameMode.SortName -> {
            "songs.artistSort COLLATE NOCASE"
        }

        SortNameMode.Name -> {
            "coalesce(songs.artist, '') COLLATE NOCASE"
        }

        SortNameMode.NonLatin -> {
            "(CASE WHEN songs.artist GLOB '*[A-Za-z]*' THEN songs.artist ELSE songs.artistSort END) COLLATE NOCASE"
        }
    }

/**
 * The ORDER BY for [spec]. Each sort lists its terms in their natural direction (newest first for Added); the
 * reversed order flips every term, and the song id breaks ties so paging and the queue never disagree.
 */
internal fun songOrder(spec: SongListSpec): String {
    val name = songNameKey(spec.sortNameMode)
    val albumTracks = listOf("songs.album COLLATE NOCASE", "songs.albumId", "songs.disc", "songs.track")
    val terms: List<Pair<String, Boolean>> =
        when (spec.sort) {
            SongSort.Name -> listOf(name to false)
            SongSort.Artist -> listOf(songArtistKey(spec.sortNameMode) to false) + albumTracks.map { it to false }
            SongSort.Album -> albumTracks.map { it to false }
            SongSort.Added -> listOf("songs.created" to true, name to false)
        } + ("songs.id" to false)
    return terms.joinToString(", ") { (term, naturalDescending) ->
        if (naturalDescending != spec.descending) "$term DESC" else term
    }
}
