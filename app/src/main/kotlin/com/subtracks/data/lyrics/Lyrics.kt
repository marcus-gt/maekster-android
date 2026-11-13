package com.subtracks.data.lyrics

data class LyricsLine(
    val text: String,
    val startMs: Long? = null,
)

data class Lyrics(
    val lines: List<LyricsLine>,
    val synced: Boolean,
    val language: String = "und",
    val offsetMs: Long = 0,
)
