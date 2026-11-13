package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/** A playlist chosen for the heart's long-press menu on one server, in the user's [position] order. */
@Entity(
    tableName = "quick_playlists",
    primaryKeys = ["sourceId", "playlistId"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceId", "position"])],
)
data class QuickPlaylist(
    val sourceId: Long,
    val playlistId: String,
    val position: Long,
)
