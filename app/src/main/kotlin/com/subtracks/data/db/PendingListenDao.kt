package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.subtracks.data.model.PendingListen
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingListenDao {
    @Insert
    suspend fun insert(listen: PendingListen)

    /** The oldest [limit] listens, in the order they were queued. */
    @Query("SELECT * FROM pending_listens ORDER BY id LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingListen>

    @Query("DELETE FROM pending_listens WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM pending_listens")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM pending_listens")
    fun count(): Flow<Int>
}
