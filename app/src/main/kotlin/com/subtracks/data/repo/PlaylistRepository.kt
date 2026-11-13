package com.subtracks.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.playlist.PlaylistAppendIntent
import com.subtracks.data.source.PlaylistDetail
import com.subtracks.data.source.PlaylistWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why a playlist edit did not happen; any other failure is the server's or the network's own exception. */
class PlaylistEditException(
    val reason: Reason,
) : Exception(reason.name) {
    enum class Reason {
        /** Offline mode, no network, or the server needs signing in again. */
        Offline,

        /** The server no longer has the playlist; it has been removed from the mirror too. */
        NotFound,

        /** The playlist changed on the server since it was shown; the mirror now shows the server's version. */
        Changed,

        /** The server created the playlist but did not say which it was; a library sync will bring it in. */
        NotReported,
    }
}

/**
 * Adds songs to a playlist, and can be run again after a failure: the first run records what the playlist held,
 * so a retry sends only what has not arrived. Keep the same instance for retries.
 */
class PlaylistAddition(
    val sourceId: Long,
    val playlistId: String,
    val songIds: List<String>,
    /** What to do about songs the playlist already holds; it applies to the first run, which fixes the plan. */
    var duplicates: Duplicates = Duplicates.Keep,
) {
    internal var intent: PlaylistAppendIntent? = null
}

enum class Duplicates {
    /** Stop and report [AddOutcome.Duplicates] so the user can choose. */
    Ask,

    /** Add every song, even ones already there. */
    Keep,

    /** Add only songs the playlist does not hold yet. */
    Skip,
}

sealed interface AddOutcome {
    /** [count] songs were added; 0 when the playlist already held them all and duplicates were skipped. */
    data class Added(
        val count: Int,
    ) : AddOutcome

    /** Nothing was written: some songs are already there and the addition asked first. */
    data object Duplicates : AddOutcome
}

/** A new playlist with songs. A retry finishes the playlist already created rather than making another. */
class PlaylistCreation(
    val sourceId: Long,
    val name: String,
    val songIds: List<String>,
) {
    var playlistId: String? = null
        internal set
    internal var addition: PlaylistAddition? = null
}

/**
 * Playlist edits for a server. Each edit goes to the server first (they are explicit actions, D9), then the playlist
 * is read back and mirrored into Room in one transaction, so lists, search and queues see the server's result.
 * Positions are 0-based.
 */
class PlaylistRepository(
    private val db: SubtracksDatabase,
    private val writerFor: suspend (sourceId: Long) -> PlaylistWriter?,
    private val now: () -> Long = System::currentTimeMillis,
    private val libraryChanged: () -> Unit = {},
) {
    private data class ReorderIntent(
        val shown: List<String>,
        val ordered: List<String>,
    )

    private val reorderMutex = Mutex()
    private val pendingReorders = mutableMapOf<Pair<Long, String>, ReorderIntent>()

    /** The playlists songs were last added to on [sourceId], newest first (only successful additions). */
    fun recentDestinations(sourceId: Long): Flow<List<Playlist>> = db.playlistDestinationDao().recent(sourceId)

    /**
     * The playlists [sourceId]'s user can add to, by name, matching [query] anywhere in the name; [owner] is the
     * signed-in user (null when unknown), and [exclude] keeps out playlists shown elsewhere (the recent ones).
     */
    fun destinations(
        sourceId: Long,
        owner: String?,
        query: String,
        exclude: List<String>,
    ): Flow<PagingData<Playlist>> {
        val pattern =
            "%" +
                query
                    .trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_") + "%"
        return Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
            db.libraryDao().playlistDestinations(sourceId, owner, exclude, pattern)
        }.flow
    }

    suspend fun add(addition: PlaylistAddition): AddOutcome {
        val writer = writer(addition.sourceId)
        val current = fresh(writer, addition.sourceId, addition.playlistId)
        val intent =
            addition.intent ?: run {
                val held = current.songIds().toSet()
                if (addition.duplicates == Duplicates.Ask && addition.songIds.any { it in held }) return AddOutcome.Duplicates
                val wanted = if (addition.duplicates == Duplicates.Skip) addition.songIds.filter { it !in held } else addition.songIds
                PlaylistAppendIntent(wanted, current.songIds()).also { addition.intent = it }
            }
        val missing = intent.remaining(current.songIds())
        if (missing.isNotEmpty()) writer.append(addition.playlistId, missing)
        refresh(writer, addition.sourceId, addition.playlistId)
        db.playlistDestinationDao().record(addition.sourceId, addition.playlistId, now())
        return AddOutcome.Added(intent.songIds.size)
    }

    suspend fun create(creation: PlaylistCreation): String {
        val writer = writer(creation.sourceId)
        val id =
            creation.playlistId ?: run {
                val created = writer.create(creation.name) ?: throw PlaylistEditException(PlaylistEditException.Reason.NotReported)
                mirror(created)
                created.playlist.id.also { creation.playlistId = it }
            }
        if (creation.songIds.isNotEmpty()) {
            val addition = creation.addition ?: PlaylistAddition(creation.sourceId, id, creation.songIds).also { creation.addition = it }
            add(addition)
        } else {
            db.playlistDestinationDao().record(creation.sourceId, id, now())
        }
        return id
    }

    suspend fun rename(
        sourceId: Long,
        playlistId: String,
        name: String,
    ) {
        val writer = writer(sourceId)
        writer.rename(playlistId, name)
        refresh(writer, sourceId, playlistId)
    }

    suspend fun setComment(
        sourceId: Long,
        playlistId: String,
        comment: String,
    ) {
        val writer = writer(sourceId)
        writer.setComment(playlistId, comment)
        refresh(writer, sourceId, playlistId)
    }

    /**
     * Removes the songs at [positions], which the caller read from the mirror as [shownSongIds]. If the server's
     * order differs, nothing is removed: the wrong songs would go.
     */
    suspend fun remove(
        sourceId: Long,
        playlistId: String,
        positions: List<Int>,
        shownSongIds: List<String>,
    ) {
        val writer = writer(sourceId)
        val current = fresh(writer, sourceId, playlistId)
        if (current.songIds() != shownSongIds) changed(current)
        if (positions.any { it !in shownSongIds.indices }) throw IllegalArgumentException("Position outside the playlist")
        writer.remove(playlistId, positions)
        refresh(writer, sourceId, playlistId)
    }

    /**
     * Removes the one entry at [position] that the user saw as [songId] (a playlist can hold a song twice). If the
     * mirror no longer has [songId] there, nothing is removed and the playlist is refreshed.
     */
    suspend fun removeEntry(
        sourceId: Long,
        playlistId: String,
        position: Int,
        songId: String,
    ) {
        val shown = db.libraryDao().playlistEntrySongIds(sourceId, playlistId)
        if (shown.getOrNull(position) != songId) {
            val writer = writer(sourceId)
            changed(fresh(writer, sourceId, playlistId))
        }
        remove(sourceId, playlistId, listOf(position), shown)
    }

    /** Moves the occurrence at [position] to [destination], its index in the resulting playlist. */
    suspend fun moveEntry(
        sourceId: Long,
        playlistId: String,
        position: Int,
        songId: String,
        destination: Int,
    ) {
        val shown = db.libraryDao().playlistEntrySongIds(sourceId, playlistId)
        if (shown.getOrNull(position) != songId) {
            val writer = writer(sourceId)
            changed(fresh(writer, sourceId, playlistId))
        }
        require(destination in shown.indices) { "Destination outside the playlist" }
        val ordered = shown.toMutableList()
        ordered.add(destination, ordered.removeAt(position))
        reorder(sourceId, playlistId, ordered, shown)
    }

    /**
     * Puts the playlist in [ordered] order, retaining failed intent until readback succeeds. A matching retry
     * accepts the original list or an ordered prefix left by a partial replacement; any other order conflicts.
     * Retry state lasts for this repository instance, not across process death.
     */
    suspend fun reorder(
        sourceId: Long,
        playlistId: String,
        ordered: List<String>,
        shownSongIds: List<String>,
    ) {
        reorderMutex.withLock {
            require(ordered.sorted() == shownSongIds.sorted()) { "A reorder must keep the same songs" }
            val key = sourceId to playlistId
            val intent = ReorderIntent(shownSongIds.toList(), ordered.toList())
            val retry = pendingReorders[key] == intent
            val writer = writer(sourceId)
            val current = fresh(writer, sourceId, playlistId)
            val held = current.songIds()
            val partial = retry && held.size <= ordered.size && held == ordered.take(held.size)
            if (held != shownSongIds && !partial) {
                pendingReorders.remove(key)
                changed(current)
            }
            pendingReorders[key] = intent
            if (held != ordered) writer.replace(playlistId, ordered)
            refresh(writer, sourceId, playlistId)
            pendingReorders.remove(key)
        }
    }

    /**
     * Deletes the playlist on the server, then runs [afterServerDelete] (for example removing its downloads, which are
     * found through the mirror) and only then drops the mirror rows, even if [afterServerDelete] fails.
     */
    suspend fun delete(
        sourceId: Long,
        playlistId: String,
        afterServerDelete: suspend () -> Unit = {},
    ) {
        writer(sourceId).delete(playlistId)
        try {
            afterServerDelete()
        } finally {
            removeMirror(sourceId, playlistId)
        }
    }

    private suspend fun writer(sourceId: Long): PlaylistWriter =
        writerFor(sourceId) ?: throw PlaylistEditException(PlaylistEditException.Reason.Offline)

    private suspend fun fresh(
        writer: PlaylistWriter,
        sourceId: Long,
        playlistId: String,
    ): PlaylistDetail =
        writer.playlist(playlistId) ?: run {
            removeMirror(sourceId, playlistId)
            throw PlaylistEditException(PlaylistEditException.Reason.NotFound)
        }

    private suspend fun refresh(
        writer: PlaylistWriter,
        sourceId: Long,
        playlistId: String,
    ) {
        val detail = fresh(writer, sourceId, playlistId)
        mirror(detail)
    }

    private suspend fun changed(current: PlaylistDetail): Nothing {
        mirror(current)
        throw PlaylistEditException(PlaylistEditException.Reason.Changed)
    }

    private suspend fun mirror(detail: PlaylistDetail) {
        db.libraryDao().replacePlaylist(detail.playlist, detail.songs)
        libraryChanged()
    }

    private suspend fun removeMirror(
        sourceId: Long,
        playlistId: String,
    ) {
        db.libraryDao().removePlaylist(sourceId, playlistId)
        libraryChanged()
    }

    private fun PlaylistDetail.songIds() = songs.map { it.songId }
}
