package com.subtracks.data.recommendations

import com.subtracks.data.db.TopSongsDao
import com.subtracks.data.model.AlbumSongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The server's most popular songs of an artist, by name as Subsonic `getTopSongs` takes it, as song ids in the
 * server's order. Throws when the server cannot answer; an empty list means it answered but knows none
 * (servers without Last.fm data).
 */
fun interface TopSongs {
    suspend fun top(
        sourceId: Long,
        artistName: String,
        count: Int,
    ): List<String>
}

/**
 * The artist page's Top songs: fetched on page open while online and cached per server and artist (D9).
 * Reads come from the cache joined with the mirror, so songs the library lacks are dropped and favorites and
 * downloads stay live.
 */
class TopSongsRepository(
    private val dao: TopSongsDao,
    private val remote: TopSongs,
    private val activeSourceId: suspend () -> Long?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun songs(
        sourceId: Long,
        artistId: String,
        downloadedOnly: Boolean,
    ): Flow<List<AlbumSongItem>> = dao.songs(sourceId, artistId, downloadedOnly)

    /** Whether the cache holds an answer for the artist, even an empty one. */
    fun fetched(
        sourceId: Long,
        artistId: String,
    ): Flow<Boolean> = dao.fetch(sourceId, artistId).map { it != null }

    /** Fetches and caches the artist's top songs. On failure the previous answer stays; returns whether it fetched. */
    suspend fun refresh(
        sourceId: Long,
        artistId: String,
        artistName: String,
    ): Boolean {
        if (artistId.isBlank() || artistName.isBlank()) return false
        val ids =
            try {
                remote.top(sourceId, artistName, COUNT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return false
            }
        if (activeSourceId() != sourceId) return false
        dao.replace(sourceId, artistId, ids.filter { it.isNotBlank() }.take(COUNT), now())
        return true
    }

    companion object {
        /** As iOS asks for. */
        const val COUNT = 25
    }
}
