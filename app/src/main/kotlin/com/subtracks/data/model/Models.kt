package com.subtracks.data.model

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val isActive: Boolean = false,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1") val creditsStale: Boolean = true,
)

@Entity(
    tableName = "subsonic_sources",
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
data class SubsonicSource(
    @PrimaryKey val sourceId: Long,
    val username: String,
    // Legacy plaintext, kept only until SourceRepository moves it into passwordCiphertext; then empty.
    val password: String,
    val useTokenAuth: Boolean = true,
    // The password sealed by the CredentialStore. Null only on a row that predates encryption.
    val passwordCiphertext: String? = null,
)

/** A custom HTTP header for a server, in the order the user listed them. The value is sealed like the password. */
@Entity(
    tableName = "source_headers",
    primaryKeys = ["sourceId", "position"],
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
data class SourceHeader(
    val sourceId: Long,
    val position: Int,
    val name: String,
    val valueCiphertext: String,
)

data class ArtistRef(
    val id: String,
    val name: String,
)

@Entity(
    tableName = "artists",
    primaryKeys = ["sourceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index(name = "index_artists_name", value = ["sourceId", "sortName", "id"]),
        Index(name = "index_artists_name_plain", value = ["sourceId", "name", "id"]),
        Index(
            name = "index_artists_albumCount",
            value = ["sourceId", "albumCount", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_artists_starred",
            value = ["sourceId", "starred", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_artists_frequent",
            value = ["sourceId", "playCount", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_artists_recent",
            value = ["sourceId", "played", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
    ],
)
data class Artist(
    val sourceId: Long,
    val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val albumCount: Long,
    val starred: Long?,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    @ColumnInfo(defaultValue = "''") val coverArts: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "''") val downloadedCoverArts: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "0") val playCount: Long = 0,
    val played: Long? = null,
    @ColumnInfo(defaultValue = "0") val songCount: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE, defaultValue = "''") val sortName: String = name,
    @ColumnInfo(defaultValue = "1") val credited: Boolean = true,
    val musicBrainzId: String? = null,
)

@Entity(
    tableName = "artist_bios",
    primaryKeys = ["sourceId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ArtistBio(
    val sourceId: Long,
    val artistId: String,
    val biography: String?,
)

@Entity(
    tableName = "albums",
    primaryKeys = ["sourceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index("sourceId", "artistId"),
        Index(name = "index_albums_name", value = ["sourceId", "sortName", "id"]),
        Index(name = "index_albums_name_plain", value = ["sourceId", "name", "id"]),
        Index(name = "index_albums_artist", value = ["sourceId", "albumArtistSort", "year", "sortName", "id"]),
        Index(name = "index_albums_artist_plain", value = ["sourceId", "albumArtist", "year", "name", "id"]),
        Index(
            name = "index_albums_year",
            value = ["sourceId", "year", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_albums_added",
            value = ["sourceId", "created", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_albums_starred",
            value = ["sourceId", "starred", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_albums_frequent",
            value = ["sourceId", "playCount", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_albums_recent",
            value = ["sourceId", "played", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
    ],
)
data class Album(
    val sourceId: Long,
    val id: String,
    val artistId: String?,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val albumArtist: String?,
    val created: Long,
    val coverArt: String?,
    val genre: String?,
    val year: Long?,
    val starred: Long?,
    val songCount: Long,
    @Ignore val discTitles: Map<Long, String> = emptyMap(),
    @ColumnInfo(defaultValue = "0") val playCount: Long = 0,
    val played: Long? = null,
    @ColumnInfo(defaultValue = "''") val artists: List<ArtistRef> = emptyList(),
    @ColumnInfo(collate = ColumnInfo.NOCASE, defaultValue = "''") val sortName: String = name,
    @ColumnInfo(collate = ColumnInfo.NOCASE, defaultValue = "''") val albumArtistSort: String = albumArtist.orEmpty(),
    val musicBrainzId: String? = null,
    // OpenSubsonic only; null when the server does not say.
    val compilation: Boolean? = null,
    // Synced into album_labels and album_release_types, in the server's order.
    @Ignore val labels: List<String> = emptyList(),
    @Ignore val releaseTypes: List<String> = emptyList(),
) {
    val artistRefs: List<ArtistRef>
        get() =
            artists.ifEmpty {
                artistId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf(ArtistRef(it, albumArtist.orEmpty())) }
                    .orEmpty()
            }
}

data class ArtistAlbums(
    val albumCredits: List<Album> = emptyList(),
    val appearances: List<Album> = emptyList(),
) {
    val count: Int get() = albumCredits.size + appearances.size
}

@Entity(
    tableName = "discs",
    primaryKeys = ["sourceId", "albumId", "disc"],
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "albumId")],
)
data class Disc(
    val sourceId: Long,
    val albumId: String,
    val disc: Long,
    val title: String,
)

@Entity(
    tableName = "playlists",
    primaryKeys = ["sourceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index(name = "index_playlists_name", value = ["sourceId", "name", "id"]),
        Index(
            name = "index_playlists_added",
            value = ["sourceId", "created", "name", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        Index(
            name = "index_playlists_updated",
            value = ["sourceId", "changed", "name", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
    ],
)
data class Playlist(
    val sourceId: Long,
    val id: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val comment: String?,
    val coverArt: String?,
    val songCount: Long,
    val created: Long,
    @ColumnInfo(defaultValue = "0") val changed: Long = 0,
    @ColumnInfo(defaultValue = "0") val duration: Long = 0,
    val owner: String? = null,
    @ColumnInfo(defaultValue = "0") val shared: Boolean = false,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["sourceId", "playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "playlistId"), Index("sourceId", "songId")],
)
data class PlaylistSong(
    val sourceId: Long,
    val playlistId: String,
    val songId: String,
    val position: Long,
)

@Entity(
    tableName = "songs",
    primaryKeys = ["sourceId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId", "artistId"),
        Index(
            name = "index_songs_sourceId_albumId_order",
            value = ["sourceId", "albumId", "disc", "track", "id"],
        ),
        Index(
            name = "index_songs_starred",
            value = ["sourceId", "starred", "sortName", "id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC, Index.Order.ASC],
        ),
        // The Songs list's title, artist and recently added sorts.
        Index(name = "index_songs_sortName", value = ["sourceId", "sortName", "id"]),
        Index(name = "index_songs_artistSort", value = ["sourceId", "artistSort"]),
        Index(name = "index_songs_created", value = ["sourceId", "created", "id"]),
    ],
)
data class Song(
    val sourceId: Long,
    val id: String,
    val albumId: String?,
    val artistId: String?,
    val title: String,
    val album: String?,
    val artist: String?,
    val duration: Long?,
    val track: Long?,
    val disc: Long?,
    val starred: Long?,
    @Ignore val genre: String? = null,
    @ColumnInfo(defaultValue = "0") val created: Long = 0,
    @ColumnInfo(defaultValue = "0") val playCount: Long = 0,
    val played: Long? = null,
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    @Ignore val genres: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "''") val artists: List<ArtistRef> = emptyList(),
    @ColumnInfo(collate = ColumnInfo.NOCASE, defaultValue = "''") val sortName: String = title,
    @ColumnInfo(collate = ColumnInfo.NOCASE, defaultValue = "''") val artistSort: String = artist.orEmpty(),
    val musicBrainzId: String? = null,
    // The file's format details as the server reports them; null when unknown.
    val suffix: String? = null,
    val bitRate: Long? = null,
    val bitDepth: Long? = null,
    val samplingRate: Long? = null,
    val channelCount: Long? = null,
) {
    val artistRefs: List<ArtistRef>
        get() =
            artists.ifEmpty {
                artistId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listOf(ArtistRef(it, artist.orEmpty())) }
                    .orEmpty()
            }
}

@Entity(
    tableName = "song_genres",
    primaryKeys = ["sourceId", "songId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "genre")],
)
data class SongGenre(
    val sourceId: Long,
    val songId: String,
    val position: Long,
    val genre: String,
)

@Entity(
    tableName = "album_artists",
    primaryKeys = ["sourceId", "albumId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "artistId")],
)
data class AlbumArtistRef(
    val sourceId: Long,
    val albumId: String,
    val position: Long,
    val artistId: String,
    val name: String,
)

@Entity(
    tableName = "album_labels",
    primaryKeys = ["sourceId", "albumId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "name")],
)
data class AlbumLabel(
    val sourceId: Long,
    val albumId: String,
    val position: Long,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

@Entity(
    tableName = "album_release_types",
    primaryKeys = ["sourceId", "albumId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AlbumReleaseType(
    val sourceId: Long,
    val albumId: String,
    val position: Long,
    val type: String,
)

@Entity(
    tableName = "song_artists",
    primaryKeys = ["sourceId", "songId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["sourceId", "id"],
            childColumns = ["sourceId", "songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId", "artistId")],
)
data class SongArtistRef(
    val sourceId: Long,
    val songId: String,
    val position: Long,
    val artistId: String,
    val name: String,
)

enum class PendingActionKind { Scrobble, Star, Unstar }

@Entity(
    tableName = "pending_actions",
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
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val kind: PendingActionKind,
    val targetId: String,
    val starType: String? = null,
    @ColumnInfo(defaultValue = "0") val time: Long = 0,
)

sealed interface SongItem {
    val song: Song
    val coverArt: String?
    val playlistPosition: Long? get() = null
}

data class AlbumSongItem(
    @Embedded override val song: Song,
    override val coverArt: String?,
) : SongItem

data class PlaylistSongItem(
    @Embedded override val song: Song,
    override val coverArt: String?,
    val position: Long,
) : SongItem {
    override val playlistPosition: Long get() = position
}

data class DiscKey(
    val albumId: String,
    val disc: Long,
)

// Library is the Library's Songs list; its refId is an encoded SongListSpec (sort, filters, search).
// Stored by name. ArtistAppearances: the artist page's Appears On (credited songs on other artists' albums).
enum class QueueKind { Playlist, Album, Song, Artist, Genre, Downloaded, Starred, Library, ArtistAppearances }

@Entity(
    tableName = "queue_entries",
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId"), Index("position")],
)
data class QueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Long,
    val sourceId: Long,
    val kind: QueueKind,
    val refId: String,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
) {
    val offset: Long get() = rangeStart ?: 0

    val count: Long? get() = rangeEnd?.let { (it - offset + 1).coerceAtLeast(0) }
}

@Entity(
    tableName = "up_next_entries",
    foreignKeys = [
        ForeignKey(
            entity = Source::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId"), Index("position")],
)
data class UpNextEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Long,
    val sourceId: Long,
    val kind: QueueKind,
    val refId: String,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
) {
    val offset: Long get() = rangeStart ?: 0

    val count: Long? get() = rangeEnd?.let { (it - offset + 1).coerceAtLeast(0) }

    fun toQueueEntry(): QueueEntry = QueueEntry(id, position, sourceId, kind, refId, rangeStart, rangeEnd)

    companion object {
        fun from(entry: QueueEntry) =
            UpNextEntry(entry.id, entry.position, entry.sourceId, entry.kind, entry.refId, entry.rangeStart, entry.rangeEnd)
    }
}

@Entity(tableName = "playback_cursor")
data class PlaybackCursor(
    @PrimaryKey val id: Long = 1,
    val queuePosition: Long,
    @ColumnInfo(defaultValue = "0") val shuffleEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val repeatMode: Int = 0,
    @ColumnInfo(defaultValue = "0") val shuffleSeed: Long = 0,
    @ColumnInfo(defaultValue = "0") val shuffleSize: Long = 0,
    @ColumnInfo(defaultValue = "0") val positionMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val upNextAnchor: Long = 0,
)

@Entity(
    tableName = "artwork_seeds",
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
data class ArtworkSeed(
    @PrimaryKey val cacheKey: String,
    val primary: Int,
    val secondary: Int?,
    val nameBusy: Boolean? = null,
    val sourceId: Long = 0,
    @ColumnInfo(defaultValue = "0") val storedAt: Long = 0,
)

@Entity(tableName = "album_search")
@Fts5(contentEntity = Album::class, tokenizer = FtsOptions.TOKENIZER_TRIGRAM)
data class AlbumSearch(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val name: String,
    val albumArtist: String?,
    val sortName: String,
    val albumArtistSort: String,
)

@Entity(tableName = "artist_search")
@Fts5(contentEntity = Artist::class, tokenizer = FtsOptions.TOKENIZER_TRIGRAM)
data class ArtistSearch(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val name: String,
    val sortName: String,
)

@Entity(tableName = "playlist_search")
@Fts5(contentEntity = Playlist::class, tokenizer = FtsOptions.TOKENIZER_TRIGRAM)
data class PlaylistSearch(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val name: String,
)

@Entity(tableName = "song_search")
@Fts5(contentEntity = Song::class, tokenizer = FtsOptions.TOKENIZER_TRIGRAM)
data class SongSearch(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val title: String,
    val artist: String?,
    val sortName: String,
    val artistSort: String,
)

class QueueKindConverter {
    @ColumnTypeConverter
    fun fromQueueKind(kind: QueueKind): String = kind.name

    @ColumnTypeConverter
    fun toQueueKind(value: String): QueueKind = QueueKind.entries.firstOrNull { it.name == value } ?: QueueKind.Song
}

class PendingActionKindConverter {
    @ColumnTypeConverter
    fun fromPendingActionKind(kind: PendingActionKind): String = kind.name

    @ColumnTypeConverter
    fun toPendingActionKind(value: String): PendingActionKind =
        PendingActionKind.entries.firstOrNull { it.name == value } ?: PendingActionKind.Scrobble
}

class ArtistRefsConverter {
    @ColumnTypeConverter
    fun fromArtistRefs(refs: List<ArtistRef>): String = ArtistRefsCodec.encode(refs)

    @ColumnTypeConverter
    fun toArtistRefs(value: String): List<ArtistRef> = ArtistRefsCodec.decode(value)
}

class StringListConverter {
    @ColumnTypeConverter
    fun fromList(values: List<String>): String = values.joinToString(LIST_SEPARATOR)

    @ColumnTypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(LIST_SEPARATOR)
}

private const val LIST_SEPARATOR = "\u001F"

object ArtistRefsCodec {
    fun encode(refs: List<ArtistRef>): String =
        buildString {
            refs.forEach { ref ->
                append(ref.id.length).append(':').append(ref.id)
                append(ref.name.length).append(':').append(ref.name)
            }
        }

    fun decode(value: String): List<ArtistRef> {
        val refs = mutableListOf<ArtistRef>()
        var index = 0
        while (index < value.length) {
            val idLengthEnd = value.indexOf(':', index)
            if (idLengthEnd < 0) return refs
            val idLength = value.substring(index, idLengthEnd).toIntOrNull() ?: return refs
            val idStart = idLengthEnd + 1
            val idEnd = idStart + idLength
            if (idLength < 0 || idEnd > value.length) return refs
            val nameLengthEnd = value.indexOf(':', idEnd)
            if (nameLengthEnd < 0) return refs
            val nameLength = value.substring(idEnd, nameLengthEnd).toIntOrNull() ?: return refs
            val nameStart = nameLengthEnd + 1
            val nameEnd = nameStart + nameLength
            if (nameLength < 0 || nameEnd > value.length) return refs
            refs += ArtistRef(value.substring(idStart, idEnd), value.substring(nameStart, nameEnd))
            index = nameEnd
        }
        return refs
    }
}
