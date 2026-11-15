package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A ListenBrainz listen that could not be sent (offline, rate limited, server down), kept until a later
 * flush imports it. Not tied to a library server: the ListenBrainz account is global.
 */
@Entity(tableName = "pending_listens")
data class PendingListen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch seconds when the play started. */
    val listenedAt: Long,
    val trackName: String,
    val artistName: String,
    val releaseName: String?,
    val durationMs: Long?,
)
