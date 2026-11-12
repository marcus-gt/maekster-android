package com.subtracks.data.db

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.subtracks.data.model.Pin
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import kotlinx.coroutines.flow.Flow

private const val RESOLVED_PINS =
    "SELECT pins.sourceId, pins.kind, pins.refId, " +
        "COALESCE(albums.name, playlists.name, artists.name) AS name, albums.albumArtist AS subtitle, " +
        "COALESCE(albums.coverArt, playlists.coverArt, artists.coverArt) AS coverArt FROM pins " +
        "LEFT JOIN albums ON pins.kind = 'Album' AND albums.sourceId = pins.sourceId AND albums.id = pins.refId " +
        "LEFT JOIN playlists ON pins.kind = 'Playlist' AND playlists.sourceId = pins.sourceId AND playlists.id = pins.refId " +
        "LEFT JOIN artists ON pins.kind = 'Artist' AND artists.sourceId = pins.sourceId AND artists.id = pins.refId " +
        "WHERE pins.sourceId = :sourceId AND COALESCE(albums.id, playlists.id, artists.id) IS NOT NULL "

private const val PIN_ORDER = "ORDER BY pins.position, pins.kind, pins.refId"

// The availability the offline Library lists use: an album or playlist with a completed song
// download, or an artist with completed downloads in the downloaded Artists rows.
private const val PIN_DOWNLOADED =
    "AND ((pins.kind = 'Album' AND EXISTS (SELECT 1 FROM songs JOIN song_downloads sd " +
        "ON sd.sourceId = songs.sourceId AND sd.songId = songs.id AND sd.status = 'Completed' " +
        "WHERE songs.sourceId = pins.sourceId AND songs.albumId = pins.refId)) " +
        "OR (pins.kind = 'Playlist' AND EXISTS (SELECT 1 FROM playlist_songs ps JOIN song_downloads sd " +
        "ON sd.sourceId = ps.sourceId AND sd.songId = ps.songId AND sd.status = 'Completed' " +
        "WHERE ps.sourceId = pins.sourceId AND ps.playlistId = pins.refId)) " +
        "OR (pins.kind = 'Artist' AND pins.refId IN (SELECT id FROM (" + DOWNLOADED_ARTIST_ROWS + ") " +
        "WHERE status = 'Completed'))) "

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface PinDao {
    @Query(RESOLVED_PINS + PIN_ORDER)
    fun resolvedPins(sourceId: Long): PagingSource<Int, PinnedItem>

    /** Resolved pins that can play offline, in pin order. */
    @Query(RESOLVED_PINS + PIN_DOWNLOADED + PIN_ORDER)
    fun downloadedPins(sourceId: Long): PagingSource<Int, PinnedItem>

    @Query("SELECT * FROM pins WHERE sourceId = :sourceId ORDER BY position, kind, refId LIMIT :limit")
    fun pins(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Pin>>

    @Query("SELECT EXISTS(SELECT 1 FROM pins WHERE sourceId = :sourceId AND kind = :kind AND refId = :refId)")
    fun isPinned(
        sourceId: Long,
        kind: PinKind,
        refId: String,
    ): Flow<Boolean>

    @Query(
        "INSERT OR IGNORE INTO pins (sourceId, kind, refId, position) " +
            "SELECT :sourceId, :kind, :refId, COALESCE(MAX(position), -1) + 1 FROM pins WHERE sourceId = :sourceId",
    )
    suspend fun pin(
        sourceId: Long,
        kind: PinKind,
        refId: String,
    )

    @Query("DELETE FROM pins WHERE sourceId = :sourceId AND kind = :kind AND refId = :refId")
    suspend fun unpin(
        sourceId: Long,
        kind: PinKind,
        refId: String,
    )
}
