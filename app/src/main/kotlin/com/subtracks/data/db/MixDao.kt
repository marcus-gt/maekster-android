package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query

/** A song a mix may pick, with the artist used to keep any one artist from dominating. */
data class MixCandidate(
    val id: String,
    val artistId: String?,
)

/** A recent play, joined to its song for the seeds Smart Shuffle derives from it. */
data class RecentPlay(
    val songId: String,
    val artistId: String?,
    val genre: String?,
)

private const val DOWNLOADED =
    "(:downloadedOnly = 0 OR EXISTS (SELECT 1 FROM song_downloads d " +
        "WHERE d.sourceId = s.sourceId AND d.songId = s.id AND d.status = 'Completed'))"

/** Bounded, random reads from the mirror for Smart Shuffle and other local mixes. Never loads a whole table. */
@Dao
interface MixDao {
    @Query(
        "SELECT pe.songId AS songId, s.artistId AS artistId, " +
            "(SELECT g.genre FROM song_genres g WHERE g.sourceId = pe.sourceId AND g.songId = pe.songId " +
            "ORDER BY g.position LIMIT 1) AS genre FROM play_events pe " +
            "LEFT JOIN songs s ON s.sourceId = pe.sourceId AND s.id = pe.songId " +
            "WHERE pe.sourceId = :sourceId ORDER BY pe.playedAt DESC, pe.id DESC LIMIT :limit",
    )
    suspend fun recentPlays(
        sourceId: Long,
        limit: Int,
    ): List<RecentPlay>

    @Query(
        "SELECT s.id AS id, s.artistId AS artistId FROM songs s " +
            "WHERE s.sourceId = :sourceId AND s.starred IS NOT NULL AND $DOWNLOADED ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun starredSongs(
        sourceId: Long,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<MixCandidate>

    @Query("SELECT id FROM albums WHERE sourceId = :sourceId AND starred IS NOT NULL ORDER BY RANDOM() LIMIT :limit")
    suspend fun starredAlbumIds(
        sourceId: Long,
        limit: Int,
    ): List<String>

    @Query("SELECT id FROM artists WHERE sourceId = :sourceId AND starred IS NOT NULL ORDER BY RANDOM() LIMIT :limit")
    suspend fun starredArtistIds(
        sourceId: Long,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT s.id AS id, s.artistId AS artistId FROM songs s " +
            "WHERE s.sourceId = :sourceId AND s.albumId IN (:albumIds) AND $DOWNLOADED ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun songsOfAlbums(
        sourceId: Long,
        albumIds: List<String>,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<MixCandidate>

    @Query(
        "SELECT s.id AS id, s.artistId AS artistId FROM songs s " +
            "WHERE s.sourceId = :sourceId AND s.artistId IN (:artistIds) AND $DOWNLOADED ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun songsOfArtists(
        sourceId: Long,
        artistIds: List<String>,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<MixCandidate>

    @Query(
        "SELECT s.id AS id, s.artistId AS artistId FROM songs s " +
            "WHERE s.sourceId = :sourceId AND EXISTS (SELECT 1 FROM song_genres g " +
            "WHERE g.sourceId = s.sourceId AND g.songId = s.id AND g.genre IN (:genres)) " +
            "AND $DOWNLOADED ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun songsOfGenres(
        sourceId: Long,
        genres: List<String>,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<MixCandidate>

    @Query(
        "SELECT s.id AS id, s.artistId AS artistId FROM songs s " +
            "WHERE s.sourceId = :sourceId AND $DOWNLOADED ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomSongs(
        sourceId: Long,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<MixCandidate>
}
