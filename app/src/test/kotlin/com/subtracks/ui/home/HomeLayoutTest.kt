package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
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
class HomeLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun album(name: String) =
        Album(
            sourceId = 1,
            id = name,
            artistId = null,
            name = name,
            albumArtist = "Artist",
            created = 0,
            coverArt = null,
            genre = null,
            year = null,
            starred = 1,
            songCount = 1,
        )

    @Test
    fun homeHidesSectionsAndUsesSavedOrder() {
        val layout = HomeLayout().move(HomeShelf.RecentlyAddedAlbums, -2).show(HomeShelf.Genres, false)
        rule.setContent {
            MaeksterTheme {
                Surface {
                    HomeScreen(
                        feed =
                            HomeFeed(
                                favoriteAlbums = listOf(album("Favorite")),
                                recentlyAddedAlbums = listOf(album("Added")),
                                genres = listOf("Rock"),
                            ),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        layout = layout,
                    )
                }
            }
        }
        rule.onNodeWithText("Genres").assertDoesNotExist()
        rule.onNodeWithText("Rock").assertDoesNotExist()
        val added =
            rule
                .onNodeWithText("Recently added")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val favorite =
            rule
                .onNodeWithText("Favorite albums")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(added < favorite)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_custom_order.png")
    }

    @Test
    fun homeOmitsAHiddenPopulatedShelf() {
        rule.setContent {
            MaeksterTheme {
                Surface {
                    HomeScreen(
                        feed = HomeFeed(favoriteAlbums = listOf(album("Favorite")), recentlyAddedAlbums = listOf(album("Added"))),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        layout = HomeLayout(hidden = setOf(HomeShelf.FavoriteAlbums)),
                    )
                }
            }
        }
        rule.onNodeWithText("Favorite albums").assertDoesNotExist()
        rule.onNodeWithText("Favorite").assertDoesNotExist()
        rule.onNodeWithText("Recently added").assertExists()
    }

    @Test
    fun editorCancelDoesNotSaveDraft() {
        var saves = 0
        var dismissed = false
        rule.setContent { MaeksterTheme { HomeLayoutDialog(HomeLayout(), { saves++ }, { dismissed = true }) } }
        rule.onNodeWithContentDescription("Your playlists").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, saves)
        assertTrue(dismissed)
    }

    @Test
    fun editorSavesVisibilityAndOrder() {
        var saved: HomeLayout? = null
        rule.setContent { MaeksterTheme { HomeLayoutDialog(HomeLayout(), { saved = it }, {}) } }
        rule.onNodeWithContentDescription("Your playlists").performClick()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_sections.png")
        rule.onNodeWithText("Move up").assertDoesNotExist()
        rule.onNodeWithText("Move down").assertDoesNotExist()
        val handle = rule.onNodeWithContentDescription("Reorder Your playlists")
        val target = rule.onNodeWithContentDescription("Reorder Favorite albums")
        val distance =
            target
                .fetchSemanticsNode()
                .boundsInRoot.center.y -
                handle
                    .fetchSemanticsNode()
                    .boundsInRoot.center.y
        handle.performTouchInput { swipe(center, center + Offset(0f, distance), durationMillis = 800) }
        rule.waitForIdle()
        rule.onNodeWithText("Save").performClick()
        assertEquals(HomeShelf.FavoriteAlbums, saved!!.order[1])
        assertTrue(HomeShelf.YourPlaylists in saved.hidden)
    }

    @Test
    @Suppress("DEPRECATION")
    fun editorResetAndLargeFont() {
        var saved: HomeLayout? = null
        val resources = rule.activity.resources
        val configuration =
            android.content.res
                .Configuration(resources.configuration)
                .apply { fontScale = 2f }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        rule.setContent {
            MaeksterTheme { HomeLayoutDialog(HomeLayout(hidden = HomeShelf.entries.toSet()), { saved = it }, {}) }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/home_sections_2x.png")
        rule.onNodeWithText("Reset to default").performScrollTo().performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(HomeLayout(), saved)
    }
}
