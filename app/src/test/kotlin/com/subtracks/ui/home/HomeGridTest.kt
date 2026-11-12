package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.Playlist
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.settings.HomeLayoutDialog
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeGridTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun albumGrid() = renderGrid(1f, false)

    @Test fun playlistGridAtLargeFont() = renderGrid(2f, true)

    private fun renderGrid(
        fontScale: Float,
        playlists: Boolean,
    ) {
        val shelf = if (playlists) HomeShelf.YourPlaylists else HomeShelf.FavoriteAlbums
        var opened = ""
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaeksterTheme {
                    Surface {
                        HomeScreen(
                            feed =
                                if (playlists) {
                                    HomeFeed(
                                        yourPlaylists =
                                            (1..3).map {
                                                Playlist(
                                                    sourceId = 1,
                                                    id = "$it",
                                                    name = "Record $it",
                                                    comment = null,
                                                    coverArt = null,
                                                    songCount = 3,
                                                    created = 0,
                                                )
                                            },
                                    )
                                } else {
                                    HomeFeed(
                                        favoriteAlbums =
                                            (1..3).map {
                                                Album(
                                                    sourceId = 1,
                                                    id = "$it",
                                                    artistId = null,
                                                    name = "Record $it",
                                                    albumArtist = "Artist",
                                                    created = 0,
                                                    coverArt = null,
                                                    genre = null,
                                                    year = null,
                                                    starred = 1,
                                                    songCount = 3,
                                                )
                                            },
                                    )
                                },
                            coverArt = { _, _ -> null },
                            playingSongId = null,
                            layout = HomeLayout(grids = setOf(shelf)),
                            onAlbumClick = { opened = it.id },
                            onPlaylistClick = { opened = it.id },
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("${shelf.name}-grid").assertExists()
        val first = rule.onNodeWithText("Record 1").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("Record 3").performScrollTo()
        val third = rule.onNodeWithText("Record 3").fetchSemanticsNode().boundsInRoot
        assertTrue(third.top > first.top)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_grid_${shelf.name}.png")
        rule.onNodeWithText("Record 3").performClick()
        assertEquals("3", opened)
    }

    @Test
    fun editorSavesGridAndResetClearsIt() {
        var saved: HomeLayout? = null
        rule.setContent { MaeksterTheme { HomeLayoutDialog(HomeLayout(), { saved = it }, {}) } }
        val visibility = rule.onNodeWithContentDescription("Your playlists").fetchSemanticsNode().boundsInRoot
        val layout = rule.onNodeWithContentDescription("Your playlists: Layout: Carousel").fetchSemanticsNode().boundsInRoot
        assertEquals(visibility.center.y, layout.center.y, 1f)
        rule.onNodeWithContentDescription("Your playlists: Layout: Carousel").performClick()
        rule.onNodeWithContentDescription("Your playlists: Layout: Grid").assertExists()
        rule.onNodeWithText("Save").performClick()
        assertEquals(setOf(HomeShelf.YourPlaylists), saved!!.grids)
        rule.onNodeWithText("Reset to default").performScrollTo().performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(emptySet<HomeShelf>(), saved.grids)
    }
}
