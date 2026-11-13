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

    @Query(
        "WITH RECURSIVE retained(id) AS (" +
            "SELECT CAST(refId AS INTEGER) FROM queue_entries WHERE kind = 'Order' " +
            "UNION SELECT CAST(refId AS INTEGER) FROM up_next_entries WHERE kind = 'Order' " +
            "UNION SELECT CAST(e.refId AS INTEGER) FROM queue_order_entries e " +
            "JOIN retained r ON e.orderId = r.id WHERE e.kind = 'Order') " +
            "DELETE FROM queue_orders WHERE id NOT IN (SELECT id FROM retained)",
    )
    suspend fun prune()

    @Query("DELETE FROM queue_orders WHERE id NOT IN (:retainedIds)")
    suspend fun deleteExcept(retainedIds: List<Long>)
}
