package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.subtracks.data.model.AudioMuseConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioMuseConfigDao {
    @Upsert
    suspend fun put(config: AudioMuseConfig)

    @Query("SELECT * FROM audiomuse_config WHERE sourceId = :sourceId")
    suspend fun get(sourceId: Long): AudioMuseConfig?

    @Query("SELECT * FROM audiomuse_config WHERE sourceId = :sourceId")
    fun observe(sourceId: Long): Flow<AudioMuseConfig?>

    @Query("DELETE FROM audiomuse_config WHERE sourceId = :sourceId")
    suspend fun delete(sourceId: Long)
}
