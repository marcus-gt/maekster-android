package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query

data class AudioMuseLocalTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
)

private const val ELIGIBLE =
    "s.sourceId = :sourceId AND (:downloadedOnly = 0 OR EXISTS " +
        "(SELECT 1 FROM song_downloads d WHERE d.sourceId = s.sourceId AND d.songId = s.id AND d.status = 'Completed'))"

@Dao
interface AudioMuseTrackDao {
    @Query("SELECT s.id, s.title, s.artist, s.album FROM songs s WHERE $ELIGIBLE AND s.id IN (:ids)")
    suspend fun byIds(
        sourceId: Long,
        ids: List<String>,
        downloadedOnly: Boolean,
    ): List<AudioMuseLocalTrack>

    @Query("SELECT s.id, s.title, s.artist, s.album FROM songs s WHERE $ELIGIBLE AND s.id > :afterId ORDER BY s.id LIMIT :limit")
    suspend fun page(
        sourceId: Long,
        afterId: String,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<AudioMuseLocalTrack>
}
