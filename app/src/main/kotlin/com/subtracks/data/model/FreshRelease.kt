package com.subtracks.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * A recent MusicBrainz release group by an artist in this server's library that the library doesn't have.
 * Stored per server so the feed shows offline; [position] keeps the feed's order (newest first).
 */
@Entity(
    tableName = "fresh_releases",
    primaryKeys = ["sourceId", "id"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sourceId", "position")],
)
data class FreshRelease(
    val sourceId: Long,
    /** The MusicBrainz release group id; also the Cover Art Archive key. */
    val id: String,
    val position: Int,
    val title: String,
    val artistName: String,
    /** MusicBrainz' partial date: "2026", "2026-09" or "2026-09-22". */
    val firstReleaseDate: String?,
    /** "Album", "Single · Remix". */
    val typeLabel: String,
)

/** When this server's feed last refreshed successfully ([refreshedAt]) and when a refresh last started. */
@Entity(
    tableName = "fresh_release_state",
    primaryKeys = ["sourceId"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class FreshReleaseState(
    val sourceId: Long,
    val refreshedAt: Long?,
    val attemptedAt: Long,
)

/**
 * A MusicBrainz artist found for a library artist name without an id in its tags, or [musicBrainzId] null when no
 * certain match was found at [triedAt]. [name] is the normalised name, so spellings that differ only in case,
 * diacritics or punctuation share one lookup.
 */
@Entity(
    tableName = "musicbrainz_names",
    primaryKeys = ["sourceId", "name"],
    foreignKeys = [
        ForeignKey(entity = Source::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MusicBrainzName(
    val sourceId: Long,
    val name: String,
    val musicBrainzId: String?,
    val triedAt: Long,
)
