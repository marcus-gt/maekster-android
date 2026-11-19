package com.subtracks.data.source.subsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `getTopSongs` for an artist name. Only song ids are read: the artist page shows the songs from the local
 * mirror, never the server's copy of their metadata.
 */
class SubsonicTopSongs(
    private val client: SubsonicClient,
) {
    suspend fun top(
        artistName: String,
        count: Int,
    ): List<String> =
        withContext(Dispatchers.IO) {
            client.stream("getTopSongs", mapOf("artist" to artistName, "count" to count.toString()), timeoutMs = TIMEOUT_MS) { input ->
                val ids = mutableListOf<String>()
                SubsonicXml.readEntities(
                    input,
                    entityTag = "song",
                    create = { attrs -> attrs.getValue("id").orEmpty() },
                    onEnd = { if (it.isNotEmpty()) ids += it },
                )
                ids
            }
        }

    companion object {
        private const val TIMEOUT_MS = 15_000L
    }
}
