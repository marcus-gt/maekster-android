package com.subtracks.playback

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric: PlaybackService.kt's top-level session command needs Android's Bundle.
@RunWith(AndroidJUnit4::class)
class TaskRemovedTest {
    @Test
    fun playingKeepsTheService() {
        assertTrue(keepPlayingOnTaskRemoved(true, Player.STATE_READY, 3))
    }

    @Test
    fun bufferingTowardsPlaybackKeepsTheService() {
        assertTrue(keepPlayingOnTaskRemoved(true, Player.STATE_BUFFERING, 1))
    }

    @Test
    fun pausedStops() {
        assertFalse(keepPlayingOnTaskRemoved(false, Player.STATE_READY, 3))
        assertFalse(keepPlayingOnTaskRemoved(false, Player.STATE_BUFFERING, 3))
    }

    @Test
    fun endedIdleOrEmptyStops() {
        assertFalse(keepPlayingOnTaskRemoved(true, Player.STATE_ENDED, 3))
        assertFalse(keepPlayingOnTaskRemoved(true, Player.STATE_IDLE, 3))
        assertFalse(keepPlayingOnTaskRemoved(true, Player.STATE_READY, 0))
    }
}
