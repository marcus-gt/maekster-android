package com.subtracks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The Material 3 scale on the system font (decision D4). Hierarchy comes from size and weight. */
val MaeksterTypography =
    Typography().let { base ->
        base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.W800),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.W700),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.W600),
        )
    }

/**
 * Named text roles that mirror the iOS typography table, so a screen asks for "the track title" rather
 * than a size. Read them with [MaeksterTheme.text]. All sizes are `sp`, so they follow the system font scale.
 */
@Immutable
class MaeksterTextStyles(
    /** Artist name over the hero image. */
    val heroTitle: TextStyle,
    /** Track title in Now Playing. */
    val playerTitle: TextStyle,
    /** Artist line under the player title. */
    val playerSubtitle: TextStyle,
    /** Album line under the player artist. */
    val playerTertiary: TextStyle,
    /** Album or playlist name in a detail header. */
    val detailTitle: TextStyle,
    /** Artist or owner under a detail title. */
    val detailSubtitle: TextStyle,
    /** Section and disc headers. */
    val sectionTitle: TextStyle,
    /** Carousel and shelf headers. */
    val shelfTitle: TextStyle,
    val body: TextStyle,
    /** Primary text in list cells. */
    val cellTitle: TextStyle,
    /** Secondary text in list cells. */
    val cellSubtitle: TextStyle,
    /** Track titles in album and playlist lists. */
    val trackTitle: TextStyle,
    /** Track numbers and durations, with tabular digits so columns line up. */
    val trackNumber: TextStyle,
    /** Release metadata line, format line and chips, with tabular digits. */
    val metadata: TextStyle,
    /** Format badges (FLAC, MP3) and other short all-caps labels. */
    val badge: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
    val lyricsLine: TextStyle,
)

private const val TABULAR_DIGITS = "tnum"

internal fun maeksterTextStyles(base: Typography = MaeksterTypography): MaeksterTextStyles =
    MaeksterTextStyles(
        heroTitle = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        playerTitle = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        playerSubtitle = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        playerTertiary = base.bodyLarge,
        detailTitle = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        detailSubtitle = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
        sectionTitle = base.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        shelfTitle = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        body = base.bodyLarge,
        cellTitle = base.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        cellSubtitle = base.bodyMedium,
        trackTitle = base.bodyLarge,
        trackNumber = base.bodyMedium.copy(fontFeatureSettings = TABULAR_DIGITS),
        metadata = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_DIGITS),
        badge = base.labelSmall.copy(fontWeight = FontWeight.Bold),
        caption = base.bodySmall,
        caption2 = base.labelSmall,
        lyricsLine = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    )

internal val LocalMaeksterTextStyles = staticCompositionLocalOf { maeksterTextStyles() }
