package com.subtracks.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ShufflePrefixTest {
    @Test
    fun persistedRemovalDataRejectsInvalidCoordinates() {
        for (encoded in listOf("x", "-1", "10", "2,2", "3,1", "1,")) {
            assertNull(ShuffleState.restore(42, 10, encoded))
        }
        assertNull(ShuffleState.restore(42, -1, null))
        assertNull(ShuffleState.restore(42, (1L shl 40) + 1, null))
        val state = ShuffleState.restore(42, 10, "1,4,8")!!
        assertEquals("1,4,8", state.encodeRemoved())
        assertEquals(7L, state.liveSize)
        (0L until state.liveSize).forEach { assertEquals(it, state.playFor(state.flatFor(it))) }
    }

    @Test
    fun everyCutKeepsTheOriginalOrderAndRejectsTheTail() {
        for (domain in listOf(1L, 2L, 7L, 16L, 33L)) {
            for (seed in listOf(0L, 42L, -1L, Long.MIN_VALUE)) {
                val order = (0 until domain).map { Shuffle.toFlat(seed, domain, it) }
                for (count in 0..domain) {
                    val prefix = ShufflePrefix(ShuffleState(seed, domain), count)
                    assertEquals(order.take(count.toInt()), (0 until count).map { prefix.flatFor(it) })
                    order.forEachIndexed { play, flat ->
                        assertEquals(play.toLong().takeIf { it < count }, prefix.playFor(flat))
                    }
                    assertNull(prefix.flatFor(-1))
                    assertNull(prefix.flatFor(count))
                    assertNull(prefix.playFor(-1))
                    assertNull(prefix.playFor(domain))
                }
            }
        }
    }

    @Test
    fun sparseRemovalsKeepTheSamePrefixInLiveCanonicalCoordinates() {
        val domain = 31L
        val seed = 42L
        val removed = setOf(0L, 4L, 17L, 30L)
        val remainingFlats = (0 until domain).filterNot { it in removed }
        val expected =
            (0 until domain)
                .map { Shuffle.toFlat(seed, domain, it) }
                .filterNot { it in removed }
                .map { remainingFlats.indexOf(it).toLong() }
        val shuffle = removed.fold(ShuffleState(seed, domain)) { state, flat -> state.withRemoved(flat) }
        for (count in 0..shuffle.liveSize) {
            val prefix = ShufflePrefix(shuffle, count)
            assertEquals(expected.take(count.toInt()), (0 until count).map { prefix.flatFor(it) })
            expected.forEachIndexed { play, flat ->
                assertEquals(play.toLong().takeIf { it < count }, prefix.playFor(flat))
            }
        }
    }

    @Test
    fun aBillionPositionPrefixUsesTheOriginalDomainWithoutExpandingIt() {
        val domain = 1_000_000_000L
        val seed = 7L
        val count = 800_000_001L
        val prefix = ShufflePrefix(ShuffleState(seed, domain), count)
        for (play in listOf(0L, 1L, 400_000_000L, count - 1)) {
            val flat = Shuffle.toFlat(seed, domain, play)
            assertEquals(flat, prefix.flatFor(play))
            assertEquals(play, prefix.playFor(flat))
        }
        assertNull(prefix.flatFor(count))
        assertNull(prefix.playFor(Shuffle.toFlat(seed, domain, count)))
    }

    @Test
    fun repeatedTruncationCannotRegrowThePrefix() {
        val original = ShufflePrefix(ShuffleState(42, 100), 80)
        val shorter = original.take(20)
        assertEquals(80L, original.size)
        assertEquals(20L, shorter.size)
        (0L until 20).forEach { assertEquals(original.flatFor(it), shorter.flatFor(it)) }
        assertNull(shorter.flatFor(20))
        assertThrows(IllegalArgumentException::class.java) { shorter.take(21) }
        assertThrows(IllegalArgumentException::class.java) { original.take(-1) }
        assertThrows(IllegalArgumentException::class.java) { ShufflePrefix(ShuffleState(42, 100), 101) }
        assertThrows(IllegalArgumentException::class.java) { ShufflePrefix(ShuffleState(42, 100), -1) }
    }
}
