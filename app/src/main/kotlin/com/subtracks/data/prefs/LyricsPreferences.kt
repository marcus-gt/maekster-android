package com.subtracks.data.prefs

import com.subtracks.data.lyrics.LyricsSource

const val LYRICS_TIMING_OFFSET_LIMIT_MS = 3000

/**
 * As on iOS, Auto falls back to LRCLIB for timed lyrics when the server's are plain or missing; the listener can turn
 * it off in Settings > Lyrics, and an explicit choice is kept (owner decision, UX-26 / D10, 20 Nov 2026).
 */
const val LRCLIB_ALLOWED_BY_DEFAULT = true

data class LyricsPreferences(
    val source: LyricsSource = LyricsSource.Auto,
    val allowLrclib: Boolean = LRCLIB_ALLOWED_BY_DEFAULT,
    val showCurrentLine: Boolean = true,
    val timingOffsetMs: Int = 0,
)
