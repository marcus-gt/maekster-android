package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSaveDraft
import com.subtracks.data.model.PlaylistSaveDraftSong

@Dao
interface PlaylistSaveDraftDao {
    @Insert
    suspend fun insert(draft: PlaylistSaveDraft): Long

    @Insert
    suspend fun insertSongs(songs: List<PlaylistSaveDraftSong>)

    @Query("SELECT * FROM playlist_save_drafts WHERE id = :id")
    suspend fun draft(id: Long): PlaylistSaveDraft?

    @Query("UPDATE playlist_save_drafts SET size = :size WHERE id = :id")
    suspend fun setSize(
        id: Long,
        size: Long,
    )

    @Query("UPDATE playlist_save_drafts SET playlistId = :playlistId WHERE id = :id")
    suspend fun setPlaylistId(
        id: Long,
        playlistId: String?,
    )

    /** Up to [limit] captured song IDs from [from] on, in order. */
    @Query(
        "SELECT songId FROM playlist_save_draft_songs WHERE draftId = :draftId AND position >= :from " +
            "ORDER BY position LIMIT :limit",
    )
    suspend fun songIds(
        draftId: Long,
        from: Long,
        limit: Int,
    ): List<String>

    @Query("DELETE FROM playlist_save_drafts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_save_drafts WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)

    @Upsert
    suspend fun upsertPlaylist(playlist: Playlist)

    @Query("DELETE FROM playlist_songs WHERE sourceId = :sourceId AND playlistId = :playlistId")
    suspend fun deletePlaylistSongs(
        sourceId: Long,
        playlistId: String,
    )

    @Query(
        "INSERT INTO playlist_songs (sourceId, playlistId, songId, position) " +
            "SELECT :sourceId, :playlistId, songId, position FROM playlist_save_draft_songs WHERE draftId = :draftId",
    )
    suspend fun copySongs(
        draftId: Long,
        sourceId: Long,
        playlistId: String,
    )

    /**
     * Mirrors [playlist] with the draft's songs as its entries (the server has been checked to hold exactly those),
     * then drops the draft, without loading the songs into memory.
     */
    @Transaction
    suspend fun complete(
        draftId: Long,
        playlist: Playlist,
    ) {
        upsertPlaylist(playlist)
        deletePlaylistSongs(playlist.sourceId, playlist.id)
        copySongs(draftId, playlist.sourceId, playlist.id)
        delete(draftId)
    }
}
