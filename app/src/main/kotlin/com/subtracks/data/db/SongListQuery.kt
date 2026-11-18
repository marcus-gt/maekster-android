package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import com.subtracks.data.prefs.SongSort
import com.subtracks.data.prefs.SortNameMode
import com.subtracks.data.prefs.StarredFilter
import org.json.JSONArray

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
    val albumGenreTags: List<String>? = null,
) {
    // The search goes last so it may contain the separator.
    fun encode(): String {
        val base =
            listOf(
                VERSION,
                sort.name,
                if (descending) "1" else "0",
                starred.name,
                if (downloaded) "1" else "0",
                sortNameMode.name,
                search,
            ).joinToString(SEPARATOR)
        return albumGenreTags?.let { JSONArray(listOf(GENRE_VERSION, base, JSONArray(it))).toString() } ?: base
    }

    companion object {
        private const val VERSION = "v1"
        private const val GENRE_VERSION = "genre-v1"
        private const val SEPARATOR = "|"
        private const val FIELDS = 7

        /** The spec an [encode]d reference names; null for anything else, so a bad reference plays nothing. */
        fun decode(value: String): SongListSpec? {
            if (value.startsWith("[")) {
                return runCatching {
                    val fields = JSONArray(value)
                    if (fields.getString(0) != GENRE_VERSION) return null
                    val base = decodeBase(fields.getString(1)) ?: return null
                    val tags = fields.getJSONArray(2)
                    base.copy(albumGenreTags = List(tags.length(), tags::getString))
                }.getOrNull()
            }
            return decodeBase(value)
        }

        private fun decodeBase(value: String): SongListSpec? {
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

internal fun SongListSpec.where(): String =
    SONGS_FROM + (if (downloaded) SONG_DOWNLOADED else "") +
        (if (albumGenreTags != null) " AND songs.albumId IN ($GENRE_ALBUM_MEMBERS)" else "")

private fun SongListSpec.bindings(sourceId: Long): Map<String, Any?> =
    mapOf("sourceId" to sourceId, "starredFilter" to starred.ordinal, "search" to search) +
        if (albumGenreTags == null) emptyMap() else mapOf("genreTags" to JSONArray(albumGenreTags).toString())

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

internal fun songsExistQuery(
    sourceId: Long,
    spec: SongListSpec,
): RoomRawQuery = rawQuery(sql = "SELECT EXISTS(SELECT 1 ${spec.where()})", bindings = spec.bindings(sourceId))

internal fun songNameKey(mode: SortNameMode): String =
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
 * reversed order flips every term while keeping unknown play and release dates last. The song id breaks ties.
 */
internal fun songOrder(spec: SongListSpec): String {
    if (spec.albumGenreTags != null) {
        return "$GENRE_ALBUM_ORDER, songs.disc, songs.track, songs.id"
    }
    val name = songNameKey(spec.sortNameMode)

    fun dateOrder(date: String): String {
        val direction = if (spec.descending) "ASC" else "DESC"
        val tieDirection = if (spec.descending) " DESC" else ""
        return "$date $direction NULLS LAST, $name$tieDirection, songs.id$tieDirection"
    }
    val albumTracks = listOf("songs.album COLLATE NOCASE", "songs.albumId", "songs.disc", "songs.track")
    val terms: List<Pair<String, Boolean>> =
        when (spec.sort) {
            SongSort.Name -> listOf(name to false)
            SongSort.Artist -> listOf(songArtistKey(spec.sortNameMode) to false) + albumTracks.map { it to false }
            SongSort.Album -> albumTracks.map { it to false }
            SongSort.Added -> listOf("songs.created" to true, name to false)
            SongSort.Recent -> return dateOrder("songs.played")
            SongSort.Starred -> return dateOrder("songs.starred")
            SongSort.Released -> return dateOrder("COALESCE(NULLIF(songs.year, 0), NULLIF(albums.year, 0))")
        } + ("songs.id" to false)
    return terms.joinToString(", ") { (term, naturalDescending) ->
        if (naturalDescending != spec.descending) "$term DESC" else term
    }
}
