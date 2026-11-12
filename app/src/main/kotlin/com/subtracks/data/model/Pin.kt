package com.subtracks.data.model

import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

enum class PinKind { Album, Playlist, Artist }

@Entity(
    tableName = "pins",
    primaryKeys = ["sourceId", "kind", "refId"],
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
data class Pin(
    val sourceId: Long,
    val kind: PinKind,
    val refId: String,
    val position: Long,
)

class PinKindConverter {
    @ColumnTypeConverter
    fun encode(kind: PinKind): String = kind.name

    @ColumnTypeConverter
    fun decode(value: String): PinKind = PinKind.valueOf(value)
}
