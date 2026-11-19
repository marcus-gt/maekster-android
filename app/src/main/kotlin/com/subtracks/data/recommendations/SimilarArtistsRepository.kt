package com.subtracks.data.recommendations

import com.subtracks.data.db.LibraryDao
import com.subtracks.data.model.Artist

data class ResolvedSimilarArtist(
    val recommendation: SimilarArtist,
    val libraryArtist: Artist?,
)

/** Resolves a bounded cached answer against the mirror; it never downloads library metadata. */
class SimilarArtistsRepository(
    private val cache: SimilarArtistsCache,
    private val library: LibraryDao,
    private val activeSourceId: suspend () -> Long?,
    private val offline: () -> Boolean,
) {
    suspend fun read(
        sourceId: Long,
        artistId: String,
    ): List<ResolvedSimilarArtist> = resolve(sourceId, artistId, cache.read(sourceId, artistId))

    /** Reuses fresh cached results and refreshes stale results when the source is online. */
    suspend fun refresh(
        sourceId: Long,
        artistId: String,
    ): List<ResolvedSimilarArtist> {
        val candidates = cache.refreshIfStale(sourceId, artistId) { !offline() && activeSourceId() == sourceId }
        if (activeSourceId() != sourceId) return emptyList()
        return resolve(sourceId, artistId, candidates)
    }

    private suspend fun resolve(
        sourceId: Long,
        seedId: String,
        candidates: List<SimilarArtist>,
    ): List<ResolvedSimilarArtist> {
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { candidate ->
            val byId = candidate.serverId?.let { library.artistOnce(sourceId, it) }
            val byMbid =
                if (byId == null) {
                    candidate.musicBrainzId?.let { library.artistsByMusicBrainzId(sourceId, it).singleOrNull() }
                } else {
                    null
                }
            val byName =
                if (byId == null && byMbid == null) {
                    library.artistsByExactName(sourceId, candidate.name).singleOrNull()?.takeIf {
                        candidate.musicBrainzId == null || it.musicBrainzId == null ||
                            candidate.musicBrainzId.equals(it.musicBrainzId, ignoreCase = true)
                    }
                } else {
                    null
                }
            val local = byId ?: byMbid ?: byName
            when {
                local?.id == seedId -> null
                local != null && !seen.add(local.id) -> null
                else -> ResolvedSimilarArtist(candidate, local)
            }
        }
    }
}
