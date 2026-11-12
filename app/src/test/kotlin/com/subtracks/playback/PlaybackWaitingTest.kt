package com.subtracks.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackWaitingTest {
    @Test fun waitingAppearsAtSevenHundredMillisecondsAndClearsImmediately() =
        runTest {
            val indicator = PlaybackWaitingIndicator(this) {}
            indicator.update(PlaybackWaiting.Preparing, "song")
            advanceTimeBy(699)
            runCurrent()
            assertNull(indicator.value)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(PlaybackWaiting.Preparing, indicator.value)
            indicator.update(null, "song")
            assertNull(indicator.value)
        }

    @Test fun shortWaitDoesNotFlashLater() =
        runTest {
            val indicator = PlaybackWaitingIndicator(this) {}
            indicator.update(PlaybackWaiting.Buffering, "song")
            advanceTimeBy(300)
            indicator.update(null, "song")
            advanceTimeBy(1000)
            runCurrent()
            assertNull(indicator.value)
        }

    @Test fun changingReasonKeepsTheEpisodeDelayAndVisibleStatusStaysCurrent() =
        runTest {
            val indicator = PlaybackWaitingIndicator(this) {}
            indicator.update(PlaybackWaiting.Preparing, "song")
            advanceTimeBy(500)
            indicator.update(PlaybackWaiting.Reconnecting, "song")
            advanceTimeBy(200)
            runCurrent()
            assertEquals(PlaybackWaiting.Reconnecting, indicator.value)
            indicator.update(PlaybackWaiting.Buffering, "song")
            assertEquals(PlaybackWaiting.Buffering, indicator.value)
        }

    @Test fun anotherTrackStartsANewDelayAndResetCancelsIt() =
        runTest {
            val indicator = PlaybackWaitingIndicator(this) {}
            indicator.update(PlaybackWaiting.Buffering, "first")
            advanceTimeBy(700)
            runCurrent()
            indicator.update(PlaybackWaiting.Preparing, "second")
            assertNull(indicator.value)
            advanceTimeBy(699)
            runCurrent()
            assertNull(indicator.value)
            indicator.reset()
            advanceTimeBy(1000)
            runCurrent()
            assertNull(indicator.value)
        }
}
