package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "audiomuse_mood_scores",
    primaryKeys = ["sourceId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = AudioMuseConfig::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedAudioMuseScore(
    val sourceId: Long,
    val songId: String,
    val configurationKey: String,
    val payload: String,
)
