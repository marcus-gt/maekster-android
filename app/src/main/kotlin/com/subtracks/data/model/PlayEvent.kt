package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One play that reached the scrobble threshold, kept locally for history and Wrapped. Names are a snapshot
 * taken at the time, so a play still reads correctly after a sync prunes or renames the song.
 */
@Entity(
    tableName = "play_events",
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "playedAt"), Index("playedAt")],
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val songId: String,
    /** Epoch seconds when the play started. */
    val playedAt: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
)
