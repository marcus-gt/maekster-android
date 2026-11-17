package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeConsolidationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val album = Album(1, "a", "ar", "A favorite record", "Artist", 0, null, null, 2026, 1, 1)
    private val artist = Artist(1, "ar", "A new artist to rediscover", 1, null)

    @Test fun sharedBuilderAndArtistPicksRenderThroughTheirOwnHomeSections() {
        rule.setContent {
            MaeksterTheme {
                Surface {
                    HomeScreen(
                        HomeFeed(favoriteAlbums = listOf(album)),
                        { _, _ -> null },
                        null,
                        artistPicks = listOf(artist),
                        queueBuilderContent = { Text("Shared builder") },
                        layout =
                            HomeLayout(
                                order =
                                    listOf(HomeShelf.QueueBuilder, HomeShelf.ArtistPicks) +
                                        HomeLayout().order.filter { it != HomeShelf.QueueBuilder && it != HomeShelf.ArtistPicks },
                            ),
                    )
                }
            }
        }
        rule.onNodeWithText("Shared builder").assertExists()
        rule.onNodeWithText("Artist picks").assertExists()
        rule.onNodeWithText(artist.name).assertExists()
    }

    @Test fun hiddenHomeBuilderDoesNotRemoveOtherHomeSections() {
        rule.setContent {
            MaeksterTheme {
                HomeScreen(
                    HomeFeed(favoriteAlbums = listOf(album)),
                    { _, _ -> null },
                    null,
                    queueBuilderContent = { Text("Shared builder") },
                    layout = HomeLayout().show(HomeShelf.QueueBuilder, false),
                )
            }
        }
        rule.onNodeWithText("Shared builder").assertDoesNotExist()
        rule.onNodeWithText("Your favorite albums").assertExists()
    }

    @Test fun normalLight() = render(1f, false)

    @Test fun largeDark() = render(2f, true)

    private fun render(
        scale: Float,
        dark: Boolean,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme(themeMode = if (dark) com.subtracks.data.prefs.ThemeMode.Dark else com.subtracks.data.prefs.ThemeMode.Light) {
                    Surface {
                        HomeScreen(
                            HomeFeed(
                                favoriteAlbums = listOf(album),
                                recentlyPlayedAlbums = listOf(album.copy(id = "recent")),
                                recentlyAddedAlbums = listOf(album.copy(id = "added")),
                                genres = listOf("Jazz", "Electronic", "Rock"),
                            ),
                            { _, _ -> null },
                            null,
                            artistPicks = listOf(artist),
                            queueBuilderContent = {
                                com.subtracks.ui.discover.QueueBuilderSection(
                                    com.subtracks.ui.discover
                                        .QueueBuilderState(loading = false),
                                    null,
                                    false,
                                    com.subtracks.ui.discover
                                        .QueueBuilderActions(),
                                )
                            },
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/home_consolidated_${scale}_$dark.png")
        rule.onNodeWithTag("homeList").performScrollToNode(hasText("Artist picks"))
        rule.onRoot().captureRoboImage("src/test/screenshots/home_consolidated_picks_${scale}_$dark.png")
    }
}
