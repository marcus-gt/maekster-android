package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.subtracks.data.model.AudioMuseConfig
import com.subtracks.data.model.CachedAudioMuseScore

@Dao
interface AudioMuseScoreDao {
    @Query("SELECT * FROM audiomuse_config WHERE sourceId = :sourceId")
    suspend fun configuration(sourceId: Long): AudioMuseConfig?

    @Query("SELECT * FROM audiomuse_mood_scores WHERE sourceId = :sourceId AND configurationKey = :key AND songId IN (:songIds)")
    suspend fun read(
        sourceId: Long,
        key: String,
        songIds: List<String>,
    ): List<CachedAudioMuseScore>

    @Transaction
    suspend fun readIfCurrent(
        expected: AudioMuseConfig,
        key: String,
        songIds: List<String>,
    ): List<CachedAudioMuseScore> {
        val current = configuration(expected.sourceId) ?: return emptyList()
        if (current.url != expected.url || current.tokenCiphertext != expected.tokenCiphertext) return emptyList()
        return read(expected.sourceId, key, songIds)
    }

    @Upsert
    suspend fun put(score: CachedAudioMuseScore)

    @Transaction
    suspend fun putIfCurrent(
        expected: AudioMuseConfig,
        score: CachedAudioMuseScore,
    ): Boolean {
        require(expected.sourceId == score.sourceId)
        val current = configuration(expected.sourceId) ?: return false
        if (current.url != expected.url || current.tokenCiphertext != expected.tokenCiphertext) return false
        put(score)
        return true
    }
}
