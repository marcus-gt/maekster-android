package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import org.json.JSONArray

private const val HOME_GENRE_LIMIT = 5
private const val HOME_GENRE_ALBUM_LIMIT = 6

internal fun homeGenreAlbumsQuery(
    sourceId: Long,
    mapping: List<GenreTag>,
    day: Long,
    downloaded: Boolean,
): RoomRawQuery {
    val downloadFilter = if (downloaded) "AND $ALBUM_DOWNLOADED" else ""
    return rawQuery(
        sql =
            """
            WITH tags AS (
                SELECT value ->> 0 AS tag, value ->> 1 AS genre FROM json_each(:mapping)
            ), votes AS (
                SELECT albums.id AS albumId, tags.genre, COUNT(DISTINCT songs.id) AS tracks,
                    MAX(CASE WHEN albums.genre = tags.tag COLLATE NOCASE THEN 1 ELSE 0 END) AS albumTag
                FROM tags
                JOIN song_genres ON song_genres.genre = tags.tag AND song_genres.sourceId = :sourceId
                JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId
                JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId
                WHERE albums.sourceId = :sourceId $downloadFilter
                GROUP BY albums.id, tags.genre
            ), rankedGenres AS (
                SELECT albumId, genre, ROW_NUMBER() OVER (
                    PARTITION BY albumId ORDER BY tracks DESC, albumTag DESC, genre COLLATE NOCASE
                ) AS preference
                FROM votes
            ), largest AS (
                SELECT genre, COUNT(*) AS albumCount
                FROM rankedGenres
                WHERE preference = 1
                GROUP BY genre
                ORDER BY albumCount DESC, genre COLLATE NOCASE
                LIMIT $HOME_GENRE_LIMIT
            ), ordered AS (
                SELECT rankedGenres.albumId, largest.genre, largest.albumCount,
                    ROW_NUMBER() OVER (PARTITION BY largest.genre ORDER BY rankedGenres.albumId) - 1 AS ordinal
                FROM rankedGenres JOIN largest ON largest.genre = rankedGenres.genre
                WHERE rankedGenres.preference = 1
            ), rotated AS (
                SELECT albumId, genre, albumCount,
                    ((ordinal - :day * min($HOME_GENRE_ALBUM_LIMIT, max(1, albumCount - 1))) % albumCount + albumCount)
                        % albumCount AS position
                FROM ordered
            )
            SELECT albums.*, rotated.genre AS shelfGenre
            FROM rotated JOIN albums ON albums.sourceId = :sourceId AND albums.id = rotated.albumId
            WHERE rotated.position < $HOME_GENRE_ALBUM_LIMIT
            ORDER BY rotated.albumCount DESC, rotated.genre COLLATE NOCASE, rotated.position
            """.trimIndent(),
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "day" to day,
                "mapping" to JSONArray(mapping.map { JSONArray(listOf(it.tag, it.main)) }).toString(),
            ),
    )
}
