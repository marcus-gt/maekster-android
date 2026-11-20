package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.playback.PlaybackOrigin
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.PlaybackWaiting
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class PlaybackWaitingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        waiting: PlaybackWaiting?,
        fontScale: Float = 1f,
    ) {
        val state = mutableStateOf(waiting)
        show(state, fontScale)
    }

    private fun show(
        waitingState: State<PlaybackWaiting?>,
        fontScale: Float = 1f,
        origin: PlaybackOrigin? = null,
    ) {
        rule.setContent {
            val waiting by waitingState
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    NowPlayingScreen(
                        state =
                            PlaybackState(
                                item = QueueItem("s1", "Everything In Its Right Place", "Radiohead", "Kid A", null),
                                waiting = waiting,
                                durationMs = 251000,
                                playbackOrigin = origin,
                            ),
                        positionMs = 62000,
                        title = "Kid A",
                        coverArt = null,
                        artwork = null,
                        onBack = {},
                        onQueue = {},
                        onPlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                    )
                }
            }
        }
    }

    @Test fun preparingHasAnExplicitStatus() {
        show(PlaybackWaiting.Preparing)
        rule.onNodeWithText("Preparing").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test fun bufferingHasAnExplicitStatus() {
        show(PlaybackWaiting.Buffering)
        rule.onNodeWithText("Buffering").assertIsDisplayed()
    }

    @Test fun reconnectingIsVisibleWhileThePlayerIsNotPlaying() {
        show(PlaybackWaiting.Reconnecting)
        rule.onNodeWithText("Reconnecting").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_reconnecting.png")
    }

    @Test fun reconnectingIsReadableAtLargeFontScale() {
        show(PlaybackWaiting.Reconnecting, 2f)
        rule.onNodeWithText("Reconnecting").assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_reconnecting_large_font.png")
    }

    // A short buffering blip used to add a line above the controls, nudging them down for a moment.
    @Test fun bufferingDoesNotMoveTheControls() = assertWaitingKeepsLayout(origin = null)

    @Test fun bufferingWhileStreamingDoesNotMoveTheControls() = assertWaitingKeepsLayout(PlaybackOrigin.Streaming)

    private fun assertWaitingKeepsLayout(origin: PlaybackOrigin?) {
        val waiting = mutableStateOf<PlaybackWaiting?>(null)
        show(waiting, origin = origin)
        val ready = rule.onNodeWithContentDescription("Next").getBoundsInRoot()
        val coverReady = rule.onNodeWithTag(NOW_PLAYING_COVER_TAG).getBoundsInRoot()
        waiting.value = PlaybackWaiting.Buffering
        rule.onNodeWithText("Buffering").assertIsDisplayed()
        assertEquals(ready, rule.onNodeWithContentDescription("Next").getBoundsInRoot())
        assertEquals(coverReady, rule.onNodeWithTag(NOW_PLAYING_COVER_TAG).getBoundsInRoot())
        // The format line keeps its space but is neither shown nor announced under the status.
        rule.onNodeWithText("Streaming").assertDoesNotExist()
        waiting.value = null
        if (origin != null) rule.onNodeWithText("Streaming").assertIsDisplayed()
    }

    @Test fun noWaitingStatusIsShownWhenReady() {
        show(null)
        rule.onNodeWithText("Preparing").assertDoesNotExist()
        rule.onNodeWithText("Buffering").assertDoesNotExist()
        rule.onNodeWithText("Reconnecting").assertDoesNotExist()
    }
}
