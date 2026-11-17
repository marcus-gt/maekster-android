package com.subtracks.data.recommendations

import com.subtracks.data.db.SimilarArtistsCacheDao
import com.subtracks.data.model.CachedSimilarArtists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Explicit refreshes only; browsing reads the cache without contacting a provider. */
class SimilarArtistsCache(
    private val dao: SimilarArtistsCacheDao,
    private val provider: suspend (Long) -> SimilarArtists?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refreshLock = Mutex()

    suspend fun read(
        sourceId: Long,
        artistId: String,
    ): List<SimilarArtist> = dao.get(sourceId, artistId)?.let { decode(it.payload) }.orEmpty()

    /** [canRequest] must reflect both active source and connectivity, and is rechecked before publication. */
    suspend fun refreshIfStale(
        sourceId: Long,
        artistId: String,
        canRequest: suspend () -> Boolean,
    ): List<SimilarArtist> =
        refreshLock.withLock {
            val cached = dao.get(sourceId, artistId)
            val previous = cached?.let { decode(it.payload) }
            if (artistId.isBlank() || !canRequest()) return@withLock previous.orEmpty()
            val time = now()
            if (cached != null && previous != null && time >= cached.fetchedAt && time - cached.fetchedAt < TTL_MS) return@withLock previous
            try {
                val remote = provider(sourceId) ?: return@withLock previous.orEmpty()
                if (!canRequest()) return@withLock previous.orEmpty()
                val result = remote.similar(artistId, LIMIT).take(LIMIT)
                if (!canRequest()) return@withLock previous.orEmpty()
                dao.put(CachedSimilarArtists(sourceId, artistId, encode(result), now()))
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                previous.orEmpty()
            }
        }

    private fun encode(artists: List<SimilarArtist>): String =
        JSONObject()
            .put("version", 1)
            .put(
                "artists",
                JSONArray().apply {
                    artists.forEach { artist ->
                        put(
                            JSONObject()
                                .put("id", artist.serverId)
                                .put("name", artist.name)
                                .put("cover", artist.coverArt)
                                .put("mbid", artist.musicBrainzId),
                        )
                    }
                },
            ).toString()

    private fun decode(payload: String): List<SimilarArtist>? =
        try {
            val root = JSONObject(payload)
            if (root.getInt("version") != 1) {
                null
            } else {
                val rows = root.getJSONArray("artists")
                require(rows.length() <= LIMIT)
                List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SimilarArtist(
                        row.optString("id").takeIf { it.isNotBlank() },
                        row.getString("name").also { require(it.isNotBlank()) },
                        row.optString("cover").takeIf { it.isNotBlank() },
                        row.optString("mbid").takeIf { it.isNotBlank() },
                    )
                }
            }
        } catch (_: Exception) {
            null
        }

    companion object {
        const val TTL_MS = 24 * 60 * 60 * 1000L
        private const val LIMIT = 50
    }
}
