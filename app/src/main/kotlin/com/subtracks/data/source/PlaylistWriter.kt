package com.subtracks.data.source

import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong

/** A playlist as the server holds it right now: its summary and its songs in order. */
data class PlaylistDetail(
    val playlist: Playlist,
    val songs: List<PlaylistSong>,
)

/**
 * Edits playlists on a server. Every call is an explicit user action, so it may reach the server; the caller mirrors
 * the result into Room. Positions are 0-based indices into the server's current order.
 */
interface PlaylistWriter {
    /**
     * Creates an empty playlist, returning it when the server reports it (older servers answer with no body).
     * Songs are added with [append], so a retry can tell what already arrived.
     */
    suspend fun create(name: String): PlaylistDetail?

    suspend fun rename(
        playlistId: String,
        name: String,
    )

    suspend fun setComment(
        playlistId: String,
        comment: String,
    )

    suspend fun append(
        playlistId: String,
        songIds: List<String>,
    )

    suspend fun remove(
        playlistId: String,
        positions: List<Int>,
    )

    /** Replaces every song, in the given order (used for reordering). Repeating it with the same list is safe. */
    suspend fun replace(
        playlistId: String,
        songIds: List<String>,
    )

    /** Deletes the playlist; one the server no longer has counts as deleted. */
    suspend fun delete(playlistId: String)

    /** Reads the playlist fresh from the server, or null when the server no longer has it. */
    suspend fun playlist(playlistId: String): PlaylistDetail?
}
