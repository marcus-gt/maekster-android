package com.subtracks.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatsTest {
    @Test
    fun losslessFormatsAreRecognisedWhateverTheCase() {
        listOf("flac", "ALAC", " wav ", "aiff", "dsf", "wv").forEach { assertTrue(it, AudioFormats.isLossless(it)) }
        listOf("mp3", "opus", "ogg", "m4a", "aac", "").forEach { assertFalse(it, AudioFormats.isLossless(it)) }
    }

    @Test
    fun badgesAreDistinctUppercaseSuffixesInTrackOrderSkippingUnknowns() {
        assertEquals(listOf("MP3", "FLAC"), AudioFormats.badges(listOf(null, "mp3", " ", "flac", "MP3", "ogg")))
        assertEquals(listOf("FLAC"), AudioFormats.badges(listOf("flac", "FLAC")))
        assertTrue(AudioFormats.badges(listOf(null, "")).isEmpty())
    }
}
