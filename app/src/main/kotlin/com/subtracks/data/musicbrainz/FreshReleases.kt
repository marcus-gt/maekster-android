package com.subtracks.data.musicbrainz

import com.subtracks.data.db.FreshReleaseDao
import com.subtracks.data.db.WatchArtist
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.model.FreshReleaseState
import com.subtracks.data.model.MusicBrainzName
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex

/**
 * "New releases by artists in your library that you don't own", from MusicBrainz, as on iOS. Library artists are
 * read a page at a time and searched in batches, so neither the artists nor the albums table is ever loaded whole.
 * The result is stored per server and read from Room, so it shows offline. Callers decide when to refresh (an opt-in
 * screen, online only); [refreshIfStale] keeps that to about once a day.
 */
class FreshReleases(
    private val dao: FreshReleaseDao,
    private val musicBrainz: MusicBrainz,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val running = Mutex()

    fun releases(sourceId: Long): Flow<List<FreshRelease>> = dao.releases(sourceId)

    /** Whether a refresh is due: never refreshed, or 20 hours old, and no attempt in the last 15 minutes. */
    suspend fun isStale(sourceId: Long): Boolean {
        val state = dao.state(sourceId) ?: return true
        val time = now()
        if (time - state.attemptedAt < RETRY_FAILED_AFTER_MS) return false
        val refreshedAt = state.refreshedAt ?: return true
        return time - refreshedAt >= REFRESH_INTERVAL_MS
    }

    /** Refreshes when stale; false when nothing ran (fresh, or another refresh is running). */
    suspend fun refreshIfStale(
        sourceId: Long,
        isCurrent: suspend () -> Boolean,
    ): Boolean = isStale(sourceId) && refresh(sourceId, isCurrent)

    /**
     * One full refresh in two passes, so the first result comes quickly: artists with a known MusicBrainz id are
     * searched and published first; artists still to be looked up by name follow (at most [NAME_LOOKUPS_PER_REFRESH]).
     * A failed search keeps the previous result. Nothing is stored once [isCurrent] says the server changed.
     */
    suspend fun refresh(
        sourceId: Long,
        isCurrent: suspend () -> Boolean,
    ): Boolean {
        if (!running.tryLock()) return false
        try {
            val started = now()
            val previous = dao.state(sourceId)
            dao.setState(FreshReleaseState(sourceId, previous?.refreshedAt, attemptedAt = started))
            val today = NewReleaseMatching.musicBrainzDay(started)
            val from = NewReleaseMatching.musicBrainzDay(started - WINDOW_DAYS * DAY_MS)
            val search = Search(today)
            val toLookUp = mutableListOf<Pair<WatchArtist, Set<String>>>()

            var after = ""
            while (true) {
                val page = dao.watchArtists(sourceId, after, MusicBrainz.ARTISTS_PER_SEARCH)
                if (page.isEmpty()) break
                after = page.last().id
                val owned =
                    dao
                        .ownedAlbums(
                            sourceId,
                            page.map { it.id },
                        ).groupBy({ it.artistId }, { NewReleaseMatching.normalisedTitle(it.name) })
                val known = dao.names(sourceId, page.map { NewReleaseMatching.normalisedName(it.name) }).associateBy { it.name }
                val followed = mutableMapOf<String, MutableSet<String>>()
                for (artist in page) {
                    val name = NewReleaseMatching.normalisedName(artist.name)
                    val titles = owned[artist.id].orEmpty().toSet()
                    if (titles.isEmpty() || name in SPECIAL_PURPOSE_NAMES) continue
                    val tagged = artist.musicBrainzId?.takeIf(NewReleaseMatching::isMusicBrainzId)?.lowercase()
                    val cached = known[name]
                    val mbid =
                        when {
                            tagged != null -> {
                                tagged
                            }

                            cached != null && (cached.musicBrainzId != null || started - cached.triedAt < RETRY_UNRESOLVED_AFTER_MS) -> {
                                cached.musicBrainzId
                            }

                            else -> {
                                if (toLookUp.size < NAME_LOOKUPS_PER_REFRESH) toLookUp += artist to titles
                                null
                            }
                        }
                    if (mbid != null && mbid !in SPECIAL_PURPOSE_IDS) followed.getOrPut(mbid) { mutableSetOf() } += titles
                }
                search.add(followed, musicBrainz.releaseGroups(followed.keys.toList(), from, today))
            }
            if (!publish(sourceId, search, started, isCurrent)) return true

            val resolved = mutableMapOf<String, MutableSet<String>>()
            for ((artist, titles) in toLookUp) {
                val mbid =
                    try {
                        musicBrainz.artistId(artist.name)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        // Not stored: the next refresh tries this name again.
                        Log.w(TAG, "name lookup failed", e)
                        continue
                    }
                dao.setName(MusicBrainzName(sourceId, NewReleaseMatching.normalisedName(artist.name), mbid, started))
                if (mbid != null && mbid !in SPECIAL_PURPOSE_IDS) resolved.getOrPut(mbid) { mutableSetOf() } += titles
            }
            if (resolved.isEmpty()) return true
            for (batch in resolved.keys.chunked(MusicBrainz.ARTISTS_PER_SEARCH)) {
                search.add(batch.associateWith { resolved.getValue(it) }, musicBrainz.releaseGroups(batch, from, today))
            }
            publish(sourceId, search, started, isCurrent)
            return true
        } finally {
            running.unlock()
        }
    }

    private suspend fun publish(
        sourceId: Long,
        search: Search,
        refreshedAt: Long,
        isCurrent: suspend () -> Boolean,
    ): Boolean {
        if (!isCurrent()) return false
        val releases =
            search.releases().mapIndexed { position, group ->
                FreshRelease(
                    sourceId = sourceId,
                    id = group.id,
                    position = position,
                    title = group.title,
                    artistName = group.creditName,
                    firstReleaseDate = group.firstReleaseDate,
                    typeLabel = NewReleaseMatching.typeLabel(group.primaryType, group.secondaryTypes),
                )
            }
        dao.publish(sourceId, releases, FreshReleaseState(sourceId, refreshedAt, refreshedAt))
        Log.i(TAG, "${releases.size} fresh releases")
        return true
    }

    /**
     * What the searches found so far. A release owned under any credited artist is dropped, even when another
     * artist's search returned it, so the feed never lists something the library has.
     */
    private class Search(
        private val today: String,
    ) {
        private val found = linkedMapOf<String, MbReleaseGroup>()
        private val owned = mutableSetOf<String>()

        fun add(
            followed: Map<String, Set<String>>,
            groups: List<MbReleaseGroup>,
        ) {
            for (group in groups) {
                if (!NewReleaseMatching.isReleased(group.firstReleaseDate, today)) continue
                val credited = group.credits.map { it.artistId.lowercase() }.filter { it in followed }
                if (credited.isEmpty()) continue
                val title = NewReleaseMatching.normalisedTitle(group.title)
                if (credited.any { title in followed.getValue(it) }) owned += group.id
                found.putIfAbsent(group.id, group)
            }
        }

        /** Newest first; one entry per title and date, as MusicBrainz sometimes lists a record under two credits. */
        fun releases(): List<MbReleaseGroup> {
            val titles = mutableSetOf<String>()
            return found.values
                .filter { it.id !in owned }
                .sortedByDescending { it.firstReleaseDate.orEmpty() }
                .filter { titles.add(NewReleaseMatching.normalisedTitle(it.title) + "|" + it.firstReleaseDate.orEmpty()) }
                .take(MAX_RELEASES)
        }
    }

    companion object {
        private const val TAG = "fresh"
        private const val DAY_MS = 86_400_000L
        const val WINDOW_DAYS = 90L
        const val REFRESH_INTERVAL_MS = 20 * 3_600_000L
        const val RETRY_FAILED_AFTER_MS = 15 * 60_000L
        const val RETRY_UNRESOLVED_AFTER_MS = 30 * DAY_MS
        const val NAME_LOOKUPS_PER_REFRESH = 120
        const val MAX_RELEASES = 500

        /** MusicBrainz' special-purpose artists; following "Various Artists" would list every new compilation. */
        val SPECIAL_PURPOSE_IDS =
            setOf(
                "89ad4ac3-39f7-470e-963a-56509c546377", // Various Artists
                "125ec42a-7229-4250-afc5-e057484327fe", // [unknown]
                "f731ccc4-e22a-43af-a747-64213329e088", // [anonymous]
                "9be7f096-97ec-4615-8957-8d40b5dcbc41", // [traditional]
                "eec63d3c-3b81-4ad4-b1e4-7c147d4d2b61", // [no artist]
                "33cf029c-63b0-41a0-9855-be2a3665fb3b", // [data]
                "314e1c25-dde7-4e4d-b2f4-0a7b9f7c56dc", // [dialogue]
            )
        val SPECIAL_PURPOSE_NAMES = setOf("various artists", "various", "va", "unknown artist", "unknown")
    }
}
