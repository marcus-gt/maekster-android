package com.subtracks.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.subtracks.data.model.Source
import com.subtracks.data.model.SourceHeader
import com.subtracks.data.model.SubsonicSource
import kotlinx.coroutines.flow.Flow

/** A server's connection details as stored; SourceRepository opens the password before anything uses it. */
data class StoredSubsonicConfig(
    val id: Long,
    val name: String,
    val address: String,
    val username: String,
    val password: String,
    val passwordCiphertext: String?,
    val useTokenAuth: Boolean,
)

data class LegacyPassword(
    val sourceId: Long,
    val password: String,
)

data class SealedPassword(
    val sourceId: Long,
    val passwordCiphertext: String,
)

private const val CONFIG_COLUMNS =
    "s.id AS id, s.name AS name, s.address AS address, c.username AS username, c.password AS password, " +
        "c.passwordCiphertext AS passwordCiphertext, c.useTokenAuth AS useTokenAuth " +
        "FROM sources s JOIN subsonic_sources c ON c.sourceId = s.id"

@Dao
interface SourcesDao {
    @Query("SELECT * FROM sources ORDER BY createdAt")
    fun sources(): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE id = :id")
    fun source(id: Long): Flow<Source?>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun sourceOnce(id: Long): Source?

    @Query("SELECT id FROM sources WHERE isActive = 1 LIMIT 1")
    fun activeSourceId(): Flow<Long?>

    @Query("SELECT * FROM subsonic_sources WHERE sourceId = :sourceId")
    fun subsonicSource(sourceId: Long): Flow<SubsonicSource?>

    @Query("SELECT id FROM sources WHERE isActive = 1 LIMIT 1")
    suspend fun activeSourceIdOnce(): Long?

    @Query("SELECT creditsStale FROM sources WHERE id = :sourceId")
    suspend fun creditsStale(sourceId: Long): Boolean?

    @Query("UPDATE sources SET creditsStale = :stale WHERE id = :sourceId AND creditsStale != :stale")
    suspend fun setCreditsStale(
        sourceId: Long,
        stale: Boolean,
    )

    @Query("UPDATE sources SET name = :name, address = :address WHERE id = :sourceId")
    suspend fun updateSourceDetails(
        sourceId: Long,
        name: String,
        address: String,
    )

    @Insert
    suspend fun insertSource(source: Source): Long

    @Upsert
    suspend fun upsertSource(source: Source)

    @Upsert
    suspend fun upsertSubsonicSource(subsonic: SubsonicSource)

    @Query("UPDATE subsonic_sources SET useTokenAuth = 0 WHERE sourceId = :sourceId")
    suspend fun disableTokenAuth(sourceId: Long)

    /** Rows still holding a plaintext password, from before encryption. */
    @Query("SELECT sourceId, password FROM subsonic_sources WHERE passwordCiphertext IS NULL AND password != ''")
    suspend fun legacyPasswords(): List<LegacyPassword>

    /**
     * Stores [ciphertext] and clears the plaintext in one statement, and only while the row still holds
     * [plaintext], so an edit made meanwhile is never overwritten. Returns the number of rows changed.
     */
    @Query(
        "UPDATE subsonic_sources SET passwordCiphertext = :ciphertext, password = '' " +
            "WHERE sourceId = :sourceId AND passwordCiphertext IS NULL AND password = :plaintext",
    )
    suspend fun sealLegacyPassword(
        sourceId: Long,
        plaintext: String,
        ciphertext: String,
    ): Int

    @Query("SELECT sourceId, passwordCiphertext FROM subsonic_sources WHERE passwordCiphertext IS NOT NULL")
    fun sealedPasswords(): Flow<List<SealedPassword>>

    @Query("SELECT * FROM source_headers WHERE sourceId = :sourceId ORDER BY position")
    suspend fun headersOnce(sourceId: Long): List<SourceHeader>

    @Query(
        "SELECT h.* FROM source_headers h JOIN sources s ON s.id = h.sourceId WHERE s.isActive = 1 ORDER BY h.position",
    )
    fun activeHeaders(): Flow<List<SourceHeader>>

    @Query("SELECT * FROM source_headers ORDER BY sourceId, position")
    fun allHeaders(): Flow<List<SourceHeader>>

    @Query("DELETE FROM source_headers WHERE sourceId = :sourceId")
    suspend fun deleteHeaders(sourceId: Long)

    @Insert
    suspend fun insertHeaders(headers: List<SourceHeader>)

    @Transaction
    suspend fun replaceHeaders(
        sourceId: Long,
        headers: List<SourceHeader>,
    ) {
        deleteHeaders(sourceId)
        if (headers.isNotEmpty()) insertHeaders(headers)
    }

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSourceRow(id: Long)

    suspend fun deleteSource(id: Long) = deleteSourceRow(id)

    @Query("UPDATE sources SET isActive = (id = :sourceId)")
    suspend fun setActiveSource(sourceId: Long)

    @Query("SELECT $CONFIG_COLUMNS WHERE s.isActive = 1 LIMIT 1")
    fun activeSubsonicConfig(): Flow<StoredSubsonicConfig?>

    @Query("SELECT $CONFIG_COLUMNS WHERE s.isActive = 1 LIMIT 1")
    suspend fun activeSubsonicConfigOnce(): StoredSubsonicConfig?

    @Query("SELECT $CONFIG_COLUMNS WHERE s.id = :sourceId LIMIT 1")
    suspend fun subsonicConfigOnce(sourceId: Long): StoredSubsonicConfig?
}
