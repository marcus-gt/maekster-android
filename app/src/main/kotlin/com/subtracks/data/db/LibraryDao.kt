package com.subtracks.data.db

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Upsert
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumArtistRef
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.data.model.AlbumLabel
import com.subtracks.data.model.AlbumReleaseType
import com.subtracks.data.model.AlbumSearch
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.ArtistBio
import com.subtracks.data.model.ArtistSearch
import com.subtracks.data.model.Disc
import com.subtracks.data.model.DiscKey
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSearch
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.PlaylistSongItem
import com.subtracks.data.model.PlaylistUsage
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongArtistRef
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.SongGenre
import com.subtracks.data.model.SongSearch
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.Flow

internal const val PLAYLIST_SONGS_SELECT =
    "SELECT songs.*, albums.coverArt AS coverArt, playlist_songs.position AS position FROM playlist_songs " +
        "JOIN songs ON songs.sourceId = playlist_songs.sourceId AND songs.id = playlist_songs.songId " +
        "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
        "WHERE playlist_songs.sourceId = :sourceId AND playlist_songs.playlistId = :playlistId"

internal const val PLAYLIST_SONGS_ORDER = " ORDER BY playlist_songs.position"

internal const val PLAYLIST_SONGS_SQL = PLAYLIST_SONGS_SELECT + PLAYLIST_SONGS_ORDER

internal const val DOWNLOADED_SONG =
    "EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = playlist_songs.sourceId " +
        "AND sd.songId = playlist_songs.songId AND sd.status = 'Completed')"

internal const val ALBUM_ORDER_BY_YEAR = "year DESC, sortName COLLATE NOCASE, id"
internal const val ALBUM_ORDER_BY_YEAR_REVERSED = "year ASC, sortName COLLATE NOCASE DESC, id DESC"
internal const val ALBUM_ORDER_BY_ADDED = "created DESC, sortName COLLATE NOCASE, id"
internal const val ALBUM_ORDER_BY_ADDED_REVERSED = "created ASC, sortName COLLATE NOCASE DESC, id DESC"

// Starred orders keep unstarred rows last in both directions, matching the list grouping, so the
// reversed order is not a literal mirror of the base order.
internal const val ALBUM_ORDER_BY_STARRED = "starred DESC NULLS LAST, sortName COLLATE NOCASE, id"
internal const val ALBUM_ORDER_BY_STARRED_REVERSED = "starred ASC NULLS LAST, sortName COLLATE NOCASE DESC, id DESC"

internal const val ALBUM_ORDER_BY_FREQUENT = "playCount DESC, sortName COLLATE NOCASE, id"
internal const val ALBUM_ORDER_BY_FREQUENT_REVERSED = "playCount ASC, sortName COLLATE NOCASE DESC, id DESC"
internal const val ALBUM_ORDER_BY_RECENT = "played DESC NULLS LAST, sortName COLLATE NOCASE, id"
internal const val ALBUM_ORDER_BY_RECENT_REVERSED = "played ASC NULLS LAST, sortName COLLATE NOCASE DESC, id DESC"

private const val ALBUM_NAME_SORT_KEY_MODE =
    "(CASE :sortMode WHEN 'Name' THEN name WHEN 'NonLatin' THEN (CASE WHEN name GLOB '*[A-Za-z]*' THEN name ELSE sortName END) ELSE sortName END) COLLATE NOCASE"
internal const val ALBUM_ORDER_BY_NAME_SORT_MODE = "$ALBUM_NAME_SORT_KEY_MODE, id"

internal const val ARTIST_CREDITED =
    "(artists.credited = 1 OR COALESCE((SELECT creditsStale FROM sources WHERE id = artists.sourceId), 1) = 1)"

// May repeat a (artistId, songId) pair when several credit paths reach it; consumers dedupe.
internal const val CREDITED_SONG_ROWS =
    "SELECT songs.artistId AS artistId, songs.id AS songId, songs.albumId AS albumId, " +
        "songs.playCount AS playCount, songs.played AS played " +
        "FROM songs WHERE songs.sourceId = :sourceId AND songs.artistId IS NOT NULL " +
        "UNION ALL " +
        "SELECT song_artists.artistId, songs.id, songs.albumId, songs.playCount, songs.played " +
        "FROM song_artists JOIN songs " +
        "ON songs.sourceId = song_artists.sourceId AND songs.id = song_artists.songId " +
        "WHERE song_artists.sourceId = :sourceId " +
        "UNION ALL " +
        "SELECT albums.artistId, songs.id, songs.albumId, songs.playCount, songs.played " +
        "FROM albums JOIN songs ON songs.sourceId = albums.sourceId AND songs.albumId = albums.id " +
        "WHERE albums.sourceId = :sourceId AND albums.artistId IS NOT NULL " +
        "UNION ALL " +
        "SELECT album_artists.artistId, songs.id, songs.albumId, songs.playCount, songs.played " +
        "FROM album_artists JOIN songs " +
        "ON songs.sourceId = album_artists.sourceId AND songs.albumId = album_artists.albumId " +
        "WHERE album_artists.sourceId = :sourceId"

internal val ALBUMS_FOR_ARTIST =
    "SELECT albums.* FROM (SELECT DISTINCT albumId FROM ($CREDITED_ALBUM_ROWS) WHERE artistId = :artistId) credits " +
        "JOIN albums ON albums.sourceId = :sourceId AND albums.id = credits.albumId " +
        "ORDER BY year DESC, sortName COLLATE NOCASE, id"

internal val ALBUM_CREDIT_ALBUM_IDS =
    "SELECT DISTINCT albumId FROM ($CREDITED_ALBUM_ROWS) " +
        "WHERE artistId = :artistId AND creditType = 'album'"

internal val DOWNLOADED_ARTIST_ALBUMS =
    "SELECT albumId AS id FROM ($DOWNLOADED_ALBUM_ROWS) WHERE artistId = artists.id"

private const val ARTIST_COVER_RANKS_TAIL =
    "ranked AS (SELECT credits.artistId AS artistId, albums.coverArt AS coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY credits.artistId " +
        "ORDER BY credits.creditRank, albums.year DESC, albums.sortName COLLATE NOCASE, albums.id) AS coverRank " +
        "FROM credits JOIN albums ON albums.sourceId = :sourceId AND albums.id = credits.albumId " +
        "WHERE albums.coverArt IS NOT NULL), " +
        "unique_ranked AS (SELECT artistId, coverArt, MIN(coverRank) AS coverRank FROM ranked GROUP BY artistId, coverArt), " +
        "deduped AS (SELECT artistId, coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY artistId ORDER BY coverRank) AS coverRank FROM unique_ranked)"

private val ARTIST_COVER_RANKS =
    "credits AS (SELECT artistId, albumId, " +
        "MIN(CASE creditType WHEN 'album' THEN 0 ELSE 1 END) AS creditRank " +
        "FROM ($ARTIST_COVER_CREDITS) GROUP BY artistId, albumId), " +
        ARTIST_COVER_RANKS_TAIL

private val ARTIST_COVER_RANKS_FOR_ARTIST =
    "credits AS (SELECT artistId, albumId, " +
        "MIN(CASE creditType WHEN 'album' THEN 0 ELSE 1 END) AS creditRank " +
        "FROM ($ARTIST_COVER_CREDITS_FOR_ARTIST) GROUP BY artistId, albumId), " +
        ARTIST_COVER_RANKS_TAIL

internal val ARTIST_ART_UPDATE_SQL =
    "WITH $ARTIST_COVER_RANKS, " +
        "covers AS (SELECT artistId, group_concat(coverArt, char(31)) AS coverArts FROM (" +
        "SELECT artistId, coverArt, coverRank FROM deduped WHERE coverRank <= 4 ORDER BY artistId, coverRank" +
        ") GROUP BY artistId), " +
        "downloaded_albums AS (SELECT DISTINCT artistId, albumId FROM ($DOWNLOADED_COVER_ALBUM_ROWS)), " +
        "downloaded AS (SELECT dl.artistId AS artistId, albums.coverArt AS coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY dl.artistId " +
        "ORDER BY albums.year DESC, albums.sortName COLLATE NOCASE, albums.id) AS coverRank " +
        "FROM downloaded_albums dl JOIN albums ON albums.sourceId = :sourceId AND albums.id = dl.albumId " +
        "WHERE albums.coverArt IS NOT NULL), " +
        "unique_downloaded AS (SELECT artistId, coverArt, MIN(coverRank) AS coverRank FROM downloaded GROUP BY artistId, coverArt), " +
        "downloaded_deduped AS (SELECT artistId, coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY artistId ORDER BY coverRank) AS coverRank FROM unique_downloaded), " +
        "downloaded_covers AS (SELECT artistId, group_concat(coverArt, char(31)) AS coverArts FROM (" +
        "SELECT artistId, coverArt, coverRank FROM downloaded_deduped WHERE coverRank <= 4 ORDER BY artistId, coverRank" +
        ") GROUP BY artistId), " +
        "desired AS (SELECT artists.rowid AS rowid, " +
        "COALESCE((SELECT coverArts FROM covers WHERE covers.artistId = artists.id), '') AS coverArts, " +
        "COALESCE((SELECT coverArts FROM downloaded_covers " +
        "WHERE downloaded_covers.artistId = artists.id), '') AS downloadedCoverArts " +
        "FROM artists WHERE artists.sourceId = :sourceId) " +
        "UPDATE artists SET coverArts = desired.coverArts, downloadedCoverArts = desired.downloadedCoverArts " +
        "FROM desired WHERE artists.rowid = desired.rowid AND " +
        "(artists.coverArts != desired.coverArts OR artists.downloadedCoverArts != desired.downloadedCoverArts)"

internal val ARTIST_COVER_ARTS_SQL =
    "WITH $ARTIST_COVER_RANKS_FOR_ARTIST " +
        "SELECT coverArt FROM deduped WHERE coverRank <= 4 ORDER BY coverRank"

internal val DOWNLOADED_ARTIST_ART_UPDATE_SQL =
    "WITH downloaded_albums AS (SELECT DISTINCT artistId, albumId FROM ($DOWNLOADED_COVER_ALBUM_ROWS) " +
        "WHERE artistId IN (:artistIds)), " +
        "downloaded AS (SELECT dl.artistId AS artistId, albums.coverArt AS coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY dl.artistId " +
        "ORDER BY albums.year DESC, albums.sortName COLLATE NOCASE, albums.id) AS coverRank " +
        "FROM downloaded_albums dl JOIN albums ON albums.sourceId = :sourceId AND albums.id = dl.albumId " +
        "WHERE albums.coverArt IS NOT NULL), " +
        "unique_downloaded AS (SELECT artistId, coverArt, MIN(coverRank) AS coverRank FROM downloaded GROUP BY artistId, coverArt), " +
        "downloaded_deduped AS (SELECT artistId, coverArt, " +
        "ROW_NUMBER() OVER (PARTITION BY artistId ORDER BY coverRank) AS coverRank FROM unique_downloaded), " +
        "downloaded_covers AS (SELECT artistId, group_concat(coverArt, char(31)) AS coverArts FROM (" +
        "SELECT artistId, coverArt, coverRank FROM downloaded_deduped WHERE coverRank <= 4 ORDER BY artistId, coverRank" +
        ") GROUP BY artistId), " +
        "desired AS (SELECT artists.rowid AS rowid, " +
        "COALESCE((SELECT coverArts FROM downloaded_covers " +
        "WHERE downloaded_covers.artistId = artists.id), '') AS downloadedCoverArts " +
        "FROM artists WHERE artists.sourceId = :sourceId AND artists.id IN (:artistIds)) " +
        "UPDATE artists SET downloadedCoverArts = desired.downloadedCoverArts " +
        "FROM desired WHERE artists.rowid = desired.rowid AND " +
        "artists.downloadedCoverArts != desired.downloadedCoverArts"

internal const val ARTIST_COLUMNS_TAIL =
    "artists.starred, artists.coverArt, artists.artistImageUrl, artists.coverArts, " +
        "artists.downloadedCoverArts, artists.playCount, artists.played, artists.songCount, " +
        "artists.sortName, artists.credited, artists.musicBrainzId"

internal const val ARTIST_COLUMNS =
    "artists.sourceId, artists.id, artists.name, artists.albumCount, $ARTIST_COLUMNS_TAIL"

internal const val ARTIST_ORDER_BY_ALBUM_COUNT = "albumCount DESC, sortName COLLATE NOCASE, id"
internal const val ARTIST_ORDER_BY_ALBUM_COUNT_REVERSED = "albumCount ASC, sortName COLLATE NOCASE DESC, id DESC"
internal const val ARTIST_ORDER_BY_STARRED = "starred DESC NULLS LAST, sortName COLLATE NOCASE, id"
internal const val ARTIST_ORDER_BY_STARRED_REVERSED = "starred ASC NULLS LAST, sortName COLLATE NOCASE DESC, id DESC"
internal const val ARTIST_ORDER_BY_FREQUENT = "artists.playCount DESC, sortName COLLATE NOCASE, id"
internal const val ARTIST_ORDER_BY_FREQUENT_REVERSED = "artists.playCount ASC, sortName COLLATE NOCASE DESC, id DESC"
internal const val ARTIST_ORDER_BY_RECENT = "artists.played DESC NULLS LAST, sortName COLLATE NOCASE, id"
internal const val ARTIST_ORDER_BY_RECENT_REVERSED = "artists.played ASC NULLS LAST, sortName COLLATE NOCASE DESC, id DESC"

private const val ARTIST_NAME_SORT_KEY_MODE =
    "(CASE :sortMode WHEN 'Name' THEN name WHEN 'NonLatin' THEN (CASE WHEN name GLOB '*[A-Za-z]*' THEN name ELSE sortName END) ELSE sortName END) COLLATE NOCASE"
internal const val ARTIST_ORDER_BY_NAME_SORT_MODE = "$ARTIST_NAME_SORT_KEY_MODE, id"

private const val SONG_NAME_SORT_KEY_MODE =
    "(CASE :sortMode WHEN 'Name' THEN songs.title WHEN 'NonLatin' THEN (CASE WHEN songs.title GLOB '*[A-Za-z]*' THEN songs.title ELSE songs.sortName END) ELSE songs.sortName END) COLLATE NOCASE"
internal const val SONG_SEARCH_ORDER_SORT_MODE = "$SONG_NAME_SORT_KEY_MODE, songs.id"

internal const val PLAYLIST_ORDER_BY_NAME = "name COLLATE NOCASE, id"
internal const val PLAYLIST_ORDER_BY_NAME_REVERSED = "name COLLATE NOCASE DESC, id DESC"
internal const val PLAYLIST_ORDER_BY_ADDED = "created DESC, name COLLATE NOCASE, id"
internal const val PLAYLIST_ORDER_BY_ADDED_REVERSED = "created ASC, name COLLATE NOCASE DESC, id DESC"
internal const val PLAYLIST_ORDER_BY_UPDATED = "changed DESC, name COLLATE NOCASE, id"
internal const val PLAYLIST_ORDER_BY_UPDATED_REVERSED = "changed ASC, name COLLATE NOCASE DESC, id DESC"

// Cross-type search only runs from three characters, since a shorter term cannot use the trigram
// index and scanning the whole library for it is not worth it.
const val SEARCH_MIN_LENGTH = 3

internal const val ALBUM_SEARCH_FILTER =
    "(length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM album_search " +
        "WHERE album_search MATCH '\"' || replace(:search, '\"', '\"\"') || '\"'))"

internal const val ARTIST_SEARCH_FILTER =
    "(length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM artist_search " +
        "WHERE artist_search MATCH '\"' || replace(:search, '\"', '\"\"') || '\"'))"

internal const val PLAYLIST_SEARCH_FILTER =
    "(length(:search) >= $SEARCH_MIN_LENGTH AND rowid IN (SELECT rowid FROM playlist_search " +
        "WHERE playlist_search MATCH '\"' || replace(:search, '\"', '\"\"') || '\"'))"

internal const val SONG_SEARCH_FILTER =
    "(length(:search) >= $SEARCH_MIN_LENGTH AND songs.rowid IN (SELECT rowid FROM song_search " +
        "WHERE song_search MATCH '\"' || replace(:search, '\"', '\"\"') || '\"'))"

// Search ranks by the title as iOS does: exact, then prefix, then contains, then a match only in another
// field (an artist, a sort name). Ranked before LIMIT so an exact title is never cut off. lower() folds
// ASCII only, so other scripts rank case-sensitively; matching itself stays the FTS trigram match.
private const val SEARCH_TERM = "lower(:search)"
internal const val ALBUM_SEARCH_RANK =
    "CASE WHEN lower(albums.name) = $SEARCH_TERM THEN 0 " +
        "WHEN substr(lower(albums.name), 1, length(:search)) = $SEARCH_TERM THEN 1 " +
        "WHEN instr(lower(albums.name), $SEARCH_TERM) > 0 THEN 2 ELSE 3 END"
internal const val ARTIST_SEARCH_RANK =
    "CASE WHEN lower(artists.name) = $SEARCH_TERM THEN 0 " +
        "WHEN substr(lower(artists.name), 1, length(:search)) = $SEARCH_TERM THEN 1 " +
        "WHEN instr(lower(artists.name), $SEARCH_TERM) > 0 THEN 2 ELSE 3 END"
internal const val PLAYLIST_SEARCH_RANK =
    "CASE WHEN lower(playlists.name) = $SEARCH_TERM THEN 0 " +
        "WHEN substr(lower(playlists.name), 1, length(:search)) = $SEARCH_TERM THEN 1 " +
        "WHEN instr(lower(playlists.name), $SEARCH_TERM) > 0 THEN 2 ELSE 3 END"
internal const val SONG_SEARCH_RANK =
    "CASE WHEN lower(songs.title) = $SEARCH_TERM THEN 0 " +
        "WHEN substr(lower(songs.title), 1, length(:search)) = $SEARCH_TERM THEN 1 " +
        "WHEN instr(lower(songs.title), $SEARCH_TERM) > 0 THEN 2 ELSE 3 END"

internal val ARTIST_AGGREGATES_SQL =
    "WITH credited AS (SELECT DISTINCT * FROM ($CREDITED_SONG_ROWS)), " +
        "credited_albums AS ($CREDITED_ALBUM_ROWS), " +
        "totals AS (" +
        "SELECT artistId AS id, COUNT(DISTINCT songId) AS songCount, " +
        "SUM(playCount) AS playCount, MAX(played) AS played " +
        "FROM credited GROUP BY artistId" +
        "), album_totals AS (" +
        "SELECT artistId AS id, COUNT(DISTINCT albumId) AS albumCount " +
        "FROM credited_albums GROUP BY artistId" +
        ") " +
        "UPDATE artists SET songCount = c.songCount, albumCount = c.albumCount, " +
        "playCount = c.playCount, played = c.played, credited = c.credited " +
        "FROM (SELECT artists.id AS id, COALESCE(totals.songCount, 0) AS songCount, " +
        "COALESCE(album_totals.albumCount, 0) AS albumCount, " +
        "COALESCE(totals.playCount, 0) AS playCount, totals.played AS played, " +
        "((NOT EXISTS (SELECT 1 FROM album_artists WHERE sourceId = artists.sourceId) " +
        "AND NOT EXISTS (SELECT 1 FROM song_artists WHERE sourceId = artists.sourceId)) " +
        "OR EXISTS (SELECT 1 FROM album_artists aa " +
        "WHERE aa.sourceId = artists.sourceId AND aa.artistId = artists.id) " +
        "OR EXISTS (SELECT 1 FROM song_artists sa " +
        "WHERE sa.sourceId = artists.sourceId AND sa.artistId = artists.id)) AS credited " +
        "FROM artists LEFT JOIN totals ON totals.id = artists.id " +
        "LEFT JOIN album_totals ON album_totals.id = artists.id " +
        "WHERE artists.sourceId = :sourceId) c " +
        "WHERE artists.sourceId = :sourceId AND artists.id = c.id " +
        "AND (artists.songCount != c.songCount OR artists.albumCount != c.albumCount " +
        "OR artists.playCount != c.playCount OR artists.played IS NOT c.played " +
        "OR artists.credited != c.credited)"

internal val ALBUMS_FOR_ARTIST_DOWNLOADED =
    "SELECT * FROM ($DOWNLOADED_ALBUM_DETAILS_FOR_ARTIST) ORDER BY year DESC, sortName COLLATE NOCASE, id"

/** How much of one server's library the mirror holds. */
data class LibraryCounts(
    val artists: Long = 0,
    val albums: Long = 0,
    val songs: Long = 0,
    val playlists: Long = 0,
)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
interface LibraryDao {
    @Upsert
    suspend fun upsertArtists(items: List<Artist>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM artists WHERE sourceId = :sourceId) AS artists, " +
            "(SELECT COUNT(*) FROM albums WHERE sourceId = :sourceId) AS albums, " +
            "(SELECT COUNT(*) FROM songs WHERE sourceId = :sourceId) AS songs, " +
            "(SELECT COUNT(*) FROM playlists WHERE sourceId = :sourceId) AS playlists",
    )
    fun libraryCounts(sourceId: Long): Flow<LibraryCounts>

    @Upsert
    suspend fun upsertAlbums(items: List<Album>)

    @Upsert
    suspend fun upsertPlaylists(items: List<Playlist>)

    @Upsert
    suspend fun upsertSongs(items: List<Song>)

    @Upsert
    suspend fun upsertPlaylistSongs(items: List<PlaylistSong>)

    @Query(
        "UPDATE albums SET " +
            "playCount = COALESCE((SELECT SUM(s.playCount) FROM songs s " +
            "WHERE s.sourceId = albums.sourceId AND s.albumId = albums.id), 0), " +
            "played = (SELECT MAX(s.played) FROM songs s " +
            "WHERE s.sourceId = albums.sourceId AND s.albumId = albums.id) " +
            "WHERE sourceId = :sourceId AND (" +
            "playCount IS NOT COALESCE((SELECT SUM(s.playCount) FROM songs s " +
            "WHERE s.sourceId = albums.sourceId AND s.albumId = albums.id), 0) " +
            "OR played IS NOT (SELECT MAX(s.played) FROM songs s " +
            "WHERE s.sourceId = albums.sourceId AND s.albumId = albums.id))",
    )
    suspend fun recomputeAlbumPlayData(sourceId: Long)

    @RawQuery
    suspend fun recomputeArtistAggregates(query: RoomRawQuery): Int

    suspend fun recomputeArtistAggregates(sourceId: Long) {
        recomputeArtistAggregates(rawQuery(ARTIST_AGGREGATES_SQL, mapOf("sourceId" to sourceId)))
    }

    @RawQuery
    suspend fun recomputeArtistArt(query: RoomRawQuery): Int

    suspend fun recomputeArtistArt(sourceId: Long) {
        recomputeArtistArt(rawQuery(ARTIST_ART_UPDATE_SQL, mapOf("sourceId" to sourceId)))
    }

    @RawQuery(observedEntities = [Album::class, AlbumArtistRef::class, Song::class, SongArtistRef::class])
    fun artistCoverArts(query: RoomRawQuery): Flow<List<String>>

    fun artistCoverArts(
        sourceId: Long,
        artistId: String,
    ): Flow<List<String>> = artistCoverArts(rawQuery(ARTIST_COVER_ARTS_SQL, mapOf("sourceId" to sourceId, "artistId" to artistId)))

    @RawQuery
    suspend fun recomputeDownloadedArtistArt(query: RoomRawQuery): Int

    // Room runs a @RawQuery on a read-only pooled connection; the transaction moves this UPDATE to the writer.
    @Transaction
    suspend fun recomputeDownloadedArtistArt(
        sourceId: Long,
        artistIds: List<String>,
    ) {
        recomputeDownloadedArtistArt(downloadedArtistArtQuery(sourceId, artistIds))
    }

    @Query("UPDATE artists SET downloadedCoverArts = '' WHERE sourceId = :sourceId AND downloadedCoverArts != ''")
    suspend fun clearDownloadedArtistArt(sourceId: Long)

    @Transaction
    suspend fun recomputeAggregates(sourceId: Long) {
        recomputeAlbumPlayData(sourceId)
        recomputeArtistAggregates(sourceId)
        recomputeArtistArt(sourceId)
    }

    /** Snapshots a play of a mirrored song into `play_events`; an unknown song records nothing. */
    @Query(
        "INSERT INTO play_events (sourceId, songId, playedAt, title, artist, album, durationMs) " +
            "SELECT sourceId, id, :at, title, artist, album, " +
            "CASE WHEN duration > 0 THEN duration * 1000 ELSE NULL END " +
            "FROM songs WHERE sourceId = :sourceId AND id = :songId",
    )
    suspend fun insertPlayEvent(
        sourceId: Long,
        songId: String,
        at: Long,
    )

    @Query(
        "UPDATE songs SET playCount = playCount + 1, played = MAX(COALESCE(played, :at), :at) " +
            "WHERE sourceId = :sourceId AND id = :songId",
    )
    suspend fun bumpSongPlay(
        sourceId: Long,
        songId: String,
        at: Long,
    )

    @Query(
        "UPDATE albums SET playCount = playCount + 1, played = MAX(COALESCE(played, :at), :at) " +
            "WHERE sourceId = :sourceId AND id = :albumId",
    )
    suspend fun bumpAlbumPlay(
        sourceId: Long,
        albumId: String,
        at: Long,
    )

    @Query(
        "UPDATE artists SET playCount = playCount + 1, played = MAX(COALESCE(played, :at), :at) " +
            "WHERE sourceId = :sourceId AND (" +
            "id = (SELECT artistId FROM albums WHERE sourceId = :sourceId AND id = :albumId) " +
            "OR id IN (SELECT artistId FROM album_artists WHERE sourceId = :sourceId AND albumId = :albumId) " +
            "OR id = (SELECT artistId FROM songs WHERE sourceId = :sourceId AND id = :songId) " +
            "OR id IN (SELECT artistId FROM song_artists WHERE sourceId = :sourceId AND songId = :songId))",
    )
    suspend fun bumpArtistPlay(
        sourceId: Long,
        albumId: String?,
        songId: String,
        at: Long,
    )

    @Query("SELECT albumId FROM songs WHERE sourceId = :sourceId AND id = :songId")
    suspend fun albumIdForSong(
        sourceId: Long,
        songId: String,
    ): String?

    @Query("SELECT DISTINCT artistId FROM ($CREDITED_SONG_ROWS) WHERE songId IN (:songIds)")
    suspend fun creditedArtistIdsForSongs(
        sourceId: Long,
        songIds: List<String>,
    ): List<String>

    // Counted locally when a scrobble is submitted, so the play sorts move before the next sync; the
    // sync then overwrites with the server's own number. The play is also kept as a local event.
    @Transaction
    suspend fun recordPlay(
        sourceId: Long,
        songId: String,
        at: Long,
    ) {
        insertPlayEvent(sourceId, songId, at)
        bumpSongPlay(sourceId, songId, at)
        val albumId = albumIdForSong(sourceId, songId)
        if (albumId != null) bumpAlbumPlay(sourceId, albumId, at)
        bumpArtistPlay(sourceId, albumId, songId, at)
    }

    // Albums, not songs: these flows re-run on every write to their table, and songs are written
    // one batch at a time during a sync.
    @Query("SELECT EXISTS(SELECT 1 FROM albums WHERE sourceId = :sourceId AND playCount > 0)")
    fun hasAlbumPlayCount(sourceId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM albums WHERE sourceId = :sourceId AND played IS NOT NULL)")
    fun hasAlbumPlayed(sourceId: Long): Flow<Boolean>

    // Whether each Library list has anything to show offline. They use the downloaded lists' own filters (with no
    // search or starred filter), so an entry is hidden exactly when its list would be empty. The flows observe songs
    // and downloads, so collect them only while offline, when neither a sync nor a download writes.
    @Query("SELECT EXISTS(SELECT 1 $ALBUMS_FILTER_DOWNLOADED)")
    fun hasDownloadedAlbums(
        sourceId: Long,
        starredFilter: Int,
        search: String,
    ): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 $ARTISTS_FILTER_DOWNLOADED)")
    fun hasDownloadedArtists(
        sourceId: Long,
        starredFilter: Int,
        search: String,
    ): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 $PLAYLISTS_FILTER_DOWNLOADED)")
    fun hasDownloadedPlaylists(
        sourceId: Long,
        search: String,
    ): Flow<Boolean>

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND played IS NOT NULL " +
            "ORDER BY played DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun recentlyPlayedAlbums(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Album>>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND played IS NOT NULL AND $ARTIST_CREDITED " +
            "ORDER BY played DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun recentlyPlayedArtists(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Artist>>

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND playCount > 0 " +
            "ORDER BY playCount DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun mostPlayedAlbums(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Album>>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND playCount > 0 AND $ARTIST_CREDITED " +
            "ORDER BY playCount DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun mostPlayedArtists(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Artist>>

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId " +
            "ORDER BY created DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun recentlyAddedAlbums(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Album>>

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND starred IS NOT NULL " +
            "ORDER BY starred DESC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun favoriteAlbums(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Album>>

    // Home's Your playlists: most recently changed first, falling back to the creation date when the server sends
    // no change date (as iOS does). Playlists are few, so no index is needed for the expression.
    @Query(
        "SELECT * FROM playlists WHERE sourceId = :sourceId " +
            "ORDER BY CASE WHEN changed > 0 THEN changed ELSE created END DESC, name COLLATE NOCASE, id LIMIT :limit",
    )
    fun recentlyChangedPlaylists(
        sourceId: Long,
        limit: Int,
    ): Flow<List<Playlist>>

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND played IS NOT NULL AND played < :cutoff " +
            "ORDER BY played ASC, sortName COLLATE NOCASE, id LIMIT :limit",
    )
    fun rediscoverAlbums(
        sourceId: Long,
        cutoff: Long,
        limit: Int,
    ): Flow<List<Album>>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND songs.starred IS NOT NULL " +
            "ORDER BY songs.starred DESC, songs.sortName COLLATE NOCASE, songs.id LIMIT :limit",
    )
    fun recentlyStarredSongs(
        sourceId: Long,
        limit: Int,
    ): Flow<List<AlbumSongItem>>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND songs.played IS NOT NULL " +
            "ORDER BY songs.played DESC, songs.id LIMIT 8",
    )
    fun songPathRecent(sourceId: Long): Flow<List<AlbumSongItem>>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND songs.playCount > 0 " +
            "ORDER BY songs.playCount DESC, songs.id LIMIT 8",
    )
    fun songPathTop(sourceId: Long): Flow<List<AlbumSongItem>>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND (:exclude IS NULL OR songs.id != :exclude) ORDER BY RANDOM() LIMIT 1",
    )
    suspend fun songPathSurprise(
        sourceId: Long,
        exclude: String?,
    ): AlbumSongItem?

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND $ALBUM_SEARCH_FILTER " +
            "AND (:downloaded = 0 OR $SEARCH_ALBUM_DOWNLOADED) ORDER BY $ALBUM_SEARCH_RANK, $ALBUM_ORDER_BY_NAME_SORT_MODE LIMIT :limit",
    )
    fun searchAlbums(
        sourceId: Long,
        search: String,
        limit: Int,
        sortMode: String = "",
        downloaded: Boolean = false,
    ): Flow<List<Album>>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND $ARTIST_SEARCH_FILTER AND $ARTIST_CREDITED " +
            "AND (:downloaded = 0 OR $SEARCH_ARTIST_DOWNLOADED) ORDER BY $ARTIST_SEARCH_RANK, $ARTIST_ORDER_BY_NAME_SORT_MODE LIMIT :limit",
    )
    fun searchArtists(
        sourceId: Long,
        search: String,
        limit: Int,
        sortMode: String = "",
        downloaded: Boolean = false,
    ): Flow<List<Artist>>

    @Query(
        "SELECT * FROM playlists WHERE sourceId = :sourceId AND $PLAYLIST_SEARCH_FILTER " +
            "AND (:downloaded = 0 OR $SEARCH_PLAYLIST_DOWNLOADED) ORDER BY $PLAYLIST_SEARCH_RANK, name COLLATE NOCASE, id LIMIT :limit",
    )
    fun searchPlaylists(
        sourceId: Long,
        search: String,
        limit: Int,
        downloaded: Boolean = false,
    ): Flow<List<Playlist>>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND $SONG_SEARCH_FILTER " +
            "AND (:downloaded = 0 OR $SEARCH_SONG_DOWNLOADED) ORDER BY $SONG_SEARCH_RANK, $SONG_SEARCH_ORDER_SORT_MODE LIMIT :limit",
    )
    fun searchSongs(
        sourceId: Long,
        search: String,
        limit: Int,
        sortMode: String = "",
        downloaded: Boolean = false,
    ): Flow<List<AlbumSongItem>>

    @Query(
        "SELECT song_genres.genre FROM song_genres " +
            "JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId " +
            "WHERE song_genres.sourceId = :sourceId " +
            "GROUP BY song_genres.genre ORDER BY SUM(songs.playCount) DESC, song_genres.genre COLLATE NOCASE",
    )
    fun genresByMostPlayed(sourceId: Long): Flow<List<String>>

    @Query(
        "SELECT DISTINCT (year / 10) * 10 AS decade FROM albums " +
            "WHERE sourceId = :sourceId AND year IS NOT NULL AND year > 0 ORDER BY decade ASC",
    )
    fun decades(sourceId: Long): Flow<List<Long>>

    @Query("UPDATE artists SET starred = :starred WHERE sourceId = :sourceId AND id = :id")
    suspend fun setArtistStar(
        sourceId: Long,
        id: String,
        starred: Long?,
    )

    @Query("UPDATE albums SET starred = :starred WHERE sourceId = :sourceId AND id = :id")
    suspend fun setAlbumStar(
        sourceId: Long,
        id: String,
        starred: Long?,
    )

    @Query("UPDATE songs SET starred = :starred WHERE sourceId = :sourceId AND id = :id")
    suspend fun setSongStar(
        sourceId: Long,
        id: String,
        starred: Long?,
    )

    @Query("SELECT id FROM artists WHERE sourceId = :sourceId")
    suspend fun artistIds(sourceId: Long): List<String>

    @Query("SELECT id FROM artists WHERE sourceId = :sourceId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun artistIdsAfter(
        sourceId: Long,
        afterId: String,
        limit: Int,
    ): List<String>

    @Query("SELECT id FROM albums WHERE sourceId = :sourceId")
    suspend fun albumIds(sourceId: Long): List<String>

    @Query("SELECT id FROM albums WHERE sourceId = :sourceId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun albumIdsAfter(
        sourceId: Long,
        afterId: String,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT albumId, disc FROM discs WHERE sourceId = :sourceId " +
            "AND (albumId > :afterAlbumId OR (albumId = :afterAlbumId AND disc > :afterDisc)) " +
            "ORDER BY albumId, disc LIMIT :limit",
    )
    suspend fun discKeysAfter(
        sourceId: Long,
        afterAlbumId: String,
        afterDisc: Long,
        limit: Int,
    ): List<DiscKey>

    @Query("SELECT id FROM playlists WHERE sourceId = :sourceId")
    suspend fun playlistIds(sourceId: Long): List<String>

    @Query("SELECT id FROM playlists WHERE sourceId = :sourceId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun playlistIdsAfter(
        sourceId: Long,
        afterId: String,
        limit: Int,
    ): List<String>

    @Query("SELECT id FROM songs WHERE sourceId = :sourceId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun songIdsAfter(
        sourceId: Long,
        afterId: String,
        limit: Int,
    ): List<String>

    @Query("DELETE FROM artists WHERE sourceId = :sourceId AND id IN (:ids)")
    suspend fun deleteArtists(
        sourceId: Long,
        ids: Collection<String>,
    )

    @Query("DELETE FROM albums WHERE sourceId = :sourceId AND id IN (:ids)")
    suspend fun deleteAlbums(
        sourceId: Long,
        ids: Collection<String>,
    )

    @Query("DELETE FROM discs WHERE sourceId = :sourceId AND albumId = :albumId AND disc = :disc")
    suspend fun deleteDisc(
        sourceId: Long,
        albumId: String,
        disc: Long,
    )

    @Query("DELETE FROM playlists WHERE sourceId = :sourceId AND id IN (:ids)")
    suspend fun deletePlaylists(
        sourceId: Long,
        ids: Collection<String>,
    )

    @Query("DELETE FROM songs WHERE sourceId = :sourceId AND id IN (:ids)")
    suspend fun deleteSongs(
        sourceId: Long,
        ids: Collection<String>,
    )

    /** Every entry's song in order, including songs the mirror has no row for, so indices are server positions. */
    @Query("SELECT songId FROM playlist_songs WHERE sourceId = :sourceId AND playlistId = :playlistId ORDER BY position")
    suspend fun playlistEntrySongIds(
        sourceId: Long,
        playlistId: String,
    ): List<String>

    @Query("DELETE FROM playlist_songs WHERE sourceId = :sourceId AND playlistId = :playlistId AND position >= :position")
    suspend fun deletePlaylistSongsFrom(
        sourceId: Long,
        playlistId: String,
        position: Long,
    )

    @Query(
        "DELETE FROM playlist_songs WHERE sourceId = :sourceId " +
            "AND playlistId NOT IN (SELECT id FROM playlists WHERE sourceId = :sourceId)",
    )
    suspend fun deleteOrphanPlaylistSongs(sourceId: Long)

    /**
     * Playlists songs can be added to, by name: the user's own (or ones whose owner the server did not say), minus
     * [exclude], matching [pattern] (a LIKE pattern with `\` as escape; `%` for all).
     */
    @Query(
        "SELECT * FROM playlists WHERE sourceId = :sourceId AND (owner IS NULL OR :owner IS NULL OR owner = :owner) " +
            "AND id NOT IN (:exclude) AND name LIKE :pattern ESCAPE '\\' ORDER BY name, id",
    )
    fun playlistDestinations(
        sourceId: Long,
        owner: String?,
        exclude: List<String>,
        pattern: String,
    ): PagingSource<Int, Playlist>

    /** Mirrors one playlist exactly as the server now holds it, after an edit. */
    @Transaction
    suspend fun replacePlaylist(
        playlist: Playlist,
        songs: List<PlaylistSong>,
    ) {
        upsertPlaylists(listOf(playlist))
        deletePlaylistSongsFrom(playlist.sourceId, playlist.id, 0)
        if (songs.isNotEmpty()) upsertPlaylistSongs(songs)
    }

    @Transaction
    suspend fun removePlaylist(
        sourceId: Long,
        playlistId: String,
    ) {
        deletePlaylistSongsFrom(sourceId, playlistId, 0)
        deletePlaylists(sourceId, listOf(playlistId))
    }

    @RawQuery(observedEntities = [Album::class])
    fun albumsPaging(query: RoomRawQuery): PagingSource<Int, Album>

    // The fallback art reads albums and credits, but a successful sync writes sources after the
    // library changes, so observing artists and sources avoids invalidating on every song/album write.
    @RawQuery(observedEntities = [Artist::class, Source::class])
    fun artistsPaging(query: RoomRawQuery): PagingSource<Int, Artist>

    @Query(
        """
        INSERT OR REPLACE INTO playlist_usage (sourceId, playlistId, usedAt)
        SELECT :sourceId, :playlistId, MAX(:usedAt, COALESCE((
            SELECT usedAt FROM playlist_usage WHERE sourceId = :sourceId AND playlistId = :playlistId
        ), :usedAt))
        WHERE EXISTS (SELECT 1 FROM playlists WHERE sourceId = :sourceId AND id = :playlistId)
        """,
    )
    suspend fun recordPlaylistUse(
        sourceId: Long,
        playlistId: String,
        usedAt: Long,
    )

    @RawQuery(observedEntities = [Playlist::class, PlaylistUsage::class])
    fun playlistsPaging(query: RoomRawQuery): PagingSource<Int, Playlist>

    @RawQuery(observedEntities = [Song::class, Album::class, SongSearch::class])
    fun songsPaging(query: RoomRawQuery): PagingSource<Int, AlbumSongItem>

    @RawQuery(
        observedEntities = [
            Album::class, Artist::class, Song::class, SongDownload::class, Source::class,
            SongArtistRef::class, AlbumArtistRef::class,
        ],
    )
    fun letterBuckets(query: RoomRawQuery): Flow<List<LetterBucket>>

    @RawQuery(observedEntities = [SongGenre::class, Song::class, Album::class, AlbumLabel::class, SongDownload::class])
    fun albumCollectionsPaging(query: RoomRawQuery): PagingSource<Int, AlbumCollectionSummary>

    @RawQuery(observedEntities = [SongGenre::class, Song::class, Album::class, AlbumLabel::class, SongDownload::class])
    fun collectionAlbumsPaging(query: RoomRawQuery): PagingSource<Int, Album>

    @RawQuery(observedEntities = [SongGenre::class, Song::class, Album::class, AlbumLabel::class, SongDownload::class])
    fun hasDownloadedCollections(query: RoomRawQuery): Flow<Boolean>

    @RawQuery(observedEntities = [Song::class, Album::class, SongDownload::class, SongSearch::class])
    fun downloadedSongsPaging(query: RoomRawQuery): PagingSource<Int, AlbumSongItem>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM song_downloads sd JOIN songs ON songs.sourceId = sd.sourceId AND songs.id = sd.songId " +
            "WHERE sd.sourceId = :sourceId AND sd.status = 'Completed')",
    )
    fun hasDownloadedSongs(sourceId: Long): Flow<Boolean>

    // These observe every table the statements read; the sets match what Room inferred for the
    // old downloaded @Query methods, so download and library writes invalidate as before.
    @RawQuery(observedEntities = [Album::class, Song::class, SongDownload::class, AlbumSearch::class])
    fun downloadedAlbumsPaging(query: RoomRawQuery): PagingSource<Int, Album>

    @RawQuery(
        observedEntities = [
            Album::class,
            Song::class,
            SongDownload::class,
            AlbumArtistRef::class,
            SongArtistRef::class,
            Artist::class,
            Source::class,
            ArtistSearch::class,
        ],
    )
    fun downloadedArtistsPaging(query: RoomRawQuery): PagingSource<Int, Artist>

    @RawQuery(observedEntities = [PlaylistSong::class, SongDownload::class, Playlist::class, PlaylistSearch::class, PlaylistUsage::class])
    fun downloadedPlaylistsPaging(query: RoomRawQuery): PagingSource<Int, Playlist>

    @Query("SELECT * FROM albums WHERE sourceId = :sourceId AND played IS NOT NULL ORDER BY played DESC, sortName COLLATE NOCASE, id")
    fun homeRecentlyPlayedAlbums(sourceId: Long): PagingSource<Int, Album>

    @Query("SELECT * FROM albums WHERE sourceId = :sourceId AND playCount > 0 ORDER BY playCount DESC, sortName COLLATE NOCASE, id")
    fun homeMostPlayedAlbums(sourceId: Long): PagingSource<Int, Album>

    @Query("SELECT * FROM albums WHERE sourceId = :sourceId ORDER BY created DESC, sortName COLLATE NOCASE, id")
    fun homeRecentlyAddedAlbums(sourceId: Long): PagingSource<Int, Album>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND played IS NOT NULL AND $ARTIST_CREDITED " +
            "ORDER BY played DESC, sortName COLLATE NOCASE, id",
    )
    fun homeRecentlyPlayedArtists(sourceId: Long): PagingSource<Int, Artist>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND playCount > 0 AND $ARTIST_CREDITED " +
            "ORDER BY playCount DESC, sortName COLLATE NOCASE, id",
    )
    fun homeMostPlayedArtists(sourceId: Long): PagingSource<Int, Artist>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE songs.sourceId = :sourceId AND songs.starred IS NOT NULL " +
            "ORDER BY songs.starred DESC, songs.sortName COLLATE NOCASE, songs.id",
    )
    fun starredSongs(sourceId: Long): PagingSource<Int, AlbumSongItem>

    @Query(
        "SELECT songs.id FROM songs WHERE sourceId = :sourceId AND starred IS NOT NULL " +
            "ORDER BY starred DESC, sortName COLLATE NOCASE, id",
    )
    suspend fun starredSongIds(sourceId: Long): List<String>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "JOIN song_downloads d ON d.sourceId = songs.sourceId AND d.songId = songs.id " +
            "WHERE songs.sourceId = :sourceId AND d.status = 'Completed' " +
            "ORDER BY d.downloadedAt DESC, songs.sortName COLLATE NOCASE, songs.id LIMIT :limit",
    )
    fun downloadedSongs(
        sourceId: Long,
        limit: Int,
    ): Flow<List<AlbumSongItem>>

    @Query(
        "SELECT songs.id FROM songs " +
            "JOIN song_downloads d ON d.sourceId = songs.sourceId AND d.songId = songs.id " +
            "WHERE songs.sourceId = :sourceId AND d.status = 'Completed' " +
            "ORDER BY d.downloadedAt DESC, songs.sortName COLLATE NOCASE, songs.id",
    )
    suspend fun downloadedSongIds(sourceId: Long): List<String>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM songs " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "JOIN song_downloads d ON d.sourceId = songs.sourceId AND d.songId = songs.id " +
            "WHERE songs.sourceId = :sourceId AND d.status = 'Completed' " +
            "ORDER BY d.downloadedAt DESC, songs.sortName COLLATE NOCASE, songs.id",
    )
    fun homeDownloadedSongs(sourceId: Long): PagingSource<Int, AlbumSongItem>

    @Query(
        "SELECT albums.* FROM albums " +
            "JOIN songs ON songs.sourceId = albums.sourceId AND songs.albumId = albums.id " +
            "JOIN song_downloads d ON d.sourceId = songs.sourceId AND d.songId = songs.id AND d.status = 'Completed' " +
            "WHERE albums.sourceId = :sourceId " +
            "GROUP BY albums.sourceId, albums.id " +
            "ORDER BY MAX(d.downloadedAt) DESC, albums.sortName COLLATE NOCASE, albums.id",
    )
    fun downloadedAlbums(sourceId: Long): PagingSource<Int, Album>

    @Query(
        "SELECT $ARTIST_COLUMNS FROM artists " +
            "JOIN (SELECT id, MAX(downloadedAt) AS lastDownloadedAt FROM ($DOWNLOADED_ARTIST_ROWS) " +
            "WHERE status = 'Completed' GROUP BY id) dl ON dl.id = artists.id " +
            "WHERE artists.sourceId = :sourceId AND $ARTIST_CREDITED " +
            "ORDER BY dl.lastDownloadedAt DESC, artists.sortName COLLATE NOCASE, artists.id",
    )
    fun downloadedArtists(sourceId: Long): PagingSource<Int, Artist>

    @Query(
        "SELECT songs.*, albums.coverArt AS coverArt FROM song_genres " +
            "JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId " +
            "LEFT JOIN albums ON albums.sourceId = songs.sourceId AND albums.id = songs.albumId " +
            "WHERE song_genres.sourceId = :sourceId AND song_genres.genre = :genre " +
            "ORDER BY songs.sortName COLLATE NOCASE, songs.id",
    )
    fun songsByGenre(
        sourceId: Long,
        genre: String,
    ): PagingSource<Int, AlbumSongItem>

    @Query(
        "SELECT songs.id FROM song_genres " +
            "JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId " +
            "WHERE song_genres.sourceId = :sourceId AND song_genres.genre = :genre " +
            "ORDER BY songs.sortName COLLATE NOCASE, songs.id",
    )
    suspend fun songsByGenreIds(
        sourceId: Long,
        genre: String,
    ): List<String>

    @Query(
        "SELECT COUNT(*) FROM song_genres " +
            "JOIN songs ON songs.sourceId = song_genres.sourceId AND songs.id = song_genres.songId " +
            "WHERE song_genres.sourceId = :sourceId AND song_genres.genre = :genre " +
            "AND (songs.sortName COLLATE NOCASE < :sortName OR (songs.sortName COLLATE NOCASE = :sortName AND songs.id < :id))",
    )
    suspend fun genreSongOrdinal(
        sourceId: Long,
        genre: String,
        sortName: String,
        id: String,
    ): Long

    @Query(
        "SELECT COUNT(*) FROM song_downloads d " +
            "JOIN songs s ON s.sourceId = d.sourceId AND s.id = d.songId " +
            "WHERE d.sourceId = :sourceId AND d.status = 'Completed' " +
            "AND (d.downloadedAt > (SELECT downloadedAt FROM song_downloads WHERE sourceId = :sourceId AND songId = :id) " +
            "OR (d.downloadedAt = (SELECT downloadedAt FROM song_downloads WHERE sourceId = :sourceId AND songId = :id) " +
            "AND (s.sortName COLLATE NOCASE < (SELECT sortName FROM songs WHERE sourceId = :sourceId AND id = :id) " +
            "OR (s.sortName COLLATE NOCASE = (SELECT sortName FROM songs WHERE sourceId = :sourceId AND id = :id) AND s.id < :id))))",
    )
    suspend fun downloadedSongOrdinal(
        sourceId: Long,
        id: String,
    ): Long

    @Query(
        "SELECT COUNT(*) FROM songs s " +
            "WHERE s.sourceId = :sourceId AND s.starred IS NOT NULL " +
            "AND (s.starred > (SELECT starred FROM songs WHERE sourceId = :sourceId AND id = :id) " +
            "OR (s.starred = (SELECT starred FROM songs WHERE sourceId = :sourceId AND id = :id) " +
            "AND (s.sortName COLLATE NOCASE < (SELECT sortName FROM songs WHERE sourceId = :sourceId AND id = :id) " +
            "OR (s.sortName COLLATE NOCASE = (SELECT sortName FROM songs WHERE sourceId = :sourceId AND id = :id) AND s.id < :id))))",
    )
    suspend fun starredSongOrdinal(
        sourceId: Long,
        id: String,
    ): Long

    @Query(
        "SELECT * FROM albums WHERE sourceId = :sourceId AND year >= :start AND year < :end " +
            "ORDER BY year DESC, sortName COLLATE NOCASE, id",
    )
    fun albumsByDecade(
        sourceId: Long,
        start: Long,
        end: Long,
    ): PagingSource<Int, Album>

    @Query(PLAYLIST_SONGS_SQL)
    fun playlistSongs(
        sourceId: Long,
        playlistId: String,
    ): PagingSource<Int, PlaylistSongItem>

    @Query("SELECT * FROM albums WHERE sourceId = :sourceId AND id = :albumId")
    fun album(
        sourceId: Long,
        albumId: String,
    ): Flow<Album?>

    @Query("SELECT * FROM albums WHERE sourceId = :sourceId AND id = :albumId")
    suspend fun albumOnce(
        sourceId: Long,
        albumId: String,
    ): Album?

    @Query("SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND id = :artistId")
    suspend fun artistOnce(
        sourceId: Long,
        artistId: String,
    ): Artist?

    @Query("SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND musicBrainzId = :mbid COLLATE NOCASE LIMIT 2")
    suspend fun artistsByMusicBrainzId(
        sourceId: Long,
        mbid: String,
    ): List<Artist>

    @Query("SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND name = :name COLLATE NOCASE LIMIT 2")
    suspend fun artistsByExactName(
        sourceId: Long,
        name: String,
    ): List<Artist>

    @Query("SELECT $ARTIST_COLUMNS FROM artists WHERE sourceId = :sourceId AND id = :artistId")
    fun artist(
        sourceId: Long,
        artistId: String,
    ): Flow<Artist?>

    @Query("SELECT * FROM artist_bios WHERE sourceId = :sourceId AND artistId = :artistId")
    fun artistBio(
        sourceId: Long,
        artistId: String,
    ): Flow<ArtistBio?>

    @Query("SELECT * FROM artist_bios WHERE sourceId = :sourceId AND artistId = :artistId")
    suspend fun artistBioOnce(
        sourceId: Long,
        artistId: String,
    ): ArtistBio?

    @Upsert
    suspend fun upsertArtistBio(item: ArtistBio)

    // A credited track artist may not have an artists row; its name lives on song_artists or the songs.
    @Query(
        "SELECT COALESCE(" +
            "(SELECT name FROM song_artists WHERE sourceId = :sourceId AND artistId = :artistId " +
            "AND name != '' LIMIT 1), " +
            "(SELECT artist FROM songs WHERE sourceId = :sourceId AND artistId = :artistId " +
            "AND artist IS NOT NULL AND artist != '' LIMIT 1))",
    )
    fun artistName(
        sourceId: Long,
        artistId: String,
    ): Flow<String?>

    @Query("SELECT * FROM songs WHERE sourceId = :sourceId AND id = :songId")
    fun song(
        sourceId: Long,
        songId: String,
    ): Flow<Song?>

    @Query("SELECT * FROM songs WHERE sourceId = :sourceId AND id = :songId")
    suspend fun songOnce(
        sourceId: Long,
        songId: String,
    ): Song?

    @Query("SELECT genre FROM song_genres WHERE sourceId = :sourceId AND songId = :songId ORDER BY position")
    fun songGenres(
        sourceId: Long,
        songId: String,
    ): Flow<List<String>>

    @RawQuery(observedEntities = [Album::class, AlbumArtistRef::class, Song::class, SongArtistRef::class])
    fun albumsForArtist(query: RoomRawQuery): Flow<List<Album>>

    fun albumsForArtist(
        sourceId: Long,
        artistId: String,
    ): Flow<List<Album>> = albumsForArtist(rawQuery(ALBUMS_FOR_ARTIST, mapOf("sourceId" to sourceId, "artistId" to artistId)))

    @RawQuery(observedEntities = [Album::class, AlbumArtistRef::class, Song::class, SongArtistRef::class])
    fun albumCreditAlbumIds(query: RoomRawQuery): Flow<List<String>>

    fun albumCreditAlbumIds(
        sourceId: Long,
        artistId: String,
    ): Flow<List<String>> = albumCreditAlbumIds(rawQuery(ALBUM_CREDIT_ALBUM_IDS, mapOf("sourceId" to sourceId, "artistId" to artistId)))

    @RawQuery(observedEntities = [AlbumReleaseType::class, Album::class, AlbumArtistRef::class, Song::class, SongArtistRef::class])
    fun artistReleaseTypes(query: RoomRawQuery): Flow<List<AlbumReleaseType>>

    /** The release types of the albums filed under the artist (its discography shelves), in each album's order. */
    fun artistReleaseTypes(
        sourceId: Long,
        artistId: String,
    ): Flow<List<AlbumReleaseType>> =
        artistReleaseTypes(
            rawQuery(
                "SELECT * FROM album_release_types WHERE sourceId = :sourceId AND albumId IN ($ALBUM_CREDIT_ALBUM_IDS) " +
                    "ORDER BY albumId, position",
                mapOf("sourceId" to sourceId, "artistId" to artistId),
            ),
        )

    @Query("SELECT * FROM discs WHERE sourceId = :sourceId AND albumId = :albumId ORDER BY disc")
    fun discs(
        sourceId: Long,
        albumId: String,
    ): Flow<List<Disc>>

    @Query("SELECT name FROM album_labels WHERE sourceId = :sourceId AND albumId = :albumId ORDER BY position")
    fun albumLabels(
        sourceId: Long,
        albumId: String,
    ): Flow<List<String>>

    // The album page's genre chips: its songs' tags grouped the way the Genres list groups them, most-tagged first.
    // With downloadedOnly, only downloaded songs count, so each chip's collection offline still holds this album.
    @Query(
        "SELECT MIN(sg.genre) FROM song_genres sg " +
            "JOIN songs ON songs.sourceId = sg.sourceId AND songs.id = sg.songId " +
            "WHERE songs.sourceId = :sourceId AND songs.albumId = :albumId AND trim(sg.genre) != '' " +
            "AND (NOT :downloadedOnly OR EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = songs.sourceId " +
            "AND sd.songId = songs.id AND sd.status = 'Completed')) " +
            "GROUP BY sg.genre COLLATE NOCASE ORDER BY COUNT(DISTINCT sg.songId) DESC, MIN(sg.genre) COLLATE NOCASE " +
            "LIMIT :limit",
    )
    fun albumGenres(
        sourceId: Long,
        albumId: String,
        downloadedOnly: Boolean,
        limit: Int,
    ): Flow<List<String>>

    @Query("SELECT type FROM album_release_types WHERE sourceId = :sourceId AND albumId = :albumId ORDER BY position")
    fun albumReleaseTypes(
        sourceId: Long,
        albumId: String,
    ): Flow<List<String>>

    @Query("SELECT * FROM playlists WHERE sourceId = :sourceId AND id = :playlistId")
    fun playlist(
        sourceId: Long,
        playlistId: String,
    ): Flow<Playlist?>

    @Query("SELECT * FROM songs WHERE sourceId = :sourceId AND albumId = :albumId ORDER BY disc, track, id")
    fun songsByAlbum(
        sourceId: Long,
        albumId: String,
    ): Flow<List<Song>>

    @Query(
        "SELECT songs.* FROM songs " +
            "JOIN song_downloads sd ON sd.sourceId = songs.sourceId AND sd.songId = songs.id AND sd.status = 'Completed' " +
            "WHERE songs.sourceId = :sourceId AND songs.albumId = :albumId " +
            "ORDER BY songs.disc, songs.track, songs.id",
    )
    fun songsByAlbumDownloaded(
        sourceId: Long,
        albumId: String,
    ): Flow<List<Song>>

    @Query("$PLAYLIST_SONGS_SELECT AND $DOWNLOADED_SONG$PLAYLIST_SONGS_ORDER")
    fun playlistSongsDownloaded(
        sourceId: Long,
        playlistId: String,
    ): PagingSource<Int, PlaylistSongItem>

    // The playlist footer: the same rows as playlistSongs (or playlistSongsDownloaded), counted rather than loaded.
    @Query(
        "SELECT COUNT(*) AS songCount, COALESCE(SUM(songs.duration), 0) AS duration FROM playlist_songs " +
            "JOIN songs ON songs.sourceId = playlist_songs.sourceId AND songs.id = playlist_songs.songId " +
            "WHERE playlist_songs.sourceId = :sourceId AND playlist_songs.playlistId = :playlistId " +
            "AND (NOT :downloadedOnly OR $DOWNLOADED_SONG)",
    )
    fun playlistTotals(
        sourceId: Long,
        playlistId: String,
        downloadedOnly: Boolean,
    ): Flow<ListTotals>

    @RawQuery(
        observedEntities = [Album::class, Song::class, SongDownload::class, AlbumArtistRef::class, SongArtistRef::class],
    )
    fun albumsForArtistDownloaded(query: RoomRawQuery): Flow<List<Album>>

    fun albumsForArtistDownloaded(
        sourceId: Long,
        artistId: String,
    ): Flow<List<Album>> =
        albumsForArtistDownloaded(
            rawQuery(ALBUMS_FOR_ARTIST_DOWNLOADED, mapOf("sourceId" to sourceId, "artistId" to artistId)),
        )
}
