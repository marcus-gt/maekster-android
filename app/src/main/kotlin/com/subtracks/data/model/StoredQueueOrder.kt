package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "queue_orders",
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId")],
)
data class StoredQueueOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val prefix: String,
)

@Entity(
    tableName = "queue_order_entries",
    primaryKeys = ["orderId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = StoredQueueOrder::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId")],
)
data class StoredQueueOrderEntry(
    val orderId: Long,
    val position: Long,
    val sourceId: Long,
    val kind: QueueKind,
    val refId: String,
    val rangeStart: Long?,
    val rangeEnd: Long?,
    val length: Long,
) {
    fun toQueueEntry(): QueueEntry =
        QueueEntry(position = position, sourceId = sourceId, kind = kind, refId = refId, rangeStart = rangeStart, rangeEnd = rangeEnd)
}
