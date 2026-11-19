package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.HomeGenreShelf
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h850dp")
class GenreRowsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun normalFont() = checkGrid(1f)

    @Test fun largeFont() = checkGrid(2f)

    private fun checkGrid(scale: Float) {
        val first = Album(1, "a", null, "First record", "Artist A", 0, null, null, null, null, 1)
        val second = first.copy(id = "b", name = "Second record", albumArtist = "Artist B")
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    HomeScreen(
                        feed = HomeFeed(),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        layout = HomeLayout(hidden = HomeShelf.entries.toSet() - HomeShelf.Genres),
                        genreShelves = listOf(HomeGenreShelf("Jazz", listOf(first, second))),
                        onAlbumClick = { opened += it.id },
                        onGenreClick = { opened += it },
                    )
                }
            }
        }
        val a = rule.onNodeWithText("First record").assertIsDisplayed()
        val b = rule.onNodeWithText("Second record").assertIsDisplayed()
        val firstBounds = a.fetchSemanticsNode().boundsInRoot
        val secondBounds = b.fetchSemanticsNode().boundsInRoot
        if (scale == 1f) {
            assertTrue(firstBounds.left < secondBounds.left)
        } else {
            assertTrue(firstBounds.top < secondBounds.top)
        }
        rule.onNodeWithText("Jazz").performClick()
        b.performClick()
        assertEquals(listOf("Jazz", "b"), opened)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_genre_grid_$scale.png")
    }
}
