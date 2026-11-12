package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow

/** How many opened items each server's search history keeps, as on iOS. */
const val SEARCH_HISTORY_LIMIT = 50

@Dao
interface SearchHistoryDao {
    // Always after the server's newest entry, so a reopened item tops the list even within one millisecond
    // or after the clock moves back.
    @Query(
        "INSERT OR REPLACE INTO search_history (sourceId, kind, refId, visitedAt) VALUES (:sourceId, :kind, :refId, " +
            "MAX(:visitedAt, COALESCE((SELECT MAX(visitedAt) + 1 FROM search_history WHERE sourceId = :sourceId), 0)))",
    )
    suspend fun upsert(
        sourceId: Long,
        kind: PinKind,
        refId: String,
        visitedAt: Long,
    )

    @Query(
        "DELETE FROM search_history WHERE sourceId = :sourceId AND rowid NOT IN (" +
            "SELECT rowid FROM search_history WHERE sourceId = :sourceId ORDER BY visitedAt DESC, rowid DESC LIMIT :keep)",
    )
    suspend fun trim(
        sourceId: Long,
        keep: Int,
    )

    /** Moves the item to the top of its server's history, keeping only the newest [keep] items. */
    @Transaction
    suspend fun record(
        entry: SearchHistoryEntry,
        keep: Int = SEARCH_HISTORY_LIMIT,
    ) {
        upsert(entry.sourceId, entry.kind, entry.refId, entry.visitedAt)
        trim(entry.sourceId, keep)
    }

    /** Opened items resolved from the local mirror, newest first; items no longer mirrored are hidden but kept. */
    @Query(
        "SELECT h.sourceId, h.kind, h.refId, " +
            "COALESCE(albums.name, playlists.name, artists.name) AS name, albums.albumArtist AS subtitle, " +
            "COALESCE(albums.coverArt, playlists.coverArt, artists.coverArt) AS coverArt FROM search_history h " +
            "LEFT JOIN albums ON h.kind = 'Album' AND albums.sourceId = h.sourceId AND albums.id = h.refId " +
            "LEFT JOIN playlists ON h.kind = 'Playlist' AND playlists.sourceId = h.sourceId AND playlists.id = h.refId " +
            "LEFT JOIN artists ON h.kind = 'Artist' AND artists.sourceId = h.sourceId AND artists.id = h.refId " +
            "WHERE h.sourceId = :sourceId AND COALESCE(albums.id, playlists.id, artists.id) IS NOT NULL " +
            "AND (:downloaded = 0 OR (h.kind = 'Album' AND $SEARCH_ALBUM_DOWNLOADED) " +
            "OR (h.kind = 'Artist' AND $SEARCH_ARTIST_DOWNLOADED) " +
            "OR (h.kind = 'Playlist' AND $SEARCH_PLAYLIST_DOWNLOADED)) " +
            "ORDER BY h.visitedAt DESC, h.rowid DESC LIMIT :limit",
    )
    fun recent(
        sourceId: Long,
        limit: Int = SEARCH_HISTORY_LIMIT,
        downloaded: Boolean = false,
    ): Flow<List<PinnedItem>>

    @Query("DELETE FROM search_history WHERE sourceId = :sourceId")
    suspend fun clear(sourceId: Long)
}
