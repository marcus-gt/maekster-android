package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.QuickPlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickPlaylistDao {
    /** The quick playlists still in the mirror, in the chosen order; one no longer mirrored is hidden. */
    @Query(
        "SELECT playlists.* FROM quick_playlists q JOIN playlists " +
            "ON playlists.sourceId = q.sourceId AND playlists.id = q.playlistId " +
            "WHERE q.sourceId = :sourceId ORDER BY q.position, q.playlistId",
    )
    fun resolved(sourceId: Long): Flow<List<Playlist>>

    @Query("SELECT EXISTS(SELECT 1 FROM quick_playlists WHERE sourceId = :sourceId AND playlistId = :playlistId)")
    suspend fun contains(
        sourceId: Long,
        playlistId: String,
    ): Boolean

    /** Adds the playlist after the others. */
    @Query(
        "INSERT OR IGNORE INTO quick_playlists (sourceId, playlistId, position) " +
            "SELECT :sourceId, :playlistId, COALESCE(MAX(position), -1) + 1 FROM quick_playlists WHERE sourceId = :sourceId",
    )
    suspend fun add(
        sourceId: Long,
        playlistId: String,
    )

    @Query("DELETE FROM quick_playlists WHERE sourceId = :sourceId AND playlistId = :playlistId")
    suspend fun remove(
        sourceId: Long,
        playlistId: String,
    )

    @Query("DELETE FROM quick_playlists WHERE sourceId = :sourceId")
    suspend fun clear(sourceId: Long)

    @Insert
    suspend fun insert(entries: List<QuickPlaylist>)

    /** Replaces [sourceId]'s list with [playlistIds] in that order. */
    @Transaction
    suspend fun replace(
        sourceId: Long,
        playlistIds: List<String>,
    ) {
        clear(sourceId)
        insert(playlistIds.mapIndexed { index, id -> QuickPlaylist(sourceId, id, index.toLong()) })
    }
}
