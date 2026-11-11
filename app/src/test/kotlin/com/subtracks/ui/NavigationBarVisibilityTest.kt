package com.subtracks.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBarVisibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val threshold = 10f

    @Test
    fun scrollingTowardsTheEndHidesTheBarOncePastTheThreshold() {
        val bar = NavigationBarVisibility(threshold)

        bar.onScroll(consumedY = -6f, unconsumedY = 0f)
        assertTrue(bar.visible)

        bar.onScroll(consumedY = -6f, unconsumedY = 0f)
        assertFalse(bar.visible)
    }

    @Test
    fun scrollingBackShowsTheBarAgain() {
        val bar = NavigationBarVisibility(threshold)
        bar.onScroll(consumedY = -20f, unconsumedY = 0f)

        bar.onScroll(consumedY = 12f, unconsumedY = 0f)

        assertTrue(bar.visible)
    }

    @Test
    fun aSmallMoveTheOtherWayStartsTheCountAgain() {
        val bar = NavigationBarVisibility(threshold)

        bar.onScroll(consumedY = -8f, unconsumedY = 0f)
        bar.onScroll(consumedY = 2f, unconsumedY = 0f)
        bar.onScroll(consumedY = -8f, unconsumedY = 0f)

        assertTrue(bar.visible)
    }

    @Test
    fun draggingBackOnContentThatCannotScrollStillShowsTheBar() {
        val bar = NavigationBarVisibility(threshold)
        bar.onScroll(consumedY = -20f, unconsumedY = 0f)

        bar.onScroll(consumedY = 0f, unconsumedY = 15f)

        assertTrue(bar.visible)
    }

    @Test
    fun draggingTowardsTheEndOfContentThatCannotScrollKeepsTheBar() {
        val bar = NavigationBarVisibility(threshold)

        bar.onScroll(consumedY = 0f, unconsumedY = -50f)

        assertTrue(bar.visible)
    }

    @Test
    fun showBringsTheBarBackAndForgetsPartialScrolling() {
        val bar = NavigationBarVisibility(threshold)
        bar.onScroll(consumedY = -20f, unconsumedY = 0f)
        bar.onScroll(consumedY = -8f, unconsumedY = 0f)

        bar.show()
        bar.onScroll(consumedY = -8f, unconsumedY = 0f)

        assertTrue(bar.visible)
    }

    @Test
    fun theBarGoesWhileTypingAndNeverHidesForScreenReaders() {
        assertTrue(navigationBarShown(scrolledAway = false, keyboardUp = false, screenReader = false))
        assertFalse(navigationBarShown(scrolledAway = true, keyboardUp = false, screenReader = false))
        assertTrue(navigationBarShown(scrolledAway = true, keyboardUp = false, screenReader = true))
        assertFalse(navigationBarShown(scrolledAway = false, keyboardUp = true, screenReader = false))
        assertFalse(navigationBarShown(scrolledAway = false, keyboardUp = true, screenReader = true))
    }

    @Test
    fun scrollingAListHidesTheBarAndScrollingBackShowsIt() {
        val bar = NavigationBarVisibility(threshold)
        composeRule.setContent {
            MaeksterTheme {
                Column {
                    LazyColumn(Modifier.weight(1f).nestedScroll(bar.nestedScrollConnection).testTag("list")) {
                        items(100) { Text("Row $it", Modifier.fillMaxWidth().height(48.dp)) }
                    }
                    if (bar.visible) MaeksterNavigationBar(current = TopLevelTab.Home, onSelect = {})
                }
            }
        }
        val library = composeRule.activity.getString(R.string.navigation_tabs_library)
        composeRule.onNodeWithText(library).assertExists()

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.onNodeWithText(library).assertDoesNotExist()

        composeRule.onNodeWithTag("list").performTouchInput { swipeDown() }
        composeRule.onNodeWithText(library).assertExists()
    }

    @Test
    fun revealHeightTakesUpOnlyItsShareOfTheContent() {
        composeRule.setContent {
            Column {
                Box(Modifier.testTag("half").revealHeight { 0.5f }) { Box(Modifier.height(100.dp).fillMaxWidth()) }
                Box(Modifier.testTag("spacer").layoutHeight { 40 })
            }
        }

        val half = composeRule.onNodeWithTag("half").getUnclippedBoundsInRoot()
        assertEquals(50f, (half.bottom - half.top).value, 0.5f)
        val spacer = composeRule.onNodeWithTag("spacer").fetchSemanticsNode()
        assertEquals(40, spacer.size.height)
    }
}
