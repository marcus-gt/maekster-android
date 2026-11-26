package com.subtracks.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayBackTest {
    @Test
    fun backClosesTheQueueThenTheLyricsThenNowPlaying() {
        assertEquals(OverlayBack.Queue, overlayBack(showingQueue = true, nowPlayingOpen = true, showingLyrics = true))
        assertEquals(OverlayBack.Lyrics, overlayBack(showingQueue = false, nowPlayingOpen = true, showingLyrics = true))
        assertEquals(OverlayBack.NowPlaying, overlayBack(showingQueue = false, nowPlayingOpen = true, showingLyrics = false))
        assertNull(overlayBack(showingQueue = false, nowPlayingOpen = false, showingLyrics = true))
    }

    @Test
    fun withNoOverlayTheNavigationHostGetsTheBack() {
        val harness = Harness(overlay = null)
        harness.dispatcher.onBackPressed()
        assertEquals(1, harness.routePops)
        assertEquals(emptyList<String>(), harness.events)
    }

    @Test
    fun withAnOverlayTheRouteUnderItStays() {
        val harness = Harness(overlay = OverlayBack.NowPlaying)
        harness.dispatcher.onBackPressed()
        assertEquals(0, harness.routePops)
        assertEquals(listOf("commit NowPlaying"), harness.events)
    }

    @Test
    fun aGesturePreviewsClosingNowPlayingAndACancelKeepsItOpen() {
        val harness = Harness(overlay = OverlayBack.NowPlaying)
        harness.dispatcher.dispatchOnBackStarted(event(0f))
        harness.dispatcher.dispatchOnBackProgressed(event(0.5f))
        harness.dispatcher.dispatchOnBackCancelled()
        assertEquals(listOf("preview NowPlaying 0.0", "preview NowPlaying 0.5", "cancel NowPlaying"), harness.events)
        assertEquals(0, harness.routePops)
    }

    @Test
    fun aReleasedGestureClosesTheOverlayItStartedOn() {
        val harness = Harness(overlay = OverlayBack.Queue)
        harness.dispatcher.dispatchOnBackStarted(event(0f))
        harness.overlay = OverlayBack.NowPlaying
        harness.dispatcher.dispatchOnBackProgressed(event(1f))
        harness.dispatcher.onBackPressed()
        assertEquals(listOf("preview Queue 0.0", "preview Queue 1.0", "commit Queue"), harness.events)
        assertEquals(0, harness.routePops)
    }

    @Test
    fun theNowPlayingPreviewSlidesItPartWayDown() {
        assertEquals(1f, nowPlayingBackPreview(0f))
        assertEquals(1f - NOW_PLAYING_BACK_PREVIEW, nowPlayingBackPreview(1f))
        assertEquals(1f - NOW_PLAYING_BACK_PREVIEW, nowPlayingBackPreview(2f))
    }

    private fun event(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 0f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private class Harness(
        var overlay: OverlayBack?,
    ) {
        val dispatcher = OnBackPressedDispatcher()
        val events = mutableListOf<String>()
        var routePops = 0
        private val callback =
            OverlayBackCallback(
                target = { overlay },
                onPreview = { target, progress -> events += "preview $target $progress" },
                onCancel = { events += "cancel $it" },
                onCommit = { events += "commit $it" },
            )

        init {
            dispatcher.addCallback(
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        routePops++
                    }
                },
            )
            callback.isEnabled = overlay != null
            dispatcher.addCallback(callback)
        }
    }
}
