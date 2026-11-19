package com.subtracks.data.db

import androidx.room3.RoomRawQuery
import com.subtracks.data.mix.GenreTaxonomy
import com.subtracks.data.prefs.AlbumSort
import com.subtracks.data.prefs.CollectionSort
import com.subtracks.data.prefs.SortNameMode
import org.json.JSONArray
import org.json.JSONObject

internal const val GENRE_ALBUM_MEMBERS =
    "SELECT tagged.albumId FROM song_genres sg " +
        "JOIN songs tagged ON tagged.sourceId = sg.sourceId AND tagged.id = sg.songId " +
        "WHERE sg.sourceId = :sourceId AND sg.genre IN (SELECT value FROM json_each(:genreTags))"

internal fun collectionAlbumOrder(
    sort: AlbumSort?,
    descending: Boolean,
    mode: SortNameMode,
): String =
    if (sort == null) {
        "albums.year IS NULL, albums.year DESC, albums.sortName COLLATE NOCASE, albums.id"
    } else {
        albumOrder(sort, descending, mode, prefix = "albums.")
    }

internal data class GenreTag(
    val tag: String,
    val main: String,
    val style: String?,
)

internal fun genreMapping(tags: List<String>): List<GenreTag> {
    val taxonomy = GenreTaxonomy.DISCOGS
    val styleNames = mutableMapOf<String, String>()
    return tags.flatMap { tag ->
        val resolved = taxonomy.resolve(tag.trim())
        val style =
            if (resolved.isMainTag) {
                null
            } else {
                resolved.styleName ?: styleNames.getOrPut(resolved.key) { tag.trim() }
            }
        resolved.mains.map { GenreTag(tag, it, style) }
    }
}

internal fun genreGroupsQuery(
    sourceId: Long,
    mapping: List<GenreTag>,
    sort: CollectionSort,
    descending: Boolean,
    search: String,
    downloaded: Boolean,
    main: String? = null,
): RoomRawQuery {
    val name = if (main == null) "main" else "style"
    val scope = if (main == null) "" else " AND main = :main AND style IS NOT NULL"
    val offline = if (downloaded) " AND $ALBUM_DOWNLOADED" else ""
    val otherLast = if (main == null) "(main = 'Other'), " else ""
    return rawQuery(
        sql =
            "WITH mapping AS (SELECT value ->> 0 AS tag, value ->> 1 AS main, value ->> 2 AS style FROM json_each(:mapping)) " +
                "SELECT mapping.$name AS name, COUNT(DISTINCT albums.id) AS albumCount, MAX(albums.coverArt) AS coverArt " +
                "FROM mapping JOIN song_genres sg ON sg.genre = mapping.tag " +
                "JOIN songs ON songs.sourceId = sg.sourceId AND songs.id = sg.songId " +
                "JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
                "WHERE sg.sourceId = :sourceId$scope$offline GROUP BY mapping.$name " +
                "HAVING :search = '' OR instr(lower(mapping.$name), lower(:search)) > 0 " +
                "OR MAX(instr(lower(coalesce(style, '')), lower(:search))) > 0 " +
                "ORDER BY $otherLast${collectionOrder(sort, descending)}",
        bindings =
            mapOf(
                "sourceId" to sourceId,
                "mapping" to JSONArray(mapping.map { JSONArray(listOf(it.tag, it.main, it.style ?: JSONObject.NULL)) }).toString(),
                "search" to search,
            ) + if (main == null) emptyMap() else mapOf("main" to main),
    )
}

internal fun genreAlbumSummaryQuery(
    sourceId: Long,
    genre: String,
    tags: List<String>,
    downloaded: Boolean,
): RoomRawQuery =
    rawQuery(
        sql =
            "WITH members AS (SELECT id, coverArt, created ${groupedGenreAlbumsWhere(downloaded)}) " +
                "SELECT :genre AS name, COUNT(*) AS albumCount, " +
                "(SELECT COALESCE(coverArt, id) FROM members ORDER BY created DESC, id LIMIT 1) AS coverArt " +
                "FROM members",
        bindings = mapOf("sourceId" to sourceId, "genre" to genre, "genreTags" to JSONArray(tags).toString()),
    )

private fun groupedGenreAlbumsWhere(downloaded: Boolean): String =
    "FROM albums WHERE sourceId = :sourceId AND id IN ($GENRE_ALBUM_MEMBERS)" +
        if (downloaded) " AND $ALBUM_DOWNLOADED" else ""

internal fun groupedGenreAlbumsQuery(
    sourceId: Long,
    tags: List<String>,
    downloaded: Boolean,
    sort: AlbumSort? = null,
    descending: Boolean = false,
    mode: SortNameMode = SortNameMode.SortName,
): RoomRawQuery =
    rawQuery(
        sql =
            "SELECT albums.* ${groupedGenreAlbumsWhere(downloaded)} " +
                "ORDER BY ${collectionAlbumOrder(sort, descending, mode)}",
        bindings = mapOf("sourceId" to sourceId, "genreTags" to JSONArray(tags).toString()),
    )
