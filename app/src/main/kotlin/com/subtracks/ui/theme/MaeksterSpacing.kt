package com.subtracks.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The 4dp spacing grid, mirroring the iOS `MaeksterSpacing`. Use these instead of literal `dp` values. */
object MaeksterSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val xxxxl = 48.dp

    /** The only gap between page sections (shelves, track lists, discography groups). */
    val section = 32.dp

    /** Right inset for track rows that end in a duration. */
    val trackRowTrailing = 32.dp
}

/**
 * Diameters of circular controls. Larger than the iOS 36/44/52 so each stays a comfortable Android touch
 * target (48dp minimum): inline accessories, secondary and toolbar buttons, and action-row buttons.
 */
object MaeksterControlSize {
    val compact = 40.dp
    val regular = 48.dp
    val large = 56.dp
}

/** Corner radii mirroring the iOS `MaeksterCornerRadius`. */
object MaeksterShapes {
    val xs = RoundedCornerShape(4.dp)
    val s = RoundedCornerShape(6.dp)
    val standard = RoundedCornerShape(8.dp)
    val large = RoundedCornerShape(12.dp)
    val hero = RoundedCornerShape(20.dp)
    val pill = CircleShape

    /** Material 3 slots: text fields 4, chips and small cards 8, cards and sheets 12. Dialogs keep Material's 28. */
    val material =
        Shapes(
            extraSmall = xs,
            small = standard,
            medium = large,
        )
}
