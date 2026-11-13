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
    val canonicalSize: Long get() = shuffle.liveSize

    init {
        require(size in 0..shuffle.liveSize)
    }

    fun flatFor(play: Long): Long? = if (play in 0 until size) shuffle.flatFor(play) else null

    fun playFor(canonical: Long): Long? {
        if (canonical !in 0 until shuffle.liveSize) return null
        return shuffle.playFor(canonical).takeIf { it in 0 until size }
    }

    fun encode(): String = listOf("1", shuffle.seed, shuffle.domain, size, shuffle.encodeRemoved()).joinToString("|")

    companion object {
        fun decode(encoded: String): ShufflePrefix? {
            val fields = encoded.split('|')
            if (fields.size != 5 || fields[0] != "1") return null
            val seed = fields[1].toLongOrNull() ?: return null
            val domain = fields[2].toLongOrNull() ?: return null
            val count = fields[3].toLongOrNull() ?: return null
            val shuffle = ShuffleState.restore(seed, domain, fields[4]) ?: return null
            if (count !in 0..shuffle.liveSize) return null
            return ShufflePrefix(shuffle, count)
        }
    }

    fun take(count: Long): ShufflePrefix {
        require(count in 0..size)
        return ShufflePrefix(shuffle, count)
    }
}
