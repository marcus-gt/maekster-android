package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "lyrics_cache",
    primaryKeys = ["sourceId", "songId", "provider"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedLyrics(
    val sourceId: Long,
    val songId: String,
    val provider: String,
    val payload: String,
    val fetchedAt: Long,
)
