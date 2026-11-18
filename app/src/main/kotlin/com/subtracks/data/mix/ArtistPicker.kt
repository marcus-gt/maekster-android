package com.subtracks.data.mix

import com.subtracks.data.db.ARTIST_PICK_DORMANT_CHOICES
import com.subtracks.data.db.ARTIST_PICK_FAVORITES
import com.subtracks.data.db.ARTIST_PICK_FAVORITE_POOL
import com.subtracks.data.db.ARTIST_PICK_SIZE
import com.subtracks.data.model.Artist
import kotlin.random.Random

/**
 * Home's Artist picks, as on iOS: a daily blend of the artists played most, ones played a lot but not lately,
 * and ones barely tried. The pools come bounded from Room; this only chooses and shuffles.
 */
object ArtistPicker {
    private const val DORMANT = 4
    private const val DAY_SECONDS = 86_400L
    private const val DORMANT_AFTER = 90 * DAY_SECONDS
    private const val SHORTEST_DORMANCY = 14 * DAY_SECONDS

    /**
     * How long without plays counts as dormant: 90 days, or half the play history when the library has kept less
     * than twice that, but never less than 14 days. Times are epoch seconds.
     */
    fun dormancy(
        earliestPlay: Long?,
        now: Long,
    ): Long = if (earliestPlay == null) DORMANT_AFTER else ((now - earliestPlay) / 2).coerceIn(SHORTEST_DORMANCY, DORMANT_AFTER)

    /** A seed that is stable for one library on one local day. */
    fun seed(
        sourceId: Long,
        day: Long,
    ): Long = day * 1_000_003L + sourceId

    /**
     * Picks up to [ARTIST_PICK_SIZE] artists: [ARTIST_PICK_FAVORITES] of the [ARTIST_PICK_FAVORITE_POOL] most played,
     * [DORMANT] of the not-recently-played, then [fresh] ones, filling up from whatever is left in a small library.
     */
    fun pick(
        played: List<Artist>,
        dormant: List<Artist>,
        fresh: List<Artist>,
        seed: Long,
    ): List<Artist> {
        val random = Random(seed)
        val favorites = played.take(ARTIST_PICK_FAVORITE_POOL).shuffled(random)
        val taken = favorites.take(ARTIST_PICK_FAVORITES).mapTo(HashSet()) { it.id }
        val notLately = dormant.filter { it.id !in taken }.take(ARTIST_PICK_DORMANT_CHOICES).shuffled(random)
        val untried = fresh.sortedBy { it.id }.shuffled(random)

        val chosen = LinkedHashMap<String, Artist>()

        fun take(
            pool: List<Artist>,
            count: Int,
        ) {
            var added = 0
            for (artist in pool) {
                if (added >= count || chosen.size >= ARTIST_PICK_SIZE) return
                if (chosen.putIfAbsent(artist.id, artist) == null) added++
            }
        }
        take(favorites, ARTIST_PICK_FAVORITES)
        take(notLately, DORMANT)
        take(untried, ARTIST_PICK_SIZE - chosen.size)
        for (pool in listOf(favorites, notLately, played, untried)) take(pool, ARTIST_PICK_SIZE)
        return chosen.values.shuffled(random)
    }
}
