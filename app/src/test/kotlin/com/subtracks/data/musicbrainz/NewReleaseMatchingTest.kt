package com.subtracks.data.musicbrainz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NewReleaseMatchingTest {
    @Test
    fun onlyRealUuidsCountAsMusicBrainzIds() {
        assertTrue(NewReleaseMatching.isMusicBrainzId("a74b1b7f-71a5-4011-9441-d0b5e4122711"))
        assertFalse(NewReleaseMatching.isMusicBrainzId("123456"))
        assertFalse(NewReleaseMatching.isMusicBrainzId(null))
    }

    @Test
    fun titlesDropDiacriticsQuotesArticlesAndEditionFurniture() {
        assertEquals(NewReleaseMatching.normalisedTitle("Tomorrow's Harvest"), NewReleaseMatching.normalisedTitle("Tomorrow’s Harvest"))
        assertEquals("amygdala", NewReleaseMatching.normalisedTitle("Amygdala (Deluxe Edition)"))
        assertEquals("ok computer", NewReleaseMatching.normalisedTitle("OK Computer - 2017 Remaster"))
        assertEquals("since i left you", NewReleaseMatching.normalisedTitle("Since I Left You [Expanded]"))
        assertEquals("tomorrow s harvest", NewReleaseMatching.normalisedTitle("The Tomorrow's Harvest"))
        assertEquals("cafe", NewReleaseMatching.normalisedTitle("Café"))
    }

    @Test
    fun namesIgnoreCaseDiacriticsPunctuationAndSpelledOutLetters() {
        assertEquals("royksopp", NewReleaseMatching.normalisedName("Röyksopp"))
        assertEquals("royksopp", NewReleaseMatching.normalisedName("Røyksopp"))
        assertEquals("lindstrom", NewReleaseMatching.normalisedName("Lindstrøm"))
        assertEquals("sigur ros", NewReleaseMatching.normalisedName("Sigur Rós!"))
    }

    @Test
    fun typeLabelsReleaseDatesAndDays() {
        assertEquals("Album", NewReleaseMatching.typeLabel("Album", emptyList()))
        assertEquals("Single · Remix", NewReleaseMatching.typeLabel("Single", listOf("Remix")))
        assertEquals("Release", NewReleaseMatching.typeLabel(null, emptyList()))
        assertTrue(NewReleaseMatching.isReleased("2026-09", "2026-10-27"))
        assertTrue(NewReleaseMatching.isReleased(null, "2026-10-27"))
        assertFalse(NewReleaseMatching.isReleased("2026-11-01", "2026-10-27"))
        assertEquals("2026-10-27", NewReleaseMatching.musicBrainzDay(1_793_102_400_000))
        assertEquals(LocalDate.of(2026, 9, 1), NewReleaseMatching.date("2026-09"))
        assertEquals(LocalDate.of(2026, 1, 1), NewReleaseMatching.date("2026"))
        assertNull(NewReleaseMatching.date(""))
        assertNull(NewReleaseMatching.date("2026-13"))
    }
}
