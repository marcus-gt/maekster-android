package com.subtracks.data.source.subsonic

import com.subtracks.data.recommendations.SimilarArtist
import com.subtracks.data.recommendations.SimilarArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses only the configured server; constructing the provider never starts a request. */
class SubsonicSimilarArtists(
    private val client: SubsonicClient,
) : SimilarArtists {
    override suspend fun similar(
        artistId: String,
        count: Int,
    ): List<SimilarArtist> {
        if (artistId.isBlank() || count <= 0) return emptyList()
        val limit = count.coerceAtMost(50)
        return withContext(Dispatchers.IO) {
            client.stream(
                "getArtistInfo2",
                mapOf("id" to artistId, "count" to limit.toString(), "includeNotPresent" to "true"),
                timeoutMs = 15_000L,
            ) { input ->
                val result = mutableListOf<SimilarArtist>()
                val seen = mutableSetOf<String>()
                SubsonicXml.readEntities(
                    input,
                    entityTag = "similarArtist",
                    create = { attrs ->
                        SimilarArtist(
                            serverId = attrs.getValue("id")?.takeUnless { it.isBlank() || it == "-1" },
                            name = attrs.getValue("name").orEmpty().trim(),
                            coverArt = attrs.getValue("coverArt")?.takeIf { it.isNotBlank() },
                            musicBrainzId = attrs.getValue("musicBrainzId")?.takeIf { it.isNotBlank() },
                        )
                    },
                    onEnd = { artist ->
                        val key = artist.serverId ?: artist.musicBrainzId ?: artist.name.lowercase(java.util.Locale.ROOT)
                        if (artist.name.isNotBlank() && artist.serverId != artistId && result.size < limit && seen.add(key)) {
                            result += artist
                        }
                    },
                )
                result
            }
        }
    }
}
