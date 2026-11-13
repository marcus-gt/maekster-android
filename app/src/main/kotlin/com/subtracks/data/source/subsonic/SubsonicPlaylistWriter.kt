package com.subtracks.data.source.subsonic

import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.source.PlaylistBatch
import com.subtracks.data.source.PlaylistDetail
import com.subtracks.data.source.PlaylistWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
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

    override fun playlistBatches(playlistId: String): Flow<PlaylistBatch> =
        channelFlow {
            val context = currentCoroutineContext()
            var selected: Playlist? = null
            var position = 0L
            val pending = ArrayList<PlaylistSong>(READ_BATCH_SIZE)

            fun flush() {
                context.ensureActive()
                val playlist = checkNotNull(selected)
                if (trySendBlocking(PlaylistBatch(playlist, pending.toList())).isFailure) {
                    throw CancellationException("Playlist read cancelled")
                }
                pending.clear()
            }

            try {
                client.stream("getPlaylist", mapOf("id" to playlistId)) { input ->
                    SubsonicXml.readEntities(
                        input,
                        entityTag = "playlist",
                        create = { attrs ->
                            context.ensureActive()
                            check(selected == null) { "Server reported multiple playlists" }
                            SubsonicXml.playlist(sourceId, attrs).also {
                                check(it.id == playlistId) { "Server reported a different playlist" }
                                selected = it
                            }
                        },
                        onChild = { playlist, name, attrs, level ->
                            context.ensureActive()
                            if (name == "entry" && level == 1) {
                                val id = attrs.attr("id")
                                check(id.isNotEmpty()) { "Server reported a playlist entry without an ID" }
                                pending += PlaylistSong(sourceId, playlist.id, id, position++)
                                if (pending.size == READ_BATCH_SIZE) flush()
                            }
                        },
                        onEnd = { if (pending.isNotEmpty() || position == 0L) flush() },
                    )
                }
            } catch (failure: SubsonicException) {
                if (!failure.isNotFound() || selected != null) throw failure
            }
        }.flowOn(Dispatchers.IO).buffer(0)

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
        private const val READ_BATCH_SIZE = 100
    }
}
