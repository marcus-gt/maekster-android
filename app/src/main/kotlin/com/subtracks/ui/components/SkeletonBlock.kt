package com.subtracks.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.subtracks.ui.theme.MaeksterShapes

private const val SKELETON_REST_ALPHA = 0.05f
private const val SKELETON_PULSE_ALPHA = 0.10f
private const val SKELETON_PULSE_MILLIS = 1_200

/**
 * A grey block that stands in for content that has not loaded yet. It pulses between 5% and 10% of the
 * text colour, like the iOS `SkeletonBlock`. Size it with [modifier].
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaeksterShapes.s,
) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = SKELETON_REST_ALPHA,
        targetValue = SKELETON_PULSE_ALPHA,
        animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton alpha",
    )
    val color = MaterialTheme.colorScheme.onSurface
    Box(modifier.clip(shape).background(color.copy(alpha = alpha)))
}
