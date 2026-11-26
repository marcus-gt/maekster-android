package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.artworkColorsFromSeed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NowPlayingHeaderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(
        source: String,
        kind: QueueKind,
        onOpenOutputSwitcher: (() -> Unit)? = null,
        audioOutputName: String? = null,
    ) {
        composeRule.setContent {
            MaeksterTheme {
                NowPlayingScreen(
                    state =
                        PlaybackState(
                            item =
                                QueueItem(
                                    id = "s1",
                                    title = "Mississippi",
                                    artist = "Bob Dylan",
                                    album = "Love and Theft",
                                    coverArtId = null,
                                ),
                            context = QueueContext(kind, 1, "ref"),
                            durationMs = 300_000,
                        ),
                    positionMs = 0,
                    title = source,
                    coverArt = CoverArtRef("art", "test:art"),
                    artwork = artworkColorsFromSeed(android.graphics.Color.rgb(120, 80, 200)),
                    onBack = {},
                    onQueue = {},
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    onOpenOutputSwitcher = onOpenOutputSwitcher,
                    audioOutputName = audioOutputName,
                )
            }
        }
    }

    @Test
    fun theHeaderIsStaticAndTheSecondLineIsTheType() {
        render("Bob Dylan radio", QueueKind.Song)

        composeRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeRule.onNodeWithText("Bob Dylan radio").assertIsDisplayed()
        composeRule.onNodeWithText("NOW PLAYING: SONG").assertDoesNotExist()
        composeRule.onNodeWithText("NOW PLAYING:", substring = true).assertDoesNotExist()
    }

    @Test
    fun outputActionInvokesTheProvidedCallback() {
        var opens = 0
        render("Album", QueueKind.Album, onOpenOutputSwitcher = { opens++ })

        composeRule.onNodeWithContentDescription("Change audio output").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun outputActionNamesTheSelectedExternalDevice() {
        render("Album", QueueKind.Album, onOpenOutputSwitcher = {}, audioOutputName = "AirPods")

        composeRule.onNodeWithContentDescription("Change audio output. Current output: AirPods").assertIsDisplayed()
    }

    @Test
    fun outputActionIsAbsentWhenUnavailable() {
        render("Album", QueueKind.Album)

        composeRule.onNodeWithContentDescription("Change audio output").assertDoesNotExist()
    }

    @Test
    fun theHeaderDoesNotDependOnTheCurrentEntry() {
        render("Album", QueueKind.Album)

        composeRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeRule.onNodeWithText("NOW PLAYING: ALBUM").assertDoesNotExist()
    }
}
