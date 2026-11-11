package com.subtracks.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SCROLL_THRESHOLD = 16.dp

/**
 * Whether the navigation bar is showing. Scrolling a screen towards its end hides the bar, and scrolling back
 * shows it again, so long lists get the bar's height while you browse.
 *
 * Only scrolling that actually moved the content hides the bar. Any drag back shows it, even on content that
 * cannot scroll, so the bar never gets stuck hidden on a screen too short to scroll.
 */
@Stable
internal class NavigationBarVisibility(
    private val thresholdPx: Float,
) {
    var visible by mutableStateOf(true)
        private set

    /** Distance moved in the current direction since the bar last changed or the direction flipped. */
    private var travel = 0f

    /**
     * One scroll step, in Compose's sign convention, where negative values move the content towards its end.
     * [consumedY] is how far the content scrolled; [unconsumedY] is what was left over at an edge.
     */
    fun onScroll(
        consumedY: Float,
        unconsumedY: Float,
    ) {
        val delta = consumedY + unconsumedY.coerceAtLeast(0f)
        if (delta == 0f) return
        if (travel != 0f && (travel > 0f) != (delta > 0f)) travel = 0f
        travel += delta
        if (travel <= -thresholdPx) {
            visible = false
            travel = 0f
        } else if (travel >= thresholdPx) {
            show()
        }
    }

    fun show() {
        visible = true
        travel = 0f
    }

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                onScroll(consumed.y, available.y)
                return Offset.Zero
            }
        }
}

@Composable
internal fun rememberNavigationBarVisibility(): NavigationBarVisibility {
    val thresholdPx = with(LocalDensity.current) { SCROLL_THRESHOLD.toPx() }
    return remember(thresholdPx) { NavigationBarVisibility(thresholdPx) }
}

/**
 * Whether the navigation bar should be on screen. It goes while typing, so the keyboard sits right under the
 * field, and it never hides on scroll for screen reader users, who find controls by touch.
 */
internal fun navigationBarShown(
    scrolledAway: Boolean,
    keyboardUp: Boolean,
    screenReader: Boolean,
): Boolean = !keyboardUp && (!scrolledAway || screenReader)

/** Whether a screen reader is exploring the screen by touch (TalkBack), following changes while composed. */
@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * Measures the content at its full height but takes up only [fraction] of it, clipping the rest at the bottom,
 * so the content seems to slide down out of view. [fraction] is read during layout, so animating it does not
 * recompose.
 */
internal fun Modifier.revealHeight(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, height) { placeable.place(0, 0) }
    }

/** An empty space [heightPx] tall, read during layout so animating it does not recompose. */
internal fun Modifier.layoutHeight(heightPx: () -> Int): Modifier =
    layout { _, constraints ->
        layout(constraints.maxWidth, heightPx().coerceAtLeast(0)) {}
    }
