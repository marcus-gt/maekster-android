package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.model.FreshReleaseState
import com.subtracks.data.model.MusicBrainzName
import kotlinx.coroutines.flow.Flow

/** A library artist the fresh-releases feed may follow. */
data class WatchArtist(
    val id: String,
    val name: String,
    val musicBrainzId: String?,
)

/** One album title by an album artist, for "already owned". */
data class OwnedAlbum(
    val artistId: String,
    val name: String,
)

@Dao
interface FreshReleaseDao {
    @Query("SELECT * FROM fresh_releases WHERE sourceId = :sourceId ORDER BY position")
    fun releases(sourceId: Long): Flow<List<FreshRelease>>

    @Query("SELECT * FROM fresh_release_state WHERE sourceId = :sourceId")
    suspend fun state(sourceId: Long): FreshReleaseState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: FreshReleaseState)

    @Query("DELETE FROM fresh_releases WHERE sourceId = :sourceId")
    suspend fun clearReleases(sourceId: Long)

    @Insert
    suspend fun insertReleases(releases: List<FreshRelease>)

    /** Replaces the server's feed in one step, so readers never see half of an old and half of a new result. */
    @Transaction
    suspend fun publish(
        sourceId: Long,
        releases: List<FreshRelease>,
        state: FreshReleaseState,
    ) {
        clearReleases(sourceId)
        if (releases.isNotEmpty()) insertReleases(releases)
        setState(state)
    }

    @Query("SELECT * FROM musicbrainz_names WHERE sourceId = :sourceId AND name IN (:names)")
    suspend fun names(
        sourceId: Long,
        names: List<String>,
    ): List<MusicBrainzName>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setName(name: MusicBrainzName)

    /** Album artists with at least one album, a page at a time after [afterId] (keyset), never the whole table. */
    @Query(
        "SELECT ar.id AS id, ar.name AS name, ar.musicBrainzId AS musicBrainzId FROM artists ar " +
            "WHERE ar.sourceId = :sourceId AND ar.id > :afterId AND EXISTS (SELECT 1 FROM albums al " +
            "WHERE al.sourceId = ar.sourceId AND al.artistId = ar.id) ORDER BY ar.id LIMIT :limit",
    )
    suspend fun watchArtists(
        sourceId: Long,
        afterId: String,
        limit: Int,
    ): List<WatchArtist>

    @Query("SELECT artistId, name FROM albums WHERE sourceId = :sourceId AND artistId IN (:artistIds)")
    suspend fun ownedAlbums(
        sourceId: Long,
        artistIds: List<String>,
    ): List<OwnedAlbum>
}
