package com.subtracks.data.source.subsonic

import com.subtracks.data.db.MixCandidate
import com.subtracks.data.mix.MixSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `getSimilarSongs` (song or album seed) and `getSimilarSongs2` (artist seed). Only ids and artists are read:
 * the mix plays songs from the local mirror, never the server's copy of their metadata.
 */
class SubsonicSimilarSongs(
    private val client: SubsonicClient,
) {
    suspend fun similar(
        seed: MixSeed,
        count: Int,
    ): List<MixCandidate> =
        withContext(Dispatchers.IO) {
            val method = if (seed is MixSeed.Artist) "getSimilarSongs2" else "getSimilarSongs"
            client.stream(method, mapOf("id" to seed.id, "count" to count.toString()), timeoutMs = TIMEOUT_MS) { input ->
                val songs = mutableListOf<MixCandidate>()
                SubsonicXml.readEntities(
                    input,
                    entityTag = "song",
                    create = { attrs -> MixCandidate(attrs.getValue("id").orEmpty(), attrs.getValue("artistId")?.ifEmpty { null }) },
                    onEnd = { if (it.id.isNotEmpty()) songs += it },
                )
                songs
            }
        }

    companion object {
        private const val TIMEOUT_MS = 15_000L
    }
}
