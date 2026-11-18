package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFavoriteAlbumsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun favoritesAtLargeFont() = checkFavorites(2f)

    @Test
    fun favoritesOnlyFeedShowsShelfAndOpensAlbumAndMore() = checkFavorites(1f)

    private fun checkFavorites(fontScale: Float) {
        val album =
            Album(
                sourceId = 1,
                id = "a",
                artistId = null,
                name = "Favorite record",
                albumArtist = "Artist",
                created = 0,
                coverArt = null,
                genre = null,
                year = null,
                starred = 100,
                songCount = 1,
            )
        var opened: Album? = null
        var more: HomeSection? = null
        val feed =
            buildHomeFeed(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                false,
                favoriteAlbums = listOf(album),
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaeksterTheme {
                    Surface {
                        HomeScreen(
                            feed = feed,
                            coverArt = { _, _ -> null },
                            playingSongId = null,
                            onAlbumClick = { opened = it },
                            onMore = { more = it },
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home_favorites_$fontScale.png")
        composeRule.onNodeWithText("Your favorite albums").assertExists()
        composeRule.onNodeWithText("Sync").assertDoesNotExist()
        composeRule.onNodeWithText("Favorite record").performClick()
        composeRule.onNodeWithText("Your favorite albums").performClick()
        assertEquals(album, opened)
        assertEquals(HomeSection.FavoriteAlbums, more)
    }
}
