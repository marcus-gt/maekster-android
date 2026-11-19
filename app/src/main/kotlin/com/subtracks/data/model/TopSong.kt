package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * One song of a server's `getTopSongs` answer for an artist, cached so the artist page shows it offline (D9).
 * Rows of one artist are replaced together, in the server's order; [songId] may name a song the mirror lacks.
 */
@Entity(
    tableName = "top_songs",
    primaryKeys = ["sourceId", "artistId", "position"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sourceId")],
)
data class TopSong(
    val sourceId: Long,
    val artistId: String,
    val position: Int,
    val songId: String,
)

/** When the artist's top songs were last fetched, so an empty answer is remembered as well as a full one. */
@Entity(
    tableName = "top_songs_fetches",
    primaryKeys = ["sourceId", "artistId"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TopSongsFetch(
    val sourceId: Long,
    val artistId: String,
    /** Epoch milliseconds of the server answer. */
    val fetchedAt: Long,
)
