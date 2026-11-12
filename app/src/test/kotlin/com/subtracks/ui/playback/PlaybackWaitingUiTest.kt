package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.PlaybackWaiting
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
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
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    NowPlayingScreen(
                        state =
                            PlaybackState(
                                item = QueueItem("s1", "Everything In Its Right Place", "Radiohead", "Kid A", null),
                                waiting = waiting,
                                durationMs = 251000,
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

    @Test fun noWaitingStatusIsShownWhenReady() {
        show(null)
        rule.onNodeWithText("Preparing").assertDoesNotExist()
        rule.onNodeWithText("Buffering").assertDoesNotExist()
        rule.onNodeWithText("Reconnecting").assertDoesNotExist()
    }
}
