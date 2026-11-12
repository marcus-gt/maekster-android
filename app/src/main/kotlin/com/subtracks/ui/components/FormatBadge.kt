package com.subtracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.ui.theme.MaeksterShapes
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.MaeksterTheme

/** Format facts shared by the badge and, later, the player's format line. */
object AudioFormats {
    private val lossless = setOf("flac", "alac", "wav", "wave", "aif", "aiff", "ape", "wv", "dsf", "dff", "tta", "shn")

    fun isLossless(format: String): Boolean = format.trim().lowercase() in lossless

    /** One badge per distinct file suffix, in track order; a mixed-format album shows at most [max]. */
    fun badges(
        suffixes: List<String?>,
        max: Int = 2,
    ): List<String> =
        suffixes
            .mapNotNull { it?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(max)
}

/** A short all-caps format label (FLAC, MP3). Lossless formats use the lossless colour. */
@Composable
fun FormatBadge(
    format: String,
    modifier: Modifier = Modifier,
) {
    val label = format.uppercase()
    val lossless = AudioFormats.isLossless(format)
    val color = if (lossless) MaeksterTheme.colors.lossless else MaterialTheme.colorScheme.onSurfaceVariant
    val description = if (lossless) stringResource(R.string.format_badge_lossless, label) else label
    Text(
        text = label,
        style = MaeksterTheme.text.badge,
        color = color,
        modifier =
            modifier
                .semantics { contentDescription = description }
                .background(
                    if (lossless) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    MaeksterShapes.pill,
                ).padding(horizontal = MaeksterSpacing.s - 2.dp, vertical = 2.dp),
    )
}
