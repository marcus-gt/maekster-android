package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A queue captured for saving as a new playlist, kept until the save succeeds or is discarded so a retry can finish
 * the same server playlist ([playlistId], once created) even after the queue has moved on or the process restarted.
 */
@Entity(
    tableName = "playlist_save_drafts",
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
data class PlaylistSaveDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val name: String,
    val size: Long = 0,
    val playlistId: String? = null,
)

/** One captured occurrence, at its 0-based [position] in the draft's playback order. */
@Entity(
    tableName = "playlist_save_draft_songs",
    primaryKeys = ["draftId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistSaveDraft::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistSaveDraftSong(
    val draftId: Long,
    val position: Long,
    val songId: String,
)
