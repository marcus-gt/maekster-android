package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.TopSong
import com.subtracks.data.model.TopSongsFetch
import kotlinx.coroutines.flow.Flow

@Dao
interface TopSongsDao {
    /**
     * The cached top songs that the mirror holds, in the server's order. With [downloadedOnly], only the
     * ones that can play offline.
     */
    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM top_songs " +
            "JOIN songs ON songs.sourceId = top_songs.sourceId AND songs.id = top_songs.songId " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE top_songs.sourceId = :sourceId AND top_songs.artistId = :artistId " +
            "AND (NOT :downloadedOnly OR EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = songs.sourceId " +
            "AND sd.songId = songs.id AND sd.status = 'Completed')) " +
            "ORDER BY top_songs.position",
    )
    fun songs(
        sourceId: Long,
        artistId: String,
        downloadedOnly: Boolean,
    ): Flow<List<AlbumSongItem>>

    @Query("SELECT * FROM top_songs_fetches WHERE sourceId = :sourceId AND artistId = :artistId")
    fun fetch(
        sourceId: Long,
        artistId: String,
    ): Flow<TopSongsFetch?>

    @Query("SELECT * FROM top_songs_fetches WHERE sourceId = :sourceId AND artistId = :artistId")
    suspend fun fetchOnce(
        sourceId: Long,
        artistId: String,
    ): TopSongsFetch?

    @Query("DELETE FROM top_songs WHERE sourceId = :sourceId AND artistId = :artistId")
    suspend fun delete(
        sourceId: Long,
        artistId: String,
    )

    @Insert
    suspend fun insert(rows: List<TopSong>)

    @Upsert
    suspend fun putFetch(fetch: TopSongsFetch)

    /** Replaces the artist's cached answer, so a stale list never mixes with a new one. */
    @Transaction
    suspend fun replace(
        sourceId: Long,
        artistId: String,
        songIds: List<String>,
        fetchedAt: Long,
    ) {
        delete(sourceId, artistId)
        insert(songIds.distinct().mapIndexed { index, id -> TopSong(sourceId, artistId, index, id) })
        putFetch(TopSongsFetch(sourceId, artistId, fetchedAt))
    }
}
