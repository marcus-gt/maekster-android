package com.subtracks.playback

import com.subtracks.data.db.MixDao
import com.subtracks.data.mix.InstantMix
import com.subtracks.data.mix.MixSeed
import com.subtracks.data.repo.QueueRepository
import com.subtracks.data.repo.QueueSnapshot
import kotlinx.coroutines.sync.Mutex

/** One bounded refill attempt. The controller must refresh its snapshot/player window after a true result. */
class AutoplayRefill(
    private val queue: QueueRepository,
    private val dao: MixDao,
    private val generate: suspend (Long, String, Boolean) -> List<String>,
    private val enabled: () -> Boolean,
    private val activeSourceId: suspend () -> Long?,
) {
    private val refill = Mutex()

    suspend fun extend(
        snapshot: QueueSnapshot,
        position: Long,
        downloadedOnly: Boolean,
        commit: suspend (QueueSnapshot, Long, List<String>) -> Boolean = queue::appendExtension,
    ): Boolean {
        if (!enabled() || snapshot.shuffled || position !in 0 until snapshot.size ||
            snapshot.size - position - 1 > REMAINING_THRESHOLD || !refill.tryLock()
        ) {
            return false
        }
        try {
            val modes = queue.modes()
            if (modes.shuffle || modes.repeat != 0) return false
            val sourceId = activeSourceId() ?: return false
            if (snapshot.entries.any { it.entry.sourceId != sourceId }) return false
            val seed = queue.itemAt(snapshot, position)?.song ?: return false
            if (seed.sourceId != sourceId) return false
            val nearby =
                queue
                    .range(snapshot, (position - HISTORY_LIMIT).coerceAtLeast(0), snapshot.size - 1)
                    .map { it.item.song.id }
                    .toSet()
            val recent = dao.recentPlays(sourceId, HISTORY_LIMIT.toInt()).map { it.songId }.toSet()
            val candidates = generate(sourceId, seed.id, downloadedOnly).take(BATCH_SIZE).distinct()
            val known = if (candidates.isEmpty()) emptySet() else dao.existing(sourceId, candidates, downloadedOnly).map { it.id }.toSet()
            val picks = candidates.filter { it in known && it !in nearby && it !in recent }
            if (!enabled() || activeSourceId() != sourceId) return false
            return commit(snapshot, sourceId, picks)
        } finally {
            refill.unlock()
        }
    }

    companion object {
        const val REMAINING_THRESHOLD = 15L
        const val HISTORY_LIMIT = 50L
        const val BATCH_SIZE = 50

        fun of(
            queue: QueueRepository,
            dao: MixDao,
            mix: InstantMix,
            enabled: () -> Boolean,
            activeSourceId: suspend () -> Long?,
        ) = AutoplayRefill(
            queue,
            dao,
            generate = { sourceId, songId, downloadedOnly ->
                mix.build(sourceId, MixSeed.Song(songId), online = !downloadedOnly, targetSize = BATCH_SIZE).songIds
            },
            enabled,
            activeSourceId,
        )
    }
}
