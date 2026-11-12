package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.AudioEncoding
import com.subtracks.playback.PlaybackOrigin
import com.subtracks.playback.PlaybackState
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
class PlaybackFormatLineTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        origin: PlaybackOrigin?,
        encoding: AudioEncoding?,
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
                                audioEncoding = encoding,
                                playbackOrigin = origin,
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

    @Test fun transcodedAudioShowsTheDecoderFormatRatherThanTheLibraryFile() {
        show(PlaybackOrigin.Transcoding, AudioEncoding("audio/mpeg", 192000, 44100))
        rule.onNodeWithText("Transcoding · MP3 · 192 kbps · 44.1 kHz").assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_format.png")
    }

    @Test fun aDownloadUsesItsActualEncodingAtLargeFontScale() {
        show(PlaybackOrigin.Downloaded, AudioEncoding("audio/flac", sampleRate = 96000), 2f)
        rule.onNodeWithText("Downloaded · FLAC · 96 kHz").assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_format_large_font.png")
    }

    @Test fun unavailableEncodingDoesNotInventQuality() {
        show(PlaybackOrigin.Streaming, AudioEncoding(null, -1, -1))
        rule.onNodeWithText("Streaming").assertIsDisplayed()
    }

    @Test fun unknownOriginHidesTheLine() {
        show(null, AudioEncoding("audio/flac"))
        rule.onNodeWithText("FLAC", substring = true).assertDoesNotExist()
    }
}
