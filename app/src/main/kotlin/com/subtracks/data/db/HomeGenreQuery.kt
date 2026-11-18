package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import org.json.JSONArray

private const val HOME_GENRE_LIMIT = 5
private const val HOME_GENRE_ALBUM_LIMIT = 6

/**
 * Home's genre shelves: genres with more than [HOME_GENRE_ALBUM_LIMIT] albums appear in a daily shuffled order,
 * and smaller genres fill any remaining shelves, largest first. Each shelf shows a daily window of its albums.
 */
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
                SELECT value ->> 0 AS tag, value ->> 1 AS genre, value ->> 2 AS shuffle FROM json_each(:mapping)
            ), votes AS (
                SELECT albums.id AS albumId, tags.genre, MAX(tags.shuffle) AS shuffle, COUNT(DISTINCT songs.id) AS tracks,
                    MAX(CASE WHEN albums.genre = tags.tag COLLATE NOCASE THEN 1 ELSE 0 END) AS albumTag
                FROM tags
                JOIN song_genres ON song_genres.genre = tags.tag AND song_genres.sourceId = :sourceId
                JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId
                JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId
                WHERE albums.sourceId = :sourceId $downloadFilter
                GROUP BY albums.id, tags.genre
            ), rankedGenres AS (
                SELECT albumId, genre, shuffle, ROW_NUMBER() OVER (
                    PARTITION BY albumId ORDER BY tracks DESC, albumTag DESC, genre COLLATE NOCASE
                ) AS preference
                FROM votes
            ), counts AS (
                SELECT genre, COUNT(*) AS albumCount, MAX(shuffle) AS shuffle
                FROM rankedGenres
                WHERE preference = 1
                GROUP BY genre
            ), picked AS (
                SELECT genre, albumCount, ROW_NUMBER() OVER (
                    ORDER BY albumCount <= $HOME_GENRE_ALBUM_LIMIT,
                        CASE WHEN albumCount > $HOME_GENRE_ALBUM_LIMIT THEN shuffle END,
                        albumCount DESC, genre COLLATE NOCASE
                ) AS shelf
                FROM counts
            ), ordered AS (
                SELECT rankedGenres.albumId, picked.genre, picked.albumCount, picked.shelf,
                    ROW_NUMBER() OVER (PARTITION BY picked.genre ORDER BY rankedGenres.albumId) - 1 AS ordinal
                FROM rankedGenres JOIN picked ON picked.genre = rankedGenres.genre
                WHERE rankedGenres.preference = 1 AND picked.shelf <= $HOME_GENRE_LIMIT
            ), rotated AS (
                SELECT albumId, genre, shelf,
                    ((ordinal - :day * min($HOME_GENRE_ALBUM_LIMIT, max(1, albumCount - 1))) % albumCount + albumCount)
                        % albumCount AS position
                FROM ordered
            )
            SELECT albums.*, rotated.genre AS shelfGenre
            FROM rotated JOIN albums ON albums.sourceId = :sourceId AND albums.id = rotated.albumId
            WHERE rotated.position < $HOME_GENRE_ALBUM_LIMIT
            ORDER BY rotated.shelf, rotated.position
            """.trimIndent(),
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "day" to day,
                "mapping" to
                    JSONArray(
                        mapping.map { JSONArray(listOf(it.tag, it.main, dailyGenreShuffle(day, it.main))) },
                    ).toString(),
            ),
    )
}

/** A per-day sort key for [genre]: stable for one local day, independent of tag order, and different the next day. */
internal fun dailyGenreShuffle(
    day: Long,
    genre: String,
): Long {
    var hash =
        "$day|${genre.lowercase()}".encodeToByteArray().fold(FNV_OFFSET) { h, byte ->
            (h xor (byte.toLong() and 0xff)) * FNV_PRIME
        }
    hash = (hash xor (hash ushr 33)) * MIX_1
    hash = (hash xor (hash ushr 33)) * MIX_2
    return (hash xor (hash ushr 33)) ushr 1
}

private const val FNV_OFFSET = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val MIX_1 = -0xae502812aa7333L
private const val MIX_2 = -0x3b314601e57a13adL
