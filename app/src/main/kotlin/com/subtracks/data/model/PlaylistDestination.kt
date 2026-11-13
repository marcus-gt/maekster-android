package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/** A playlist songs were last added to on one server, newest [usedAt] first; only successful additions count. */
@Entity(
    tableName = "playlist_destinations",
    primaryKeys = ["sourceId", "playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceId", "usedAt"])],
)
data class PlaylistDestination(
    val sourceId: Long,
    val playlistId: String,
    val usedAt: Long,
)
