package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class QueueSwipeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun draggingDisablesSwipeRemoval() {
        val dragging = mutableStateOf(true)
        var removals = 0
        rule.setContent {
            MaeksterTheme {
                QueueSwipeRow(
                    dragging = dragging.value,
                    onRemove = { removals++ },
                    modifier = Modifier.width(300.dp).testTag("swipe"),
                ) {
                    Text("Dragging row", Modifier.height(80.dp))
                }
            }
        }
        rule.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(0, removals)
        rule.runOnIdle { dragging.value = false }
        rule.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, removals)
    }

    private fun check(
        upNext: Boolean,
        fontScale: Float,
        image: String,
    ) {
        val song = Song(1, "s1", "al1", "ar1", "Repeated song", "Album", "Artist", 200, 1, 1, null)
        val first = QueueRow(1, 10, AlbumSongItem(song, null), upNext)
        val second = QueueRow(2, 11, AlbumSongItem(song, null), upNext)
        val rows = mutableStateListOf(first, second)
        val removed = mutableListOf<Long>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    QueueScreen(
                        rows = rows,
                        ready = true,
                        currentPosition = 10,
                        currentSongId = "s1",
                        shuffle = true,
                        coverArt = { _, _ -> null },
                        onBack = {},
                        onPlay = {},
                        onRemove = { position ->
                            removed.add(position)
                            rows.removeAll { it.position == position }
                        },
                        onReorder = { _, _ -> },
                        onMove = { _, _ -> false },
                        onLoadOlder = {},
                        onLoadNewer = {},
                        onUndo = { rows.add(0, first.copy(id = 3)) },
                    )
                }
            }
        }

        rule.onNodeWithTag("queue-row-1").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(emptyList<Long>(), removed)
        rule.onNodeWithTag("queue-row-1").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(10L), removed)
        rule.onNodeWithTag("queue-row-1").assertDoesNotExist()
        rule.onNodeWithTag("queue-row-2").assertIsDisplayed()
        rule.onNodeWithText("Undo").assertIsDisplayed()
        rule.onRoot().captureRoboImage(image)
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithTag("queue-row-3").assertIsDisplayed()
        assertEquals(2, rows.size)
        rule.onNodeWithTag("queue-row-3").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf(10L, 10L), removed)
    }

    @Test fun contextSwipeRemovesTheCorrectDuplicateAndOffersUndo() = check(false, 1f, "src/test/screenshots/queue_swipe_undo.png")

    @Test fun manualQueueSwipeWorksAtLargeFontScale() = check(true, 2f, "src/test/screenshots/queue_swipe_undo_large_font.png")
}
