package com.subtracks.data.repo

/**
 * A prefix in playback order over an unchanged shuffle domain. Canonical positions still refer to
 * the original live entries (after sparse removals), not a compacted list of retained songs.
 * No prefix positions are enumerated or stored.
 */
class ShufflePrefix(
    private val shuffle: ShuffleState,
    val size: Long,
) {
    init {
        require(size in 0..shuffle.liveSize)
    }

    fun flatFor(play: Long): Long? = if (play in 0 until size) shuffle.flatFor(play) else null

    fun playFor(canonical: Long): Long? {
        if (canonical !in 0 until shuffle.liveSize) return null
        return shuffle.playFor(canonical).takeIf { it in 0 until size }
    }

    fun take(count: Long): ShufflePrefix {
        require(count in 0..size)
        return ShufflePrefix(shuffle, count)
    }
}
