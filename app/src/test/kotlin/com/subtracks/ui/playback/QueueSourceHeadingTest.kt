package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class QueueSourceHeadingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        title: String,
        similar: Boolean,
    ) {
        val song = Song(1, "s10", "al1", "ar1", "Generated song", "Album", "Artist", 200, 1, 1, null)
        rule.setContent {
            MaeksterTheme {
                QueueScreen(
                    rows = listOf(QueueRow(1, 0, AlbumSongItem(song, null))),
                    ready = true,
                    currentPosition = 0,
                    currentSongId = "s10",
                    contextTitle = title,
                    contextSimilar = similar,
                    coverArt = { _, _ -> null },
                    onBack = {},
                    onPlay = {},
                    onRemove = {},
                    onReorder = { _, _ -> },
                    onMove = { _, _ -> false },
                    onLoadOlder = {},
                    onLoadNewer = {},
                    onUndo = {},
                )
            }
        }
    }

    @Test fun similarHeadingStandsOnItsOwn() {
        show("Similar to Jazz tunes", similar = true)
        rule.onNodeWithText("Similar to Jazz tunes").assertIsDisplayed()
        rule.onNodeWithText("Playing from Similar to Jazz tunes").assertDoesNotExist()
        rule.onRoot().captureRoboImage("src/test/screenshots/queue_similar_heading.png")
    }

    @Test fun sourceHeadingSaysWhereItPlaysFrom() {
        show("Jazz tunes", similar = false)
        rule.onNodeWithText("Playing from Jazz tunes").assertIsDisplayed()
    }
}
