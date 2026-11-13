package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.subtracks.data.model.CachedLyrics

@Dao
interface LyricsCacheDao {
    @Upsert
    suspend fun put(entry: CachedLyrics)

    @Query("SELECT * FROM lyrics_cache WHERE sourceId = :sourceId AND songId = :songId AND provider = :provider")
    suspend fun get(
        sourceId: Long,
        songId: String,
        provider: String,
    ): CachedLyrics?

    @Query("DELETE FROM lyrics_cache WHERE sourceId = :sourceId AND songId = :songId AND provider = :provider")
    suspend fun remove(
        sourceId: Long,
        songId: String,
        provider: String,
    )
}
