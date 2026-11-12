package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
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
class ClearUpcomingActionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun check(
        fontScale: Float,
        succeeds: Boolean,
        image: String? = null,
    ) {
        var clears = 0
        var undos = 0
        val song = Song(1, "s1", "al1", "ar1", "Current song", "Album", "Artist", 200, 1, 1, null)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    QueueScreen(
                        rows = listOf(QueueRow(1, 0, AlbumSongItem(song, null))),
                        ready = true,
                        currentPosition = 0,
                        currentSongId = "s1",
                        contextTitle = "Album",
                        coverArt = { _, _ -> null },
                        onBack = {},
                        onPlay = {},
                        onRemove = {},
                        onReorder = { _, _ -> },
                        onMove = { _, _ -> false },
                        onLoadOlder = {},
                        onLoadNewer = {},
                        onUndo = { undos++ },
                        canClearUpcoming = true,
                        onClearUpcoming = {
                            clears++
                            succeeds
                        },
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Queue options").performClick()
        rule.onNodeWithText("Clear upcoming").performClick()
        rule.waitForIdle()
        assertEquals(1, clears)
        if (succeeds) {
            rule.onNodeWithText("Upcoming tracks cleared").assertIsDisplayed()
            image?.let { rule.onRoot().captureRoboImage(it) }
            rule.onNodeWithText("Undo").performClick()
            assertEquals(1, undos)
        } else {
            rule.onNodeWithText("Upcoming tracks cleared").assertDoesNotExist()
            rule.onNodeWithText("Undo").assertDoesNotExist()
        }
    }

    @Test fun clearUpcomingOffersUndoAfterSuccess() = check(1f, true, "src/test/screenshots/queue_clear_upcoming.png")

    @Test fun clearUpcomingUndoRemainsReadableAtLargeFontScale() =
        check(
            2f,
            true,
            "src/test/screenshots/queue_clear_upcoming_large_font.png",
        )

    @Test fun rejectedClearDoesNotOfferUndoForAnEarlierEdit() = check(1f, false)
}
