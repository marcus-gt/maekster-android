package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.settings.HomeLayoutDialog
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
class PinnedGridTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun gridAtNormalFont() = render(1f)

    @Test fun gridAtLargeFont() = render(2f)

    private fun render(scale: Float) {
        val pins = (1..3).map { PinnedItem(1, PinKind.entries[(it - 1) % 3], "$it", "Pin $it", null, null) }
        val flow = MutableStateFlow(PagingData.from(pins))
        var opened: PinnedItem? = null
        var held: PinnedItem? = null
        rule.setContent {
            val paging = flow.collectAsLazyPagingItems()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        HomeScreen(
                            HomeFeed(),
                            { _, _ -> null },
                            null,
                            pins = paging,
                            layout = HomeLayout(grids = setOf(HomeShelf.Pinned)),
                            onPinnedClick = { opened = it },
                            onPinnedLongClick = { held = it },
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Pin 3").performScrollTo()
        val first = rule.onNodeWithText("Pin 1").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithText("Pin 2").fetchSemanticsNode().boundsInRoot
        val third = rule.onNodeWithText("Pin 3").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(third.top > second.bottom)
        rule.onNodeWithText("Pin 3").performClick()
        rule.onNodeWithText("Pin 3").performTouchInput { longClick() }
        assertEquals(pins[2], opened)
        assertEquals(pins[2], held)
        rule.onRoot().captureRoboImage("src/test/screenshots/pins_grid_$scale.png")
    }

    @Test
    fun editorSavesPinnedGridAndResetsToCarousel() {
        var saved = HomeLayout()
        rule.setContent { MaeksterTheme { HomeLayoutDialog(HomeLayout(), { saved = it }, {}) } }
        rule.onNodeWithContentDescription("Pinned: Layout: Carousel").performClick()
        rule.onNodeWithContentDescription("Pinned: Layout: Grid").assertExists()
        rule.onNodeWithText("Save").performClick()
        assertTrue(HomeShelf.Pinned in saved.grids)
        rule.onNodeWithText("Reset to default").performClick()
        rule.onNodeWithText("Save").performClick()
        assertTrue(saved.grids.isEmpty())
    }

    @Test
    fun largeGridComposesOnlyNearbyRows() {
        val flow =
            MutableStateFlow(
                PagingData.from(
                    (1..100).map {
                        PinnedItem(1, PinKind.Album, "$it", "Pin $it", null, "$it")
                    },
                ),
            )
        val rendered = mutableSetOf<String>()
        rule.setContent {
            val paging = flow.collectAsLazyPagingItems()
            MaeksterTheme {
                Surface {
                    HomeScreen(
                        HomeFeed(),
                        { id, _ ->
                            if (id != null) rendered += id
                            null
                        },
                        null,
                        pins = paging,
                        layout = HomeLayout(grids = setOf(HomeShelf.Pinned)),
                    )
                }
            }
        }
        rule.onNodeWithText("Pin 1").assertExists()
        rule.runOnIdle { assertTrue("Only visible/prefetched cards should render: ${rendered.size}", rendered.size in 1..40) }
    }

    @Test
    fun columnsFitAvailableWidth() {
        assertEquals(1, pinGridColumns(240.dp))
        assertEquals(2, pinGridColumns(320.dp))
        assertEquals(5, pinGridColumns(800.dp))
    }
}
