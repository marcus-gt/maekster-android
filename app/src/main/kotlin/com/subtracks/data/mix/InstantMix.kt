package com.subtracks.data.mix

import com.subtracks.data.db.MixCandidate
import com.subtracks.data.db.MixDao
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/**
 * Instant Mix (iOS `LibraryService.instantMix`): songs like a seed song, album or artist.
 *
 * Online it asks the server ([SimilarSongs], an explicit action under D9) and caches the answer in Room. When the
 * server can't answer, or offline, the cached answer is used (limited to downloads offline). With nothing known,
 * it falls back to the seed's own artist and genres from the mirror, topped up with Smart Shuffle, so a mix
 * always starts. Only songs in the local mirror are ever queued.
 */
class InstantMix(
    private val dao: MixDao,
    private val similar: SimilarSongs,
    private val smartShuffle: SmartShuffle,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    enum class From { Server, Cache, Local }

    data class Mix(
        val songIds: List<String>,
        val from: From,
    )

    suspend fun build(
        sourceId: Long,
        seed: MixSeed,
        online: Boolean,
        targetSize: Int = TARGET_SIZE,
    ): Mix {
        val downloadedOnly = !online
        var from = From.Server
        val ranked =
            (if (online) fromServer(sourceId, seed) else null) ?: run {
                from = From.Cache
                inMirror(sourceId, dao.cachedSimilar(sourceId, seed.kind, seed.id), downloadedOnly)
            }
        val seedSong =
            (seed as? MixSeed.Song)?.id?.takeIf {
                inMirror(
                    sourceId,
                    listOf(MixCandidate(it, null)),
                    downloadedOnly,
                ).isNotEmpty()
            }
        var picks = order(seed, ranked.filter { it.id != seedSong }, MAX_PER_ARTIST).map { it.id }
        if (picks.isEmpty()) {
            from = From.Local
            picks = localFallback(sourceId, seed, seedSong, downloadedOnly, targetSize)
        }
        val songs = (listOfNotNull(seedSong) + picks).distinct().take(targetSize)
        Log.i("mix", "instant mix ${seed.kind}: ${songs.size} songs from ${from.name.lowercase()}")
        return Mix(songs, from)
    }

    /** The server's answer, kept to songs the mirror holds and cached; null when the server could not answer. */
    private suspend fun fromServer(
        sourceId: Long,
        seed: MixSeed,
    ): List<MixCandidate>? =
        try {
            val known = inMirror(sourceId, similar.similar(sourceId, seed, REQUEST_COUNT), downloadedOnly = false)
            dao.replaceSimilar(sourceId, seed.kind, seed.id, known, now())
            known
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w("mix", "similar songs unavailable, using the cache: ${failure.message}")
            null
        }

    /** [songs] the mirror holds (downloaded ones with [downloadedOnly]), in the given order, without repeats. */
    private suspend fun inMirror(
        sourceId: Long,
        songs: List<MixCandidate>,
        downloadedOnly: Boolean,
    ): List<MixCandidate> {
        if (songs.isEmpty()) return emptyList()
        val known =
            songs
                .map { it.id }
                .distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { dao.existing(sourceId, it, downloadedOnly) }
        val byId = known.associateBy { it.id }
        return songs.mapNotNull { byId[it.id] }.distinctBy { it.id }
    }

    private suspend fun localFallback(
        sourceId: Long,
        seed: MixSeed,
        seedSong: String?,
        downloadedOnly: Boolean,
        targetSize: Int,
    ): List<String> {
        val artist: String?
        val genres: List<String>
        when (seed) {
            is MixSeed.Song -> {
                artist = dao.songArtist(sourceId, seed.id)
                genres = dao.songGenres(sourceId, seed.id, SEED_GENRES)
            }

            is MixSeed.Album -> {
                artist = dao.albumArtist(sourceId, seed.id)
                genres = dao.albumGenres(sourceId, seed.id, SEED_GENRES)
            }

            is MixSeed.Artist -> {
                artist = seed.id
                genres = dao.artistGenres(sourceId, seed.id, SEED_GENRES)
            }
        }
        val byArtist = artist?.let { dao.songsOfArtists(sourceId, listOf(it), downloadedOnly, SmartShuffle.POOL) }.orEmpty()
        val byGenre = if (genres.isEmpty()) emptyList() else dao.songsOfGenres(sourceId, genres, downloadedOnly, SmartShuffle.POOL)
        val local =
            SmartShuffle
                .blend(listOf(byArtist, byGenre), setOfNotNull(seedSong), targetSize, MAX_PER_ARTIST, random)
                .map { it.id }
        if (local.size >= targetSize) return local
        // Too little alike: top up with Smart Shuffle, as iOS does when the server knows nothing similar.
        val taken = local.toSet() + listOfNotNull(seedSong)
        val topUp = smartShuffle.build(sourceId, downloadedOnly, targetSize).filter { it !in taken }
        return local + topUp
    }

    companion object {
        const val TARGET_SIZE = 50
        const val REQUEST_COUNT = 50
        const val MAX_PER_ARTIST = 4
        const val SEED_GENRES = 2
        private const val MAX_IDS_PER_QUERY = 500

        /**
         * iOS `orderInstantMix`: a song seed keeps the server's relevance order; an album or artist seed
         * interleaves artists by first appearance. Either way no artist gets more than [maxPerArtist].
         */
        fun order(
            seed: MixSeed,
            songs: List<MixCandidate>,
            maxPerArtist: Int,
        ): List<MixCandidate> {
            val unique = songs.distinctBy { it.id }
            return if (seed is MixSeed.Song) capPreservingOrder(unique, maxPerArtist) else diversify(unique, maxPerArtist)
        }

        private fun MixCandidate.artistKey() = artistId ?: id

        private fun capPreservingOrder(
            songs: List<MixCandidate>,
            maxPerArtist: Int,
        ): List<MixCandidate> {
            val taken = HashMap<String, Int>()
            return songs.filter { song ->
                val n = taken[song.artistKey()] ?: 0
                if (n >= maxPerArtist) return@filter false
                taken[song.artistKey()] = n + 1
                true
            }
        }

        private fun diversify(
            songs: List<MixCandidate>,
            maxPerArtist: Int,
        ): List<MixCandidate> {
            val buckets = LinkedHashMap<String, ArrayDeque<MixCandidate>>()
            songs.forEach { buckets.getOrPut(it.artistKey()) { ArrayDeque() }.addLast(it) }
            val result = ArrayList<MixCandidate>(songs.size)
            repeat(maxPerArtist) {
                buckets.values.forEach { bucket -> bucket.removeFirstOrNull()?.let(result::add) }
            }
            return result
        }
    }
}
