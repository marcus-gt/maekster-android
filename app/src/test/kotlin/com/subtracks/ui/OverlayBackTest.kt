package com.subtracks.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBackTest {
    @Test
    fun backClosesTheQueueThenTheLyricsThenNowPlaying() {
        assertEquals(OverlayBack.Queue, overlayBack(showingQueue = true, nowPlayingOpen = true, showingLyrics = true))
        assertEquals(OverlayBack.Lyrics, overlayBack(showingQueue = false, nowPlayingOpen = true, showingLyrics = true))
        assertEquals(OverlayBack.NowPlaying, overlayBack(showingQueue = false, nowPlayingOpen = true, showingLyrics = false))
        assertEquals(OverlayBack.Route, overlayBack(showingQueue = false, nowPlayingOpen = false, showingLyrics = true))
    }
}
