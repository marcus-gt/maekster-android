package com.subtracks.data.playlist

/**
 * An append that can be retried safely. It remembers how often each song was in the playlist before the first
 * attempt; after a failure (or a lost response) [remaining] compares that with the playlist now and returns only
 * the occurrences that have not arrived. Duplicates that were already there, and songs others added meanwhile,
 * are left alone. The order of [songIds] is kept.
 */
class PlaylistAppendIntent(
    val songIds: List<String>,
    existingSongIds: List<String>,
) {
    private val baseline = existingSongIds.groupingBy { it }.eachCount()

    fun remaining(currentSongIds: List<String>): List<String> {
        val arrived =
            currentSongIds
                .groupingBy { it }
                .eachCount()
                .mapValuesTo(mutableMapOf()) { (id, count) -> (count - (baseline[id] ?: 0)).coerceAtLeast(0) }
        return songIds.filter { id ->
            val left = arrived[id] ?: 0
            if (left > 0) {
                arrived[id] = left - 1
                false
            } else {
                true
            }
        }
    }
}
