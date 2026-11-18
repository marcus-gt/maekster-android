package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import com.subtracks.data.prefs.AlbumSort
import com.subtracks.data.prefs.ArtistSort
import com.subtracks.data.prefs.PlaylistSort
import com.subtracks.data.prefs.SortNameMode

// The default filters intentionally omit song_downloads: the unfiltered lists do not need the
// join, and their paging sources do not observe download writes (including progress updates).
internal const val ALBUMS_FILTER = """
FROM albums WHERE sourceId = :sourceId
AND (:starredFilter = 0 OR (:starredFilter = 1 AND starred IS NOT NULL) OR (:starredFilter = 2 AND starred IS NULL))
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM album_search
        WHERE album_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND (instr(lower(name), lower(:search)) > 0
        OR instr(lower(albumArtist), lower(:search)) > 0
        OR instr(lower(sortName), lower(:search)) > 0
        OR instr(lower(albumArtistSort), lower(:search)) > 0)))
"""

internal const val ALBUMS_FILTER_DOWNLOADED = """
FROM albums WHERE sourceId = :sourceId
AND (:starredFilter = 0 OR (:starredFilter = 1 AND starred IS NOT NULL) OR (:starredFilter = 2 AND starred IS NULL))
AND EXISTS (SELECT 1 FROM songs
    JOIN song_downloads sd ON sd.sourceId = songs.sourceId AND sd.songId = songs.id
    WHERE songs.sourceId = albums.sourceId AND songs.albumId = albums.id AND sd.status = 'Completed')
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM album_search
        WHERE album_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND (instr(lower(name), lower(:search)) > 0
        OR instr(lower(albumArtist), lower(:search)) > 0
        OR instr(lower(sortName), lower(:search)) > 0
        OR instr(lower(albumArtistSort), lower(:search)) > 0)))
"""

internal const val ARTISTS_FILTER = """
FROM artists WHERE sourceId = :sourceId
AND (:starredFilter = 0 OR (:starredFilter = 1 AND starred IS NOT NULL) OR (:starredFilter = 2 AND starred IS NULL))
AND $ARTIST_CREDITED
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM artist_search
        WHERE artist_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND (instr(lower(name), lower(:search)) > 0
        OR instr(lower(sortName), lower(:search)) > 0)))
"""

internal const val ARTISTS_FILTER_DOWNLOADED = """
FROM artists WHERE sourceId = :sourceId
AND (:starredFilter = 0 OR (:starredFilter = 1 AND starred IS NOT NULL) OR (:starredFilter = 2 AND starred IS NULL))
AND $ARTIST_CREDITED
AND EXISTS (SELECT 1 FROM ($ARTIST_DOWNLOAD_ROWS) dl
    WHERE dl.id = artists.id AND dl.status = 'Completed')
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM artist_search
        WHERE artist_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND (instr(lower(name), lower(:search)) > 0
        OR instr(lower(sortName), lower(:search)) > 0)))
"""

// The downloaded list counts the albums that actually have a download so the number under an artist
// matches the albums its detail screen lists.
internal val ARTIST_COLUMNS_DOWNLOADED = """
artists.sourceId, artists.id, artists.name,
(SELECT COUNT(*) FROM ($DOWNLOADED_ARTIST_ALBUMS)) AS albumCount,
$ARTIST_COLUMNS_TAIL
"""

internal const val PLAYLISTS_FILTER = """
FROM playlists WHERE sourceId = :sourceId
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM playlist_search
        WHERE playlist_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND instr(lower(name), lower(:search)) > 0))
"""

internal const val PLAYLISTS_FILTER_DOWNLOADED = """
FROM playlists WHERE sourceId = :sourceId
AND EXISTS (SELECT 1 FROM playlist_songs ps
    JOIN song_downloads sd ON sd.sourceId = ps.sourceId AND sd.songId = ps.songId
    WHERE ps.sourceId = playlists.sourceId AND ps.playlistId = playlists.id AND sd.status = 'Completed')
AND (:search = ''
    OR (length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM playlist_search
        WHERE playlist_search MATCH '"' || replace(:search, '"', '""') || '"'))
    OR (length(:search) < $SEARCH_MIN_LENGTH AND instr(lower(name), lower(:search)) > 0))
"""

// The downloaded list counts the songs that actually have a download so the number under a
// playlist matches the songs its detail screen lists.
internal const val PLAYLISTS_COLUMNS_DOWNLOADED = """
playlists.sourceId, playlists.id, playlists.name, playlists.comment, playlists.coverArt,
(SELECT COUNT(DISTINCT ps.songId)
    FROM playlist_songs ps
    JOIN song_downloads sd ON sd.sourceId = ps.sourceId AND sd.songId = ps.songId AND sd.status = 'Completed'
    WHERE ps.sourceId = playlists.sourceId AND ps.playlistId = playlists.id
) AS songCount,
playlists.created, playlists.changed, playlists.duration, playlists.owner, playlists.shared
"""

internal fun albumsPageQuery(
    sourceId: Long,
    starredFilter: Int,
    search: String,
    sort: AlbumSort,
    descending: Boolean,
    downloaded: Boolean,
    sortNameMode: SortNameMode = SortNameMode.SortName,
): RoomRawQuery =
    rawQuery(
        sql = "SELECT * ${if (downloaded) ALBUMS_FILTER_DOWNLOADED else ALBUMS_FILTER} ORDER BY ${albumOrder(
            sort,
            descending,
            sortNameMode,
        )}",
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "starredFilter" to starredFilter,
                "search" to search,
            ),
    )

internal fun artistsPageQuery(
    sourceId: Long,
    starredFilter: Int,
    search: String,
    sort: ArtistSort,
    descending: Boolean,
    downloaded: Boolean,
    sortNameMode: SortNameMode = SortNameMode.SortName,
): RoomRawQuery =
    rawQuery(
        sql =
            "SELECT ${if (downloaded) ARTIST_COLUMNS_DOWNLOADED else ARTIST_COLUMNS} " +
                "${if (downloaded) ARTISTS_FILTER_DOWNLOADED else ARTISTS_FILTER} " +
                "ORDER BY ${artistOrder(sort, descending, sortNameMode)}",
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "starredFilter" to starredFilter,
                "search" to search,
            ),
    )

internal fun playlistsPageQuery(
    sourceId: Long,
    search: String,
    sort: PlaylistSort,
    descending: Boolean,
    downloaded: Boolean,
): RoomRawQuery =
    rawQuery(
        sql =
            "SELECT ${if (downloaded) PLAYLISTS_COLUMNS_DOWNLOADED else "*"} " +
                "${if (downloaded) PLAYLISTS_FILTER_DOWNLOADED else PLAYLISTS_FILTER} " +
                "ORDER BY ${playlistOrder(sort, descending)}",
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "search" to search,
            ),
    )

const val REDISCOVER_LIMIT = 12
private const val REDISCOVER_FREQUENT_POOL = 40
private const val REDISCOVER_RECENT_EXCLUDED = 20
private const val REDISCOVER_FAVORITES_EXCLUDED = 40

/**
 * Rediscover, as on iOS: the most played albums ([REDISCOVER_FREQUENT_POOL]) minus the recently played and favourite
 * ones, skipping the first [REDISCOVER_LIMIT] (iOS shows those as In heavy rotation). No time window.
 */
internal const val REDISCOVER_ALBUMS_SQL =
    "WITH frequent AS (" +
        "SELECT id, ROW_NUMBER() OVER (ORDER BY playCount DESC, sortName COLLATE NOCASE, id) AS frequency " +
        "FROM albums WHERE sourceId = :sourceId AND playCount > 0 " +
        "ORDER BY playCount DESC, sortName COLLATE NOCASE, id LIMIT $REDISCOVER_FREQUENT_POOL" +
        "), shown AS (" +
        "SELECT id FROM (SELECT id FROM albums WHERE sourceId = :sourceId AND played IS NOT NULL " +
        "ORDER BY played DESC, sortName COLLATE NOCASE, id LIMIT $REDISCOVER_RECENT_EXCLUDED) " +
        "UNION SELECT id FROM (SELECT id FROM albums WHERE sourceId = :sourceId AND starred IS NOT NULL " +
        "ORDER BY starred DESC, sortName COLLATE NOCASE, id LIMIT $REDISCOVER_FAVORITES_EXCLUDED)" +
        "), eligible AS (" +
        "SELECT id, ROW_NUMBER() OVER (ORDER BY frequency) AS position FROM frequent " +
        "WHERE id NOT IN (SELECT id FROM shown)" +
        ") SELECT albums.* FROM eligible JOIN albums ON albums.sourceId = :sourceId AND albums.id = eligible.id " +
        "WHERE eligible.position > $REDISCOVER_LIMIT ORDER BY eligible.position LIMIT $REDISCOVER_LIMIT"

internal fun rediscoverAlbumsQuery(sourceId: Long): RoomRawQuery =
    rawQuery(sql = REDISCOVER_ALBUMS_SQL, bindings = mapOf("sourceId" to sourceId))

// RoomRawQuery takes a positional bind function, but SQLite gives a repeated named parameter one
// index, so the SQL can keep its names and only the first-appearance order needs binding.
internal fun rawQuery(
    sql: String,
    bindings: Map<String, Any?>,
): RoomRawQuery {
    val values =
        Regex(":(\\w+)")
            .findAll(sql)
            .map { it.groupValues[1] }
            .distinct()
            .map(bindings::getValue)
            .toList()
    return RoomRawQuery(sql) { statement ->
        values.forEachIndexed { index, value -> statement.bind(index + 1, value) }
    }
}

private fun SQLiteStatement.bind(
    index: Int,
    value: Any?,
) {
    when (value) {
        null -> bindNull(index)
        is Int -> bindLong(index, value.toLong())
        is Long -> bindLong(index, value)
        is String -> bindText(index, value)
        else -> error("Unsupported SQL parameter: ${value::class.qualifiedName}")
    }
}

internal fun albumNameSortKey(
    mode: SortNameMode,
    prefix: String = "",
): String =
    when (mode) {
        SortNameMode.SortName -> "${prefix}sortName COLLATE NOCASE"
        SortNameMode.Name -> "${prefix}name COLLATE NOCASE"
        SortNameMode.NonLatin -> "(CASE WHEN ${prefix}name GLOB '*[A-Za-z]*' THEN ${prefix}name ELSE ${prefix}sortName END) COLLATE NOCASE"
    }

internal fun albumArtistSortKey(
    mode: SortNameMode,
    prefix: String = "",
): String =
    when (mode) {
        SortNameMode.SortName -> {
            "${prefix}albumArtistSort COLLATE NOCASE"
        }

        SortNameMode.Name -> {
            "${prefix}albumArtist COLLATE NOCASE"
        }

        SortNameMode.NonLatin -> {
            "(CASE WHEN ${prefix}albumArtist GLOB '*[A-Za-z]*' THEN ${prefix}albumArtist ELSE ${prefix}albumArtistSort END) COLLATE NOCASE"
        }
    }

internal fun artistNameSortKey(mode: SortNameMode): String =
    when (mode) {
        SortNameMode.SortName -> "sortName COLLATE NOCASE"
        SortNameMode.Name -> "name COLLATE NOCASE"
        SortNameMode.NonLatin -> "(CASE WHEN name GLOB '*[A-Za-z]*' THEN name ELSE sortName END) COLLATE NOCASE"
    }

internal fun albumOrder(
    sort: AlbumSort,
    descending: Boolean,
    mode: SortNameMode,
    prefix: String = "",
): String {
    val tieDirection = if (descending) " DESC" else ""
    val primaryDirection = if (descending) "ASC" else "DESC"
    val name = albumNameSortKey(mode, prefix)
    val id = "${prefix}id$tieDirection"
    return when (sort) {
        AlbumSort.Name -> "$name$tieDirection, $id"
        AlbumSort.Artist -> "${albumArtistSortKey(mode, prefix)}$tieDirection, ${prefix}year$tieDirection, $name$tieDirection, $id"
        AlbumSort.Year -> "${prefix}year $primaryDirection, ${prefix}sortName COLLATE NOCASE$tieDirection, $id"
        AlbumSort.Added -> "${prefix}created $primaryDirection, ${prefix}sortName COLLATE NOCASE$tieDirection, $id"
        AlbumSort.Starred -> "${prefix}starred $primaryDirection NULLS LAST, ${prefix}sortName COLLATE NOCASE$tieDirection, $id"
        AlbumSort.Frequent -> "${prefix}playCount $primaryDirection, ${prefix}sortName COLLATE NOCASE$tieDirection, $id"
        AlbumSort.Recent -> "${prefix}played $primaryDirection NULLS LAST, ${prefix}sortName COLLATE NOCASE$tieDirection, $id"
    }
}

private fun artistOrder(
    sort: ArtistSort,
    descending: Boolean,
    mode: SortNameMode,
): String =
    when (sort) {
        ArtistSort.Name -> {
            val name = artistNameSortKey(mode)
            if (descending) "$name DESC, id DESC" else "$name, id"
        }

        ArtistSort.AlbumCount -> {
            if (descending) ARTIST_ORDER_BY_ALBUM_COUNT_REVERSED else ARTIST_ORDER_BY_ALBUM_COUNT
        }

        ArtistSort.Starred -> {
            if (descending) ARTIST_ORDER_BY_STARRED_REVERSED else ARTIST_ORDER_BY_STARRED
        }

        ArtistSort.Frequent -> {
            if (descending) ARTIST_ORDER_BY_FREQUENT_REVERSED else ARTIST_ORDER_BY_FREQUENT
        }

        ArtistSort.Recent -> {
            if (descending) ARTIST_ORDER_BY_RECENT_REVERSED else ARTIST_ORDER_BY_RECENT
        }
    }

private const val PLAYLIST_LAST_USED_DATE = """
COALESCE(
    (SELECT usedAt FROM playlist_usage WHERE sourceId = playlists.sourceId AND playlistId = playlists.id),
    CASE
        WHEN name COLLATE BINARY IN ('maekster · Night', 'maekster · Energetic', 'maekster · Workout', 'maekster · Chill', 'maekster · Focus')
            OR (name GLOB 'maekster Wrapped [0-9]*' AND substr(name, length('maekster Wrapped ') + 1) NOT GLOB '*[^0-9]*')
        THEN NULL
        ELSE NULLIF(changed, 0)
    END
)
"""

private fun playlistOrder(
    sort: PlaylistSort,
    descending: Boolean,
): String =
    when (sort) {
        PlaylistSort.Name -> {
            if (descending) PLAYLIST_ORDER_BY_NAME_REVERSED else PLAYLIST_ORDER_BY_NAME
        }

        PlaylistSort.Added -> {
            if (descending) PLAYLIST_ORDER_BY_ADDED_REVERSED else PLAYLIST_ORDER_BY_ADDED
        }

        PlaylistSort.Updated -> {
            if (descending) PLAYLIST_ORDER_BY_UPDATED_REVERSED else PLAYLIST_ORDER_BY_UPDATED
        }

        PlaylistSort.LastUsed -> {
            if (descending) {
                "$PLAYLIST_LAST_USED_DATE DESC NULLS LAST, $PLAYLIST_ORDER_BY_NAME"
            } else {
                "$PLAYLIST_LAST_USED_DATE ASC NULLS LAST, $PLAYLIST_ORDER_BY_NAME_REVERSED"
            }
        }
    }
