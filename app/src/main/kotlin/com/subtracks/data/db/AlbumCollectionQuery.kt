package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.prefs.CollectionSort

// An album belongs to a genre when any of its songs carries the tag: albums store a single genre string, while
// songs keep every tag. Tags group case-insensitively, so "Rock" and "rock" are one genre.
private const val GENRE_ROWS =
    "FROM song_genres sg " +
        "JOIN songs ON songs.sourceId = sg.sourceId AND songs.id = sg.songId " +
        "JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
        "WHERE sg.sourceId = :sourceId AND trim(sg.genre) != ''"

private const val GENRE_SONG_DOWNLOADED =
    " AND EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = songs.sourceId " +
        "AND sd.songId = songs.id AND sd.status = 'Completed')"

// album_labels.name is NOCASE, so grouping and matching on it are already case-insensitive.
private const val LABEL_ROWS =
    "FROM album_labels al JOIN albums ON albums.sourceId = al.sourceId AND albums.id = al.albumId " +
        "WHERE al.sourceId = :sourceId AND trim(al.name) != ''"

private const val ALBUM_DOWNLOADED =
    "EXISTS (SELECT 1 FROM songs JOIN song_downloads sd ON sd.sourceId = songs.sourceId AND sd.songId = songs.id " +
        "WHERE songs.sourceId = albums.sourceId AND songs.albumId = albums.id AND sd.status = 'Completed')"

private const val COLLECTION_ALBUM_ORDER = "year IS NULL, year DESC, sortName COLLATE NOCASE, id"

/** The Genres or Labels list: one row per name with its album count, sorted and searched like the other lists. */
internal fun albumCollectionsPageQuery(
    sourceId: Long,
    kind: AlbumCollectionKind,
    sort: CollectionSort,
    descending: Boolean,
    search: String,
    downloaded: Boolean,
): RoomRawQuery {
    val (name, rows) =
        when (kind) {
            AlbumCollectionKind.Genre -> "sg.genre" to GENRE_ROWS + if (downloaded) GENRE_SONG_DOWNLOADED else ""
            AlbumCollectionKind.Label -> "al.name" to LABEL_ROWS + if (downloaded) " AND $ALBUM_DOWNLOADED" else ""
        }
    return rawQuery(
        sql =
            "SELECT $name AS name, COUNT(DISTINCT albums.id) AS albumCount, MAX(albums.coverArt) AS coverArt $rows " +
                "AND (:search = '' OR instr(lower($name), lower(:search)) > 0) " +
                "GROUP BY $name COLLATE NOCASE ORDER BY ${collectionOrder(sort, descending)}",
        bindings = mapOf("sourceId" to sourceId, "search" to search),
    )
}

/** Whether any downloaded song has a genre (or any downloaded album a label), so offline can hide an empty list. */
internal fun hasDownloadedCollectionsQuery(
    sourceId: Long,
    kind: AlbumCollectionKind,
): RoomRawQuery =
    rawQuery(
        sql =
            when (kind) {
                AlbumCollectionKind.Genre -> "SELECT EXISTS(SELECT 1 $GENRE_ROWS$GENRE_SONG_DOWNLOADED)"
                AlbumCollectionKind.Label -> "SELECT EXISTS(SELECT 1 $LABEL_ROWS AND $ALBUM_DOWNLOADED)"
            },
        bindings = mapOf("sourceId" to sourceId),
    )

/** The albums in one genre or from one label, newest first; offline, only the ones with something downloaded. */
internal fun collectionAlbumsQuery(
    sourceId: Long,
    kind: AlbumCollectionKind,
    name: String,
    downloaded: Boolean,
): RoomRawQuery {
    val members =
        when (kind) {
            AlbumCollectionKind.Genre -> {
                "SELECT songs.albumId FROM song_genres sg " +
                    "JOIN songs ON songs.sourceId = sg.sourceId AND songs.id = sg.songId " +
                    "WHERE sg.sourceId = :sourceId AND sg.genre = :name COLLATE NOCASE" +
                    if (downloaded) GENRE_SONG_DOWNLOADED else ""
            }

            AlbumCollectionKind.Label -> {
                "SELECT albumId FROM album_labels WHERE sourceId = :sourceId AND name = :name"
            }
        }
    val downloadedAlbum = if (downloaded && kind == AlbumCollectionKind.Label) " AND $ALBUM_DOWNLOADED" else ""
    return rawQuery(
        sql =
            "SELECT * FROM albums WHERE sourceId = :sourceId AND id IN ($members)$downloadedAlbum " +
                "ORDER BY $COLLECTION_ALBUM_ORDER",
        bindings = mapOf("sourceId" to sourceId, "name" to name),
    )
}

private fun collectionOrder(
    sort: CollectionSort,
    descending: Boolean,
): String =
    when (sort) {
        CollectionSort.Name -> if (descending) "name COLLATE NOCASE DESC" else "name COLLATE NOCASE"
        CollectionSort.AlbumCount -> if (descending) "albumCount ASC, name COLLATE NOCASE DESC" else "albumCount DESC, name COLLATE NOCASE"
    }
