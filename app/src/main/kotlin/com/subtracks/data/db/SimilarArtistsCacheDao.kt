package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.subtracks.data.model.CachedSimilarArtists

@Dao
interface SimilarArtistsCacheDao {
    @Upsert
    suspend fun put(entry: CachedSimilarArtists)

    @Query("SELECT * FROM similar_artists_cache WHERE sourceId = :sourceId AND artistId = :artistId")
    suspend fun get(
        sourceId: Long,
        artistId: String,
    ): CachedSimilarArtists?
}
