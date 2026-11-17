package com.subtracks.data.audiomuse

import com.subtracks.data.db.AudioMuseLocalTrack
import com.subtracks.data.db.AudioMuseTrackDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

data class AudioMuseTrackResolution(
    val songIds: List<String>,
    val unresolvedIndices: List<Int>,
) {
    val allUnresolved: Boolean get() = songIds.isEmpty() && unresolvedIndices.isNotEmpty()
}

/** Local-only resolution. Per-batch matches/misses are never retained across library updates or sources. */
class AudioMuseTrackResolver(
    private val dao: AudioMuseTrackDao,
    private val access: StateFlow<AudioMuseScoreAccess>,
) {
    suspend fun resolve(
        sourceId: Long,
        tracks: List<AudioMuseTrack>,
    ): AudioMuseTrackResolution =
        withContext(Dispatchers.Default) {
            require(tracks.size <= MAX_BATCH)
            val gate = access.value
            checkAccess(sourceId, gate)
            if (tracks.isEmpty()) return@withContext AudioMuseTrackResolution(emptyList(), emptyList())
            val directIds = tracks.filter { !it.hasInternalId && it.itemId.isNotBlank() }.map { it.itemId }.distinct()
            val direct = if (directIds.isEmpty()) emptyMap() else dao.byIds(sourceId, directIds, gate.offline).associateBy { it.id }
            val metadata = tracks.map(::descriptor)
            val directMatches =
                tracks.mapIndexed { index, track ->
                    val key = metadata[index]
                    direct[track.itemId]
                        ?.takeIf { local ->
                            !track.hasInternalId && (key == null || matches(key, local))
                        }?.id
                }
            val descriptors = tracks.indices.map { index -> if (directMatches[index] == null) metadata[index] else null }
            val wanted = descriptors.filterNotNull().distinct().groupBy { it.title }
            val byDescriptor = mutableMapOf<Descriptor, UniqueMatch>()
            if (wanted.isNotEmpty()) {
                var after = ""
                while (true) {
                    checkAccess(sourceId, gate)
                    val page = dao.page(sourceId, after, gate.offline, PAGE_SIZE)
                    for (candidate in page) {
                        currentCoroutineContext().ensureActive()
                        val title = normalise(candidate.title)
                        val variants = wanted[title] ?: continue
                        val artist = normalise(candidate.artist.orEmpty())
                        val album = normalise(candidate.album.orEmpty())
                        for (key in variants) {
                            if ((key.artist == null || key.artist == artist) && (key.album == null || key.album == album)) {
                                val match = byDescriptor.getOrPut(key) { UniqueMatch() }
                                match.add(candidate.id)
                            }
                        }
                    }
                    if (page.size < PAGE_SIZE) break
                    after = page.last().id
                }
            }
            val proposed =
                tracks.indices.map { index ->
                    directMatches[index] ?: descriptors[index]?.let { byDescriptor[it]?.id }
                }
            val eligible =
                proposed.filterNotNull().distinct().let { ids ->
                    if (ids.isEmpty()) emptyMap() else dao.byIds(sourceId, ids, gate.offline).associateBy { it.id }
                }
            val resolved = mutableListOf<String>()
            val unresolved = mutableListOf<Int>()
            for (index in tracks.indices) {
                val id = proposed[index]
                val local = id?.let(eligible::get)
                val key = metadata[index]
                if (local != null && (key == null || matches(key, local))) resolved += local.id else unresolved += index
            }
            checkAccess(sourceId, gate)
            AudioMuseTrackResolution(resolved, unresolved)
        }

    private suspend fun checkAccess(
        sourceId: Long,
        expected: AudioMuseScoreAccess,
    ) {
        currentCoroutineContext().ensureActive()
        if (expected.sourceId != sourceId || access.value != expected) throw CancellationException("AudioMuse resolution access changed")
    }

    private data class Descriptor(
        val title: String,
        val artist: String?,
        val album: String?,
    )

    private fun descriptor(track: AudioMuseTrack): Descriptor? {
        val title = normalise(track.title.orEmpty()).takeIf { it.isNotEmpty() } ?: return null
        return Descriptor(
            title,
            normalise(track.author.orEmpty()).takeIf {
                it.isNotEmpty()
            },
            normalise(track.album.orEmpty()).takeIf { it.isNotEmpty() },
        )
    }

    private fun matches(
        key: Descriptor,
        track: AudioMuseLocalTrack,
    ): Boolean =
        key.title == normalise(track.title) &&
            (key.artist == null || key.artist == normalise(track.artist.orEmpty())) &&
            (key.album == null || key.album == normalise(track.album.orEmpty()))

    private class UniqueMatch {
        private var count = 0
        private var candidate: String? = null
        val id: String? get() = candidate.takeIf { count == 1 }

        fun add(id: String) {
            count = (count + 1).coerceAtMost(2)
            candidate = id
        }
    }

    companion object {
        const val MAX_BATCH = 500
        private const val PAGE_SIZE = 500
        private val MARKS = Regex("\\p{M}+")
        private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

        internal fun normalise(value: String): String =
            SEPARATORS.replace(MARKS.replace(Normalizer.normalize(value, Normalizer.Form.NFKD), "").lowercase(Locale.ROOT), "")
    }
}
