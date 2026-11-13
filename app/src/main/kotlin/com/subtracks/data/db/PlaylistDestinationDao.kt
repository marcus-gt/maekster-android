package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import com.subtracks.data.model.Playlist
import kotlinx.coroutines.flow.Flow

/** How many recent destinations each server keeps, as on iOS. */
const val PLAYLIST_DESTINATION_LIMIT = 5

@Dao
interface PlaylistDestinationDao {
    // Always after the server's newest entry, so the latest use tops the list even within one millisecond.
    @Query(
        "INSERT OR REPLACE INTO playlist_destinations (sourceId, playlistId, usedAt) VALUES (:sourceId, :playlistId, " +
            "MAX(:usedAt, COALESCE((SELECT MAX(usedAt) + 1 FROM playlist_destinations WHERE sourceId = :sourceId), 0)))",
    )
    suspend fun upsert(
        sourceId: Long,
        playlistId: String,
        usedAt: Long,
    )

    @Query(
        "DELETE FROM playlist_destinations WHERE sourceId = :sourceId AND rowid NOT IN (" +
            "SELECT rowid FROM playlist_destinations WHERE sourceId = :sourceId ORDER BY usedAt DESC, rowid DESC LIMIT :keep)",
    )
    suspend fun trim(
        sourceId: Long,
        keep: Int,
    )

    /** Moves the playlist to the top of its server's recent destinations, keeping only the newest [keep]. */
    @Transaction
    suspend fun record(
        sourceId: Long,
        playlistId: String,
        usedAt: Long,
        keep: Int = PLAYLIST_DESTINATION_LIMIT,
    ) {
        upsert(sourceId, playlistId, usedAt)
        trim(sourceId, keep)
    }

    /** Recent destinations still in the mirror, newest first; one no longer mirrored is hidden but kept. */
    @Query(
        "SELECT playlists.* FROM playlist_destinations d JOIN playlists " +
            "ON playlists.sourceId = d.sourceId AND playlists.id = d.playlistId " +
            "WHERE d.sourceId = :sourceId ORDER BY d.usedAt DESC, d.rowid DESC",
    )
    fun recent(sourceId: Long): Flow<List<Playlist>>
}
