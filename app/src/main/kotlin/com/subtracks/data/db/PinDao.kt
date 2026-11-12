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

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface PinDao {
    @Query(
        "SELECT pins.sourceId, pins.kind, pins.refId, " +
            "COALESCE(albums.name, playlists.name, artists.name) AS name, albums.albumArtist AS subtitle, " +
            "COALESCE(albums.coverArt, playlists.coverArt, artists.coverArt) AS coverArt FROM pins " +
            "LEFT JOIN albums ON pins.kind = 'Album' AND albums.sourceId = pins.sourceId AND albums.id = pins.refId " +
            "LEFT JOIN playlists ON pins.kind = 'Playlist' AND playlists.sourceId = pins.sourceId AND playlists.id = pins.refId " +
            "LEFT JOIN artists ON pins.kind = 'Artist' AND artists.sourceId = pins.sourceId AND artists.id = pins.refId " +
            "WHERE pins.sourceId = :sourceId AND COALESCE(albums.id, playlists.id, artists.id) IS NOT NULL " +
            "ORDER BY pins.position, pins.kind, pins.refId",
    )
    fun resolvedPins(sourceId: Long): PagingSource<Int, PinnedItem>

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
