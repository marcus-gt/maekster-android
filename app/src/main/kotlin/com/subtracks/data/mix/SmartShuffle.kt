package com.subtracks.data.mix

import com.subtracks.data.db.MixCandidate
import com.subtracks.data.db.MixDao
import kotlin.random.Random

/**
 * Smart Shuffle (iOS `LibraryService.smartShuffleQueue`): a personal mix that deals about equally from three
 * sources: favourites, recent taste and rediscovery. Everything comes from the local mirror (D9), so it works
 * the same online and, limited to downloaded songs, offline. Every read is bounded.
 */
class SmartShuffle(
    private val dao: MixDao,
    private val random: Random = Random.Default,
) {
    /** Song ids for a mix of up to [targetSize]; empty when the library (or, offline, the downloads) has nothing. */
    suspend fun build(
        sourceId: Long,
        downloadedOnly: Boolean,
        targetSize: Int = TARGET_SIZE,
    ): List<String> {
        val recent = dao.recentPlays(sourceId, RECENT_PLAYS)
        val seedArtists = recent.mapNotNull { it.artistId?.takeIf(String::isNotBlank) }.distinct().take(SEED_ARTISTS)
        val seedGenres = recent.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().take(SEED_GENRES)

        // Favourites: starred songs, plus the songs of a couple of starred albums and artists.
        val favorites =
            dao.starredSongs(sourceId, downloadedOnly, POOL) +
                dao.starredAlbumIds(sourceId, STARRED_ALBUMS).ifNotEmpty { dao.songsOfAlbums(sourceId, it, downloadedOnly, POOL) } +
                dao.starredArtistIds(sourceId, STARRED_ARTISTS).ifNotEmpty { dao.songsOfArtists(sourceId, it, downloadedOnly, POOL) }
        // Recent taste: the artists and genres played lately.
        val recentTaste =
            seedArtists.ifNotEmpty { dao.songsOfArtists(sourceId, it, downloadedOnly, POOL) } +
                seedGenres.ifNotEmpty { dao.songsOfGenres(sourceId, it, downloadedOnly, POOL) }
        // Rediscovery: anything, so there is still some surprise.
        val rediscovery = dao.randomSongs(sourceId, downloadedOnly, targetSize)

        return blend(
            sources = listOf(favorites, recentTaste, rediscovery),
            excludedIds = recent.mapTo(HashSet()) { it.songId },
            targetSize = targetSize,
            maxPerArtist = MAX_PER_ARTIST,
            random = random,
        ).map { it.id }
    }

    companion object {
        const val TARGET_SIZE = 50
        const val MAX_PER_ARTIST = 3
        const val RECENT_PLAYS = 50
        const val SEED_ARTISTS = 3
        const val SEED_GENRES = 2
        const val STARRED_ALBUMS = 2
        const val STARRED_ARTISTS = 2

        /** Rows read per source query: enough variety for a 50-song mix without loading a whole library. */
        const val POOL = 200

        /**
         * Deals from each source in turn, each shuffled, so the mix alternates between them. Songs in
         * [excludedIds] (recent plays) are skipped, no artist gets more than [maxPerArtist], and a source that
         * runs dry leaves its turns to the others. If skipping recent plays leaves nothing at all (a tiny
         * library), they are allowed back rather than playing nothing.
         */
        fun blend(
            sources: List<List<MixCandidate>>,
            excludedIds: Set<String>,
            targetSize: Int,
            maxPerArtist: Int,
            random: Random,
        ): List<MixCandidate> {
            val shuffled = sources.map { it.shuffled(random) }

            fun deal(skipping: Set<String>): List<MixCandidate> {
                val seen = HashSet(skipping)
                val perArtist = HashMap<String, Int>()
                val cursors = IntArray(shuffled.size)
                val result = ArrayList<MixCandidate>(targetSize)
                while (result.size < targetSize) {
                    var progressed = false
                    for (index in shuffled.indices) {
                        if (result.size >= targetSize) break
                        val source = shuffled[index]
                        while (cursors[index] < source.size) {
                            val song = source[cursors[index]++]
                            val artist = song.artistId ?: song.id
                            if (song.id in seen || (perArtist[artist] ?: 0) >= maxPerArtist) continue
                            seen += song.id
                            perArtist[artist] = (perArtist[artist] ?: 0) + 1
                            result += song
                            progressed = true
                            break
                        }
                    }
                    if (!progressed) break
                }
                return result
            }
            val mix = deal(excludedIds)
            return if (mix.isEmpty() && excludedIds.isNotEmpty()) deal(emptySet()) else mix
        }
    }
}

private inline fun <T> List<String>.ifNotEmpty(read: (List<String>) -> List<T>): List<T> = if (isEmpty()) emptyList() else read(this)
