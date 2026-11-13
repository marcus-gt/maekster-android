package com.subtracks.data.repo

import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** What a quick add did. */
enum class QuickAddOutcome {
    /** The song is now in the playlist. */
    Added,

    /** The playlist already held the song; nothing was sent. */
    AlreadyThere,
}

/**
 * The few playlists a long-press on a song's heart offers, chosen per server and kept in order (as iOS #68).
 * Names come from the mirror, so renames follow and playlists deleted on the server drop out of the list.
 */
class QuickPlaylistRepository(
    private val db: SubtracksDatabase,
    private val playlists: PlaylistRepository,
    private val alsoFavorite: () -> Flow<Boolean>,
    private val favorite: suspend (songId: String) -> Unit,
) {
    fun quickPlaylists(sourceId: Long): Flow<List<Playlist>> = db.quickPlaylistDao().resolved(sourceId)

    /** Adds the playlist at the end of the list, or takes it off. */
    suspend fun toggle(
        sourceId: Long,
        playlistId: String,
    ) {
        val dao = db.quickPlaylistDao()
        if (dao.contains(sourceId, playlistId)) dao.remove(sourceId, playlistId) else dao.add(sourceId, playlistId)
    }

    suspend fun remove(
        sourceId: Long,
        playlistId: String,
    ) = db.quickPlaylistDao().remove(sourceId, playlistId)

    /** Moves the quick playlist shown at [from] to [to], indices into [quickPlaylists]' current list. */
    suspend fun move(
        sourceId: Long,
        from: Int,
        to: Int,
    ) {
        val ids = quickPlaylists(sourceId).first().map { it.id }.toMutableList()
        require(from in ids.indices && to in ids.indices) { "Position outside the quick playlists" }
        ids.add(to, ids.removeAt(from))
        db.quickPlaylistDao().replace(sourceId, ids)
    }

    /**
     * Adds [songId] to the playlist unless it already holds it, then, when the preference is on, adds the song to
     * Favorites if it is not there yet. A failed addition throws (see [PlaylistRepository.add]) and favorites nothing.
     */
    suspend fun add(
        sourceId: Long,
        playlistId: String,
        songId: String,
    ): QuickAddOutcome {
        val outcome = playlists.add(PlaylistAddition(sourceId, playlistId, listOf(songId), Duplicates.Skip))
        val added = (outcome as AddOutcome.Added).count > 0
        if (alsoFavorite().first() && db.libraryDao().songOnce(sourceId, songId)?.starred == null) favorite(songId)
        return if (added) QuickAddOutcome.Added else QuickAddOutcome.AlreadyThere
    }
}
