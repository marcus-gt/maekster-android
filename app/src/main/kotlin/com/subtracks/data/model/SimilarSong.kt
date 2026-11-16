package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * A server's similar-songs answer for one Instant Mix seed, cached so the mix still works offline (D9).
 * [seedKind] is a `MixSeed` kind name; rows of one seed are replaced together, in the server's order.
 */
@Entity(
    tableName = "similar_songs",
    primaryKeys = ["sourceId", "seedKind", "seedId", "position"],
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
data class SimilarSong(
    val sourceId: Long,
    val seedKind: String,
    val seedId: String,
    val position: Int,
    val songId: String,
    val artistId: String?,
    /** Epoch milliseconds of the server answer. */
    val fetchedAt: Long,
)
