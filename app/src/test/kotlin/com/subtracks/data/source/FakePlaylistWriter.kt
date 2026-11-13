package com.subtracks.data.source

import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import java.io.IOException

/** An in-memory server's playlists, with failures that can be armed for the next write. */
class FakePlaylistWriter(
    private val sourceId: Long,
) : PlaylistWriter {
    enum class Failure { BeforeWrite, AfterWrite, AfterFirstSong }

    val playlists = linkedMapOf<String, Pair<String, MutableList<String>>>()
    val appended = mutableListOf<List<String>>()
    val removed = mutableListOf<List<Int>>()
    val replaced = mutableListOf<List<String>>()
    var creates = 0
    var reportsCreated = true
    var failNext: Failure? = null
    private var nextId = 1

    fun put(
        id: String,
        name: String,
        vararg songIds: String,
    ) {
        playlists[id] = name to songIds.toMutableList()
    }

    fun songs(id: String): List<String> = playlists.getValue(id).second

    override suspend fun create(name: String): PlaylistDetail? {
        fail(Failure.BeforeWrite)
        creates++
        val id = "new${nextId++}"
        playlists[id] = name to mutableListOf()
        fail(Failure.AfterWrite)
        return if (reportsCreated) detail(id) else null
    }

    override suspend fun rename(
        playlistId: String,
        name: String,
    ) {
        fail(Failure.BeforeWrite)
        playlists[playlistId] = name to playlists.getValue(playlistId).second
    }

    override suspend fun setComment(
        playlistId: String,
        comment: String,
    ) = Unit

    override suspend fun append(
        playlistId: String,
        songIds: List<String>,
    ) {
        fail(Failure.BeforeWrite)
        appended += songIds
        val songs = playlists.getValue(playlistId).second
        if (failNext == Failure.AfterFirstSong) {
            songs += songIds.first()
            failNext = null
            throw IOException("connection lost")
        }
        songs += songIds
        fail(Failure.AfterWrite)
    }

    override suspend fun remove(
        playlistId: String,
        positions: List<Int>,
    ) {
        fail(Failure.BeforeWrite)
        removed += positions
        val songs = playlists.getValue(playlistId).second
        positions.sortedDescending().forEach { songs.removeAt(it) }
    }

    override suspend fun replace(
        playlistId: String,
        songIds: List<String>,
    ) {
        fail(Failure.BeforeWrite)
        replaced += songIds
        val songs = playlists.getValue(playlistId).second
        songs.clear()
        songs += songIds
    }

    override suspend fun delete(playlistId: String) {
        fail(Failure.BeforeWrite)
        playlists.remove(playlistId)
    }

    override suspend fun playlist(playlistId: String): PlaylistDetail? = playlists[playlistId]?.let { detail(playlistId) }

    private fun detail(id: String): PlaylistDetail {
        val (name, songs) = playlists.getValue(id)
        return PlaylistDetail(
            Playlist(sourceId, id, name, null, null, songs.size.toLong(), created = 1),
            songs.mapIndexed { position, songId -> PlaylistSong(sourceId, id, songId, position.toLong()) },
        )
    }

    private fun fail(at: Failure) {
        if (failNext == at) {
            failNext = null
            throw IOException("fixture failure at $at")
        }
    }
}
