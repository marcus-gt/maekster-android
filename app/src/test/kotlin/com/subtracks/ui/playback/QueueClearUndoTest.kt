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
class QueueClearUndoTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun check(
        fontScale: Float,
        image: String,
    ) {
        var clears = 0
        var undos = 0
        val song = Song(1, "s1", "al1", "ar1", "Queued song", "Album", "Artist", 200, 1, 1, null)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    QueueScreen(
                        rows = listOf(QueueRow(1, 1, AlbumSongItem(song, null), upNext = true)),
                        ready = true,
                        currentPosition = 0,
                        currentSongId = null,
                        coverArt = { _, _ -> null },
                        onBack = {},
                        onPlay = {},
                        onRemove = {},
                        onReorder = { _, _ -> },
                        onMove = { _, _ -> false },
                        onLoadOlder = {},
                        onLoadNewer = {},
                        onUndo = { undos++ },
                        onClearUpNext = { clears++ },
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Clear up next").performClick()
        assertEquals(1, clears)
        rule.onNodeWithText("Up next cleared").assertIsDisplayed()
        rule.onRoot().captureRoboImage(image)
        rule.onNodeWithText("Undo").performClick()
        assertEquals(1, undos)
    }

    @Test fun clearingOffersSnackbarUndo() = check(1f, "src/test/screenshots/queue_clear_undo.png")

    @Test fun clearUndoWorksAtLargeFontScale() = check(2f, "src/test/screenshots/queue_clear_undo_large_font.png")
}
