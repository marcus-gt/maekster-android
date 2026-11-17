package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "audiomuse_config",
    primaryKeys = ["sourceId"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
class AudioMuseConfig(
    val sourceId: Long,
    val url: String,
    val tokenCiphertext: String?,
)
