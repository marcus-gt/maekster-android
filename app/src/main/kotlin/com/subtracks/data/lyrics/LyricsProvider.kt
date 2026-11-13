package com.subtracks.data.lyrics

import com.subtracks.data.model.Song

fun interface LyricsProvider {
    suspend fun fetch(song: Song): List<Lyrics>
}
