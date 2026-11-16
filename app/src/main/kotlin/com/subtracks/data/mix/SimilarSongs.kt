package com.subtracks.data.mix

import com.subtracks.data.db.MixCandidate

/** What an Instant Mix starts from. Songs and albums use `getSimilarSongs`, artists `getSimilarSongs2`. */
sealed interface MixSeed {
    val kind: String
    val id: String

    data class Song(
        override val id: String,
    ) : MixSeed {
        override val kind = "song"
    }

    data class Album(
        override val id: String,
    ) : MixSeed {
        override val kind = "album"
    }

    data class Artist(
        override val id: String,
    ) : MixSeed {
        override val kind = "artist"
    }
}

/**
 * The server's song similarity for a seed, most similar first. Throws when the server cannot answer
 * (offline, unreachable, unsupported); an empty list means it answered but knows nothing similar.
 */
fun interface SimilarSongs {
    suspend fun similar(
        sourceId: Long,
        seed: MixSeed,
        count: Int,
    ): List<MixCandidate>
}
