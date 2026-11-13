package com.subtracks.data.source.subsonic

import com.subtracks.data.lyrics.Lyrics
import com.subtracks.data.lyrics.LyricsProvider
import com.subtracks.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SubsonicLyricsProvider(
    private val sourceId: Long,
    private val client: SubsonicClient,
) : LyricsProvider {
    override suspend fun fetch(song: Song): List<Lyrics> =
        withContext(Dispatchers.IO) {
            require(song.sourceId == sourceId)
            currentCoroutineContext().ensureActive()
            val structured =
                if (song.id.isBlank()) {
                    emptyList()
                } else {
                    try {
                        client.stream("getLyricsBySongId", mapOf("id" to song.id), timeoutMs = TIMEOUT_MS, body = SubsonicXml::lyrics)
                    } catch (failure: SubsonicException) {
                        if (!failure.allowsClassicFallback()) throw failure
                        emptyList()
                    }
                }
            currentCoroutineContext().ensureActive()
            if (structured.isNotEmpty()) return@withContext structured
            val artist = song.artist?.trim().orEmpty()
            val title = song.title.trim()
            if (artist.isEmpty() || title.isEmpty()) return@withContext emptyList()
            val classic =
                try {
                    client.stream(
                        "getLyrics",
                        mapOf("artist" to artist, "title" to title),
                        timeoutMs = TIMEOUT_MS,
                        body = SubsonicXml::lyrics,
                    )
                } catch (failure: SubsonicException) {
                    if (failure.code != 70) throw failure
                    emptyList()
                }
            currentCoroutineContext().ensureActive()
            classic
        }

    private fun SubsonicException.allowsClassicFallback(): Boolean =
        if (this is HttpStatusException) status in setOf(404, 405, 501) else code in setOf(0, 20, 30, 70)

    companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
