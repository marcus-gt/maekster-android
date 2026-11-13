package com.subtracks.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsTimingTest {
    private fun lyrics(offset: Long = 0) =
        Lyrics(
            listOf(LyricsLine("First", 1000), LyricsLine("Second", 3000), LyricsLine("Translation", 3000), LyricsLine("Last", 5000)),
            true,
            offsetMs = offset,
        )

    @Test
    fun findsTheLastStartedLineAtBoundariesAndAfterBackwardSeeks() {
        val timing = LyricsTiming(lyrics())
        assertNull(timing.currentLine(-1))
        assertNull(timing.currentLine(999))
        assertEquals(0, timing.currentLine(1000))
        assertEquals(0, timing.currentLine(2999))
        assertEquals(2, timing.currentLine(3000))
        assertEquals(3, timing.currentLine(9000))
        assertEquals(0, timing.currentLine(1500))
    }

    @Test
    fun positiveProviderOffsetAdvancesButPositiveUserCorrectionDelays() {
        val timing = LyricsTiming(lyrics(offset = 250), correctionMs = 100)
        assertNull(timing.currentLine(849))
        assertEquals(0, timing.currentLine(850))
        assertEquals(850L, timing.seekPosition(0))
        assertEquals(2850L, timing.seekPosition(1))
        assertEquals(2, timing.currentLine(timing.seekPosition(1)!!))
        val delayed = LyricsTiming(lyrics(offset = -200), correctionMs = 300)
        assertEquals(1500L, delayed.seekPosition(0))
        assertNull(delayed.currentLine(1499))
        assertEquals(0, delayed.currentLine(1500))
    }

    @Test
    fun seekClampsToThePlayableRangeWithoutChangingHighlightTiming() {
        val timing = LyricsTiming(lyrics(offset = 1500))
        assertEquals(0L, timing.seekPosition(0))
        assertEquals(0, timing.currentLine(0))
        assertEquals(2000L, timing.seekPosition(3, durationMs = 2000))
        assertNull(timing.seekPosition(-1))
        assertNull(timing.seekPosition(4))
        assertEquals(3500L, timing.seekPosition(3, durationMs = -1))
    }

    @Test
    fun plainInstrumentalAndMissingTimesNeverInventSeekTargets() {
        for (track in listOf(
            Lyrics(listOf(LyricsLine("Plain", 1000)), false),
            Lyrics(emptyList(), false, instrumental = true),
            Lyrics(listOf(LyricsLine("No timing"), LyricsLine("Bad", -1)), true),
        )) {
            val timing = LyricsTiming(track)
            assertNull(timing.currentLine(10_000))
            assertNull(timing.seekPosition(0))
        }
    }

    @Test
    fun extremeProviderOffsetsCannotOverflowOrReverseTime() {
        val track = Lyrics(listOf(LyricsLine("Line", 0)), true, offsetMs = Long.MIN_VALUE)
        val timing = LyricsTiming(track, correctionMs = -3000)
        assertEquals(Long.MAX_VALUE - 2999, timing.seekPosition(0))
        assertNull(timing.currentLine(10_000))
        assertEquals(Long.MAX_VALUE, LyricsTiming(track, correctionMs = Long.MAX_VALUE).seekPosition(0))
        val advanced = LyricsTiming(track.copy(offsetMs = Long.MAX_VALUE), correctionMs = Long.MIN_VALUE)
        assertEquals(0L, advanced.seekPosition(0))
        assertEquals(0, advanced.currentLine(0))
    }
}
