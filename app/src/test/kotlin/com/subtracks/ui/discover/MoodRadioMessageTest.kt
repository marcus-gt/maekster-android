package com.subtracks.ui.discover

import com.subtracks.R
import com.subtracks.data.audiomuse.MoodRadioResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodRadioMessageTest {
    @Test fun eachReasonForNotStartingHasItsOwnMessage() {
        assertNull(moodRadioMessage(MoodRadioResult.Ready(listOf("a"))))
        val messages =
            listOf(MoodRadioResult.NoMatches, MoodRadioResult.NotInLibrary, MoodRadioResult.Unavailable).map(::moodRadioMessage)
        assertEquals(
            listOf(R.string.mood_radio_empty, R.string.mood_radio_not_in_library, R.string.mood_radio_unavailable),
            messages,
        )
    }
}
