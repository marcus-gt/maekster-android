package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/** An album, playlist or artist opened from Search on one server, newest [visitedAt] first. */
@Entity(
    tableName = "search_history",
    primaryKeys = ["sourceId", "kind", "refId"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceId", "visitedAt"])],
)
data class SearchHistoryEntry(
    val sourceId: Long,
    val kind: PinKind,
    val refId: String,
    val visitedAt: Long,
)
