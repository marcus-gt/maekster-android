package com.subtracks.data.source.subsonic

import com.subtracks.data.source.PlaylistDetail
import com.subtracks.data.source.PlaylistWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Subsonic playlist edits. Song lists go out in batches so a long list never makes a URL a proxy would refuse;
 * a failed batch stops the edit, and the caller's retry works out what is still missing.
 */
class SubsonicPlaylistWriter(
    private val sourceId: Long,
    private val client: SubsonicClient,
    private val batchSize: Int = BATCH_SIZE,
) : PlaylistWriter {
    init {
        require(batchSize > 0) { "batchSize must be positive" }
    }

    override suspend fun create(name: String): PlaylistDetail? =
        io { client.stream("createPlaylist", mapOf("name" to name)) { SubsonicXml.playlistDetail(sourceId, it) } }

    override suspend fun rename(
        playlistId: String,
        name: String,
    ) = io { client.check("updatePlaylist", mapOf("playlistId" to playlistId, "name" to name)) }

    override suspend fun setComment(
        playlistId: String,
        comment: String,
    ) = io { client.check("updatePlaylist", mapOf("playlistId" to playlistId, "comment" to comment)) }

    override suspend fun append(
        playlistId: String,
        songIds: List<String>,
    ) = io { appendBatches(playlistId, songIds) }

    override suspend fun remove(
        playlistId: String,
        positions: List<Int>,
    ) = io {
        // Highest first: removing later positions never moves earlier ones, so each batch's indices stay valid.
        positions.distinct().sortedDescending().chunked(batchSize).forEach { batch ->
            ensureActive()
            client.check(
                "updatePlaylist",
                mapOf("playlistId" to playlistId),
                repeated = batch.map { "songIndexToRemove" to it.toString() },
            )
        }
    }

    override suspend fun replace(
        playlistId: String,
        songIds: List<String>,
    ) = io {
        // createPlaylist with an id replaces the whole list; the remainder is appended after it.
        client.check(
            "createPlaylist",
            mapOf("playlistId" to playlistId),
            repeated = songIds.take(batchSize).map { "songId" to it },
        )
        appendBatches(playlistId, songIds.drop(batchSize))
    }

    override suspend fun delete(playlistId: String) =
        io {
            try {
                client.check("deletePlaylist", mapOf("id" to playlistId))
            } catch (failure: SubsonicException) {
                if (!failure.isNotFound()) throw failure
            }
        }

    override suspend fun playlist(playlistId: String): PlaylistDetail? = io { playlistNow(playlistId) }

    private fun playlistNow(playlistId: String): PlaylistDetail? =
        try {
            client.stream("getPlaylist", mapOf("id" to playlistId)) { SubsonicXml.playlistDetail(sourceId, it) }
        } catch (failure: SubsonicException) {
            if (!failure.isNotFound()) throw failure
            null
        }

    private suspend fun appendBatches(
        playlistId: String,
        songIds: List<String>,
    ) {
        songIds.chunked(batchSize).forEach { batch ->
            ensureActive()
            client.check(
                "updatePlaylist",
                mapOf("playlistId" to playlistId),
                repeated = batch.map { "songIdToAdd" to it },
            )
        }
    }

    private suspend fun ensureActive() = currentCoroutineContext().ensureActive()

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun SubsonicException.isNotFound() = code == 70 || (this is HttpStatusException && status == 404)

    companion object {
        const val BATCH_SIZE = 100
    }
}
