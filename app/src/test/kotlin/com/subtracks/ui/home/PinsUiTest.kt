package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinRef
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.Playlist
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.ItemContextMenu
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.pinRef
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pinsOnlyHome() = renderPins(1f)

    @Test fun pinsAtLargeFont() = renderPins(2f)

    private fun renderPins(scale: Float) {
        val item = PinnedItem(2, PinKind.Album, "a", "Pinned album", "Artist", null)
        val items =
            kotlinx.coroutines.flow.MutableStateFlow(
                PagingData.from(listOf(item, item.copy(kind = PinKind.Artist, name = "Pinned artist"))),
            )
        var clicked: PinnedItem? = null
        var held: PinnedItem? = null
        rule.setContent {
            val paging = items.collectAsLazyPagingItems()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        HomeScreen(
                            HomeFeed(),
                            { _, _ -> null },
                            null,
                            pins = paging,
                            onPinnedClick = { clicked = it },
                            onPinnedLongClick = { held = it },
                        )
                    }
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText("Pinned").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Pinned").assertExists()
        rule.onNodeWithText("Sync").assertDoesNotExist()
        rule.onNodeWithText("Pinned album").performClick()
        rule.onNodeWithText("Pinned album").performTouchInput { longClick() }
        assertEquals(item, clicked)
        assertEquals(item, held)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_pins_$scale.png")
    }

    @Test
    fun pinActionUsesTypedAlbumAndArtistReferences() {
        val album =
            com.subtracks.data.model
                .Album(4, "a", null, "Album", null, 0, null, null, null, null, 1)
        val artist =
            com.subtracks.data.model
                .Artist(5, "a", "Artist", 1, null, null)
        assertEquals(PinRef(4, PinKind.Album, "a"), MenuTarget.Album(album).pinRef())
        assertEquals(PinRef(5, PinKind.Artist, "a"), MenuTarget.Artist(artist).pinRef())
        var requested: Boolean? = null
        rule.setContent {
            MaeksterTheme {
                ItemContextMenu(MenuTarget.Album(album), ItemActions(), {}, pinned = false, onSetPinned = { requested = it })
            }
        }
        rule.onNodeWithText("Pin to Home").performClick()
        assertEquals(true, requested)
    }

    @Test
    fun playlistMenuPinsOriginalServerAndShowsUnpinForPinnedItems() {
        val target = MenuTarget.Playlist(Playlist(7, "id", "Playlist", null, null, 1, 0))
        assertEquals(PinRef(7, PinKind.Playlist, "id"), target.pinRef())
        var requested: Boolean? = null
        rule.setContent {
            MaeksterTheme {
                ItemContextMenu(target, ItemActions(), {}, pinned = true, onSetPinned = { requested = it })
            }
        }
        rule.onNodeWithText("Unpin from Home").performClick()
        assertEquals(false, requested)
    }
}
