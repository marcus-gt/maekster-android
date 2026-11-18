package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "playlist_usage",
    primaryKeys = ["sourceId", "playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistUsage(
    val sourceId: Long,
    val playlistId: String,
    val usedAt: Long,
)
