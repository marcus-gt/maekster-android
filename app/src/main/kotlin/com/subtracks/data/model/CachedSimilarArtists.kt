package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "similar_artists_cache",
    primaryKeys = ["sourceId", "artistId"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class CachedSimilarArtists(
    val sourceId: Long,
    val artistId: String,
    val payload: String,
    val fetchedAt: Long,
)
