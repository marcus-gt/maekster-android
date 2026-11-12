package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.db.LetterBucket
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LetterRailTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lettersRequestAbsolutePositions() = renderAndClick(1f)

    @Test
    fun lettersAtLargeFont() = renderAndClick(2f)

    @Test
    fun theLastLettersScrollClearOfTheBottomPadding() {
        val letters = ('A'..'Z').mapIndexed { index, letter -> LetterBucket(letter.toString(), index) }
        composeRule.setContent {
            MaeksterTheme {
                LetterIndexedContent(letters, {}, Modifier.height(300.dp), railBottomPadding = 200.dp) { Box(it) }
            }
        }
        repeat(5) { composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }

        // Scrolled to the end, the last letter rests above the padding instead of at the bottom edge.
        val bottom = composeRule.onNodeWithTag("letter-rail-Z").getUnclippedBoundsInRoot().bottom
        assertTrue("Z ends at $bottom", bottom <= 101.dp)
    }

    private fun renderAndClick(fontScale: Float) {
        val jumps = mutableListOf<Int>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaeksterTheme {
                    LetterIndexedContent(
                        listOf(LetterBucket("#", 0), LetterBucket("A", 400), LetterBucket("B", 900)),
                        jumps::add,
                        Modifier.fillMaxSize(),
                    ) { Box(it) }
                }
            }
        }
        composeRule.onNodeWithText("B").performClick()
        composeRule.onNodeWithText("A").performClick()
        assertEquals(listOf(900, 400), jumps)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/letter_rail_$fontScale.png")
    }
}
