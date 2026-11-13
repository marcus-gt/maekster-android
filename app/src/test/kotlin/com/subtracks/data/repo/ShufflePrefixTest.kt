package com.subtracks.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ShufflePrefixTest {
    @Test
    fun linearShuffleAndSparseRemovalPrefixesRoundTrip() {
        val order = ShuffleState(0, 10, linear = true).withRemoved(2).withRemoved(7)
        (0L until order.liveSize).forEach {
            assertEquals(it, order.flatFor(it))
            assertEquals(it, order.playFor(it))
        }
        val prefix = ShufflePrefix(order, 5)
        val restored = ShufflePrefix.decode(prefix.encode())!!
        assertEquals("2|0|10|5|1|2,7", prefix.encode())
        (0L until 5).forEach { assertEquals(it, restored.flatFor(it)) }
        assertNull(restored.flatFor(5))
    }

    @Test
    fun persistenceRestoresTheOriginalDomainAndSparseRemovals() {
        val state = ShuffleState.restore(Long.MIN_VALUE, 31, "0,4,17,30")!!
        val prefix = ShufflePrefix(state, 12)
        assertEquals("1|-9223372036854775808|31|12|0,4,17,30", prefix.encode())
        val restored = ShufflePrefix.decode(prefix.encode())!!
        assertEquals(prefix.size, restored.size)
        (0L until prefix.size).forEach {
            assertEquals(prefix.flatFor(it), restored.flatFor(it))
            assertEquals(it, restored.playFor(restored.flatFor(it)!!))
        }
        assertNull(restored.flatFor(12))
        assertEquals(prefix.encode(), restored.encode())
    }

    @Test
    fun emptyAndHugePrefixesRoundTripWithoutEnumeratingPositions() {
        for (count in listOf(0L, 1L, 800_000_001L)) {
            val original = ShufflePrefix(ShuffleState(Long.MAX_VALUE, 1_000_000_000), count)
            val restored = ShufflePrefix.decode(original.encode())!!
            assertEquals(count, restored.size)
            if (count > 0) assertEquals(original.flatFor(count - 1), restored.flatFor(count - 1))
        }
    }

    @Test
    fun invalidPersistenceNeverCreatesAnotherPermutation() {
        for (encoded in listOf(
            "",
            "2|42|10|3|",
            "1|x|10|3|",
            "1|42|10|-1|",
            "1|42|10|11|",
            "1|42|10|10|1",
            "1|42|10|3|1,1",
            "1|42|10|3|10",
            "1|42|10|3|2,1",
            "1|42|10|3|x",
            "1|42|10|3",
            "1|42|10|3||",
        )) {
            assertNull(encoded, ShufflePrefix.decode(encoded))
        }
    }

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
