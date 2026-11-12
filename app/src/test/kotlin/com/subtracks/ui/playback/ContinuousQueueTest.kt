package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
class ContinuousQueueTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        fontScale: Float,
        count: Int = 4,
        initialIndex: Int = 0,
        title: String = "Kid A",
    ) {
        val rows =
            (1..count).map { index ->
                val song = Song(1, "s$index", "al1", "ar1", "Song $index", "Album", "Artist", 200, index.toLong(), 1, null)
                QueueRow(index.toLong(), index - 1L, AlbumSongItem(song, null), upNext = index in 2..3)
            }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    QueueScreen(
                        rows = rows,
                        ready = true,
                        currentPosition = initialIndex.toLong(),
                        currentSongId = "s${initialIndex + 1}",
                        initialIndex = initialIndex,
                        contextTitle = title,
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
    }

    private fun check(
        fontScale: Float,
        image: String,
    ) {
        show(fontScale)
        rule.onAllNodesWithText("Playing from Kid A").assertCountEquals(1)
        rule.onNodeWithText("Playing from Kid A").assertIsDisplayed()
        rule.onNodeWithText("Up next").assertDoesNotExist()
        rule.onNodeWithText("Next up").assertDoesNotExist()
        (1..4).forEach { rule.onNodeWithTag("queue-row-$it").assertIsDisplayed() }
        rule.onRoot().captureRoboImage(image)
    }

    @Test fun contextAndManualTracksFormOneList() = check(1f, "src/test/screenshots/queue_continuous.png")

    @Test fun continuousQueueIsReadableAtLargeFontScale() = check(2f, "src/test/screenshots/queue_continuous_large_font.png")

    private fun checkCollectionTitle(
        fontScale: Float,
        image: String,
    ) {
        show(fontScale, title = "Favorites")
        rule.onNodeWithText("Playing from Favorites").assertIsDisplayed()
        rule.onRoot().captureRoboImage(image)
    }

    @Test fun collectionTitleIsVisible() = checkCollectionTitle(1f, "src/test/screenshots/queue_collection.png")

    @Test fun collectionTitleIsReadableAtLargeFontScale() = checkCollectionTitle(2f, "src/test/screenshots/queue_collection_large_font.png")

    @Test fun openingAtTheCursorUsesRowIndicesWithoutRunHeaders() {
        show(1f, count = 40, initialIndex = 20)
        rule.onNodeWithTag("queue-row-21").assertIsDisplayed()
        rule.onNodeWithText("Playing from Kid A").assertIsDisplayed()
    }
}
