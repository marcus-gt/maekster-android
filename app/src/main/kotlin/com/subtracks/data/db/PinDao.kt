package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import com.subtracks.data.model.Pin
import com.subtracks.data.model.PinKind
import kotlinx.coroutines.flow.Flow

@Dao
interface PinDao {
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
