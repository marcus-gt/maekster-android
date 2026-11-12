package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.Playlist
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflinePinsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val pins = (1..3).map { PinnedItem(1, PinKind.entries[(it - 1) % 3], "$it", "Pin $it", null, null) }
    private val album = Album(1, "a", null, "Offline album", "Artist", 0, null, null, null, null, 1)

    private fun show(
        layout: HomeLayout,
        scale: Float = 1f,
        onClick: (PinnedItem) -> Unit = {},
        onLongClick: (PinnedItem) -> Unit = {},
        waitFor: String = "Offline album",
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        OfflineHomeScreen(
                            albums = remember { MutableStateFlow(PagingData.from(listOf(album))) }.collectAsLazyPagingItems(),
                            artists = remember { MutableStateFlow(PagingData.empty<Artist>()) }.collectAsLazyPagingItems(),
                            playlists = remember { MutableStateFlow(PagingData.empty<Playlist>()) }.collectAsLazyPagingItems(),
                            songs = emptyList(),
                            coverArt = { _, _ -> null },
                            playingSongId = null,
                            layout = layout,
                            pins = remember { MutableStateFlow(PagingData.from(pins)) }.collectAsLazyPagingItems(),
                            onPinnedClick = onClick,
                            onPinnedLongClick = onLongClick,
                        )
                    }
                }
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(waitFor).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun pinnedLeadsOfflineHome() = carousel(1f)

    @Test fun pinnedLeadsOfflineHomeAtLargeFont() = carousel(2f)

    private fun carousel(scale: Float) {
        var opened: PinnedItem? = null
        var held: PinnedItem? = null
        show(HomeLayout(), scale, { opened = it }, { held = it })
        val pinned = rule.onNodeWithText("Pinned").fetchSemanticsNode().boundsInRoot
        val downloaded = rule.onNodeWithText("Downloaded albums").fetchSemanticsNode().boundsInRoot
        assertTrue(pinned.bottom <= downloaded.top)
        rule.onNodeWithText("Pin 1").performClick()
        rule.onNodeWithText("Pin 1").performTouchInput { longClick() }
        assertEquals(pins[0], opened)
        assertEquals(pins[0], held)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_offline_pins_$scale.png")
    }

    @Test
    fun offlinePinnedFollowsTheGridChoice() {
        show(HomeLayout(grids = setOf(HomeShelf.Pinned)), waitFor = "Pin 1")
        rule.onNodeWithText("Pin 3").performScrollTo()
        val second = rule.onNodeWithText("Pin 2").fetchSemanticsNode().boundsInRoot
        val third = rule.onNodeWithText("Pin 3").fetchSemanticsNode().boundsInRoot
        assertTrue(third.top > second.bottom)
    }

    @Test
    fun hiddenPinnedStaysHiddenOffline() {
        show(HomeLayout(hidden = setOf(HomeShelf.Pinned)))
        rule.onNodeWithText("Pinned").assertDoesNotExist()
        rule.onNodeWithText("Pin 1").assertDoesNotExist()
    }
}
