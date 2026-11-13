package com.subtracks.data.prefs

import com.subtracks.data.lyrics.LyricsSource

const val LYRICS_TIMING_OFFSET_LIMIT_MS = 3000

data class LyricsPreferences(
    val source: LyricsSource = LyricsSource.Auto,
    val allowLrclib: Boolean = false,
    val showCurrentLine: Boolean = true,
    val timingOffsetMs: Int = 0,
)
