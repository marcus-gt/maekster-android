package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Query
import com.subtracks.data.model.PlayEvent

/** Reads the local play history; plays are written by [LibraryDao.recordPlay]. */
@Dao
interface PlayEventDao {
    /** Plays on [sourceId] in [from, to) epoch seconds, oldest first. */
    @Query(
        "SELECT * FROM play_events WHERE sourceId = :sourceId AND playedAt >= :from AND playedAt < :to " +
            "ORDER BY playedAt, id",
    )
    suspend fun between(
        sourceId: Long,
        from: Long,
        to: Long,
    ): List<PlayEvent>

    @Query("SELECT COUNT(*) FROM play_events WHERE sourceId = :sourceId")
    suspend fun count(sourceId: Long): Long
}
