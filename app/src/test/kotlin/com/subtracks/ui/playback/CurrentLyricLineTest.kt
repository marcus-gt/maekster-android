package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrentLyricLineTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun layout(line: String): Pair<TextLayoutResult, TextUnit> {
        var bodySize = TextUnit.Unspecified
        var shown: TextLayoutResult? = null
        rule.setContent {
            MaeksterTheme {
                bodySize = MaterialTheme.typography.bodyLarge.fontSize
                Box(Modifier.width(300.dp)) { CurrentLyricLine(line, onClick = {}, onTextLayout = { shown = it }) }
            }
        }
        rule.onNodeWithTag(LYRICS_PREVIEW_TAG).assertExists()
        return shown!! to bodySize
    }

    // UX-27 (#57): as on iOS, a long line shrinks a little rather than losing its ending.
    @Test fun aLongLineShrinksToFitInsteadOfBeingCutOff() {
        val (result, bodySize) = layout("And admit that the waters around you have grown")
        val size = result.layoutInput.style.fontSize.value
        val state = "size $size of ${bodySize.value}, line ${result.getLineLeft(0)}..${result.getLineRight(0)} in ${result.size.width}"
        assertFalse(state, result.isLineEllipsized(0))
        assertTrue(state, result.getLineRight(0) - result.getLineLeft(0) <= result.size.width)
        assertTrue(state, size < bodySize.value)
        assertTrue(state, size >= bodySize.value * 0.7f - 0.01f)
    }

    @Test fun aShortLineKeepsItsSize() {
        val (result, bodySize) = layout("Come gather 'round people")
        assertEquals(bodySize.value, result.layoutInput.style.fontSize.value, 0.01f)
    }
}
