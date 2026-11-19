package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.artworkColorsFromSeed
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
    fun theHeaderDoesNotDependOnTheCurrentEntry() {
        render("Album", QueueKind.Album)

        composeRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeRule.onNodeWithText("NOW PLAYING: ALBUM").assertDoesNotExist()
    }
}
