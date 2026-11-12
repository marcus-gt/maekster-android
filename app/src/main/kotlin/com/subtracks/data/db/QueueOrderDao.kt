package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.subtracks.data.model.StoredQueueOrder
import com.subtracks.data.model.StoredQueueOrderEntry

@Dao
interface QueueOrderDao {
    @Insert
    suspend fun insertOrder(order: StoredQueueOrder): Long

    @Insert
    suspend fun insertEntries(entries: List<StoredQueueOrderEntry>)

    @Query("SELECT * FROM queue_orders WHERE sourceId = :sourceId AND id = :id")
    suspend fun order(
        sourceId: Long,
        id: Long,
    ): StoredQueueOrder?

    @Query("SELECT * FROM queue_order_entries WHERE orderId = :orderId ORDER BY position")
    suspend fun entries(orderId: Long): List<StoredQueueOrderEntry>

    @Query("DELETE FROM queue_orders WHERE id NOT IN (:retainedIds)")
    suspend fun deleteExcept(retainedIds: List<Long>)
}
