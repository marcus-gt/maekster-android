package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.subtracks.data.model.SimilarSong

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
        "SELECT * FROM artists WHERE sourceId = :sourceId AND credited = 1 " +
            "ORDER BY (starred IS NOT NULL) DESC, played DESC, name COLLATE NOCASE, id LIMIT 12",
    )
    fun stationArtists(sourceId: Long): kotlinx.coroutines.flow.Flow<List<com.subtracks.data.model.Artist>>

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

    /** Which of [ids] the mirror holds (and, with [downloadedOnly], has downloaded); order not kept. */
    @Query("SELECT s.id AS id, s.artistId AS artistId FROM songs s WHERE s.sourceId = :sourceId AND s.id IN (:ids) AND $DOWNLOADED")
    suspend fun existing(
        sourceId: Long,
        ids: List<String>,
        downloadedOnly: Boolean,
    ): List<MixCandidate>

    @Query(
        "SELECT songId AS id, artistId FROM similar_songs " +
            "WHERE sourceId = :sourceId AND seedKind = :seedKind AND seedId = :seedId ORDER BY position",
    )
    suspend fun cachedSimilar(
        sourceId: Long,
        seedKind: String,
        seedId: String,
    ): List<MixCandidate>

    @Query("DELETE FROM similar_songs WHERE sourceId = :sourceId AND seedKind = :seedKind AND seedId = :seedId")
    suspend fun deleteSimilar(
        sourceId: Long,
        seedKind: String,
        seedId: String,
    )

    @Insert
    suspend fun insertSimilar(rows: List<SimilarSong>)

    /** Replaces one seed's cached answer, so a stale list never mixes with a new one. */
    @Transaction
    suspend fun replaceSimilar(
        sourceId: Long,
        seedKind: String,
        seedId: String,
        songs: List<MixCandidate>,
        fetchedAt: Long,
    ) {
        deleteSimilar(sourceId, seedKind, seedId)
        insertSimilar(
            songs.mapIndexed { position, song ->
                SimilarSong(sourceId, seedKind, seedId, position, song.id, song.artistId, fetchedAt)
            },
        )
    }

    @Query("SELECT artistId FROM songs WHERE sourceId = :sourceId AND id = :songId")
    suspend fun songArtist(
        sourceId: Long,
        songId: String,
    ): String?

    @Query("SELECT artistId FROM albums WHERE sourceId = :sourceId AND id = :albumId")
    suspend fun albumArtist(
        sourceId: Long,
        albumId: String,
    ): String?

    @Query(
        "SELECT DISTINCT genre FROM song_genres WHERE sourceId = :sourceId AND songId = :songId ORDER BY position LIMIT :limit",
    )
    suspend fun songGenres(
        sourceId: Long,
        songId: String,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT g.genre FROM song_genres g JOIN songs s ON s.sourceId = g.sourceId AND s.id = g.songId " +
            "WHERE g.sourceId = :sourceId AND s.albumId = :albumId GROUP BY g.genre ORDER BY COUNT(*) DESC LIMIT :limit",
    )
    suspend fun albumGenres(
        sourceId: Long,
        albumId: String,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT g.genre FROM song_genres g JOIN songs s ON s.sourceId = g.sourceId AND s.id = g.songId " +
            "WHERE g.sourceId = :sourceId AND s.artistId = :artistId GROUP BY g.genre ORDER BY COUNT(*) DESC LIMIT :limit",
    )
    suspend fun artistGenres(
        sourceId: Long,
        artistId: String,
        limit: Int,
    ): List<String>
}
