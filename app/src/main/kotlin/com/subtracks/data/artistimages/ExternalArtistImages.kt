package com.subtracks.data.artistimages

import com.subtracks.data.musicbrainz.MusicBrainz
import com.subtracks.data.musicbrainz.NewReleaseMatching
import com.subtracks.data.recommendations.SimilarArtist
import com.subtracks.log.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl

/**
 * Photos for artists outside the library: MusicBrainz artist → its Wikidata item → that item's Wikimedia Commons
 * image. A usable MusicBrainz id is looked up directly; otherwise the name is matched, but only when MusicBrainz is
 * certain of it ([MusicBrainz.artistId]). MusicBrainz requests share the app-wide one-per-second limiter.
 *
 * Definite answers (a photo, or none) are kept in a bounded in-memory cache; failures are not, so a lookup that
 * failed offline is tried again later. Concurrent requests for one artist share a single lookup, which is cancelled
 * once nobody waits for it. Callers decide whether lookups are allowed at all (D10): this class makes no checks.
 */
class ExternalArtistImages(
    private val musicBrainz: MusicBrainz,
    private val wikidata: Wikidata,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxEntries: Int = MAX_ENTRIES,
) {
    /** A definite answer: the photo URL, or null when the artist has none. */
    private data class Answer(
        val url: String?,
    )

    /** A shared lookup; it completes with null instead of failing, so it never cancels the scope it runs in. */
    private class Lookup(
        val work: Deferred<Answer?>,
    ) {
        var waiters = 0
    }

    private val lock = Any()
    private val known =
        object : LinkedHashMap<String, String?>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > maxEntries
        }
    private val inFlight = HashMap<String, Lookup>()

    /** The photo URL for [artist], or null when there is none or it could not be looked up now. */
    suspend fun imageUrl(artist: SimilarArtist): String? {
        val id = artist.musicBrainzId?.trim()
        return if (NewReleaseMatching.isMusicBrainzId(id)) imageUrlForId(id!!) else imageUrlForName(artist.name)
    }

    suspend fun imageUrlForId(musicBrainzId: String): String? {
        val id = musicBrainzId.trim().lowercase()
        if (!NewReleaseMatching.isMusicBrainzId(id)) return null
        return resolve("id:$id") { photo(id) }
    }

    suspend fun imageUrlForName(name: String): String? {
        val key = NewReleaseMatching.normalisedName(name)
        if (key.isEmpty()) return null
        return resolve("name:$key") { musicBrainz.artistId(name.trim())?.let { photo(it) } }
    }

    private suspend fun photo(musicBrainzId: String): String? {
        val item = musicBrainz.wikidataId(musicBrainzId) ?: return null
        val file = wikidata.imageFile(item) ?: return null
        return commonsFileUrl(file)
    }

    private suspend fun resolve(
        key: String,
        find: suspend () -> String?,
    ): String? {
        val lookup =
            synchronized(lock) {
                if (known.containsKey(key)) return known[key]
                inFlight.getOrPut(key) { start(key, find) }.also { it.waiters++ }
            }
        lookup.work.start()
        try {
            return lookup.work.await()?.url
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            return null
        } finally {
            synchronized(lock) {
                lookup.waiters--
                if (lookup.waiters == 0 && !lookup.work.isCompleted && inFlight[key] === lookup) {
                    inFlight.remove(key)
                    lookup.work.cancel()
                }
            }
        }
    }

    private fun start(
        key: String,
        find: suspend () -> String?,
    ): Lookup {
        lateinit var lookup: Lookup
        lookup =
            Lookup(
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        Answer(find()).also { answer -> synchronized(lock) { known[key] = answer.url } }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.w(TAG, "Artist image lookup failed: ${failure.javaClass.simpleName}")
                        null
                    } finally {
                        synchronized(lock) { if (inFlight[key] === lookup) inFlight.remove(key) }
                    }
                },
            )
        return lookup
    }

    companion object {
        private const val TAG = "ExternalArtistImages"

        /** Enough for every similar-artist shelf a session is likely to open, without growing without bound. */
        const val MAX_ENTRIES = 500

        /** Commons serves a [width]-pixel rendition of [file] from here, redirecting to its upload server. */
        fun commonsFileUrl(
            file: String,
            width: Int = 500,
        ): String =
            HttpUrl
                .Builder()
                .scheme("https")
                .host("commons.wikimedia.org")
                .addPathSegment("wiki")
                .addPathSegment("Special:FilePath")
                .addPathSegment(file.replace(' ', '_'))
                .addQueryParameter("width", width.toString())
                .build()
                .toString()
    }
}
