package com.subtracks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw brand and accent values, taken from the iOS app's `MaeksterColors.md` and `Branding/README.md`.
 * Screens never use these directly: they read `MaterialTheme.colorScheme` or [MaeksterTheme.colors].
 */
object MaeksterPalette {
    val Ink = Color(0xFF0E0E0E)
    val Paper = Color(0xFFFAF8F5)
    val TextOnLight = Color(0xFF141414)

    val AccentLight = Color(0xFFD96B1F)
    val AccentDark = Color(0xFFFF8C4B)
    val AccentSecondaryLight = Color(0xFFF5A06B)
    val AccentSecondaryDark = Color(0xFFFFB980)

    val LosslessLight = Color(0xFF1E8A3C)
    val LosslessDark = Color(0xFF68D97D)
}

/**
 * Colours that Material 3's [ColorScheme] has no slot for. Read them with [MaeksterTheme.colors].
 *
 * @property accentSecondary Gradient stop beside the accent and tinted backgrounds. Decorative, never text.
 * @property lossless Text and tint of the lossless format badge (FLAC, ALAC). Nothing else uses it.
 * @property coverBorder One-pixel outline around cover art in dark surfaces, where a shadow is invisible.
 * @property coverShadow Shadow under cover art in light surfaces. Transparent in dark.
 */
@Immutable
data class MaeksterColors(
    val accentSecondary: Color,
    val lossless: Color,
    val coverBorder: Color,
    val coverShadow: Color,
)

internal val LightMaeksterColors =
    MaeksterColors(
        accentSecondary = MaeksterPalette.AccentSecondaryLight,
        lossless = MaeksterPalette.LosslessLight,
        coverBorder = Color.Transparent,
        coverShadow = Color.Black.copy(alpha = 0.15f),
    )

internal val DarkMaeksterColors =
    MaeksterColors(
        accentSecondary = MaeksterPalette.AccentSecondaryDark,
        lossless = MaeksterPalette.LosslessDark,
        coverBorder = Color.White.copy(alpha = 0.08f),
        coverShadow = Color.Transparent,
    )

internal val LocalMaeksterColors = staticCompositionLocalOf { DarkMaeksterColors }

/**
 * The accent is for primary interactive elements only (play, progress, active toggles, tappable artist
 * names). Everything else is neutral: Paper and Ink surfaces with warm greys, so the orange stays rare.
 *
 * Contrast: on-surface text is above 15:1 in both schemes. Dark `onPrimary` is Ink on the light-orange
 * accent (about 8.5:1). Light `onPrimary` is white on the deeper accent (about 3.5:1, large text and
 * controls only), which matches the iOS accent rules and is revisited in the accessibility pass (M4.9).
 */
val MaeksterLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = MaeksterPalette.AccentLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDCC8),
        onPrimaryContainer = Color(0xFF4A1E00),
        inversePrimary = MaeksterPalette.AccentDark,
        secondary = Color(0xFF6E625A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE3DC),
        onSecondaryContainer = Color(0xFF2B211A),
        tertiary = Color(0xFF8A4A12),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDDBC),
        onTertiaryContainer = Color(0xFF2E1500),
        background = MaeksterPalette.Paper,
        onBackground = MaeksterPalette.TextOnLight,
        surface = MaeksterPalette.Paper,
        onSurface = MaeksterPalette.TextOnLight,
        surfaceVariant = Color(0xFFEAE4DE),
        onSurfaceVariant = Color(0xFF4F4741),
        surfaceTint = MaeksterPalette.AccentLight,
        inverseSurface = Color(0xFF2B2926),
        inverseOnSurface = MaeksterPalette.Paper,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF7F766F),
        outlineVariant = Color(0xFFD3CBC4),
        scrim = Color.Black,
        surfaceBright = MaeksterPalette.Paper,
        surfaceDim = Color(0xFFDDD7D1),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F2EE),
        surfaceContainer = Color(0xFFF0ECE7),
        surfaceContainerHigh = Color(0xFFEAE5DF),
        surfaceContainerHighest = Color(0xFFE4DED8),
    )

val MaeksterDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = MaeksterPalette.AccentDark,
        onPrimary = MaeksterPalette.Ink,
        primaryContainer = Color(0xFF5A2A0C),
        onPrimaryContainer = Color(0xFFFFDCC8),
        inversePrimary = MaeksterPalette.AccentLight,
        secondary = Color(0xFFCDC5BE),
        onSecondary = Color(0xFF1F1B18),
        secondaryContainer = Color(0xFF3B3530),
        onSecondaryContainer = Color(0xFFEBE1DA),
        tertiary = MaeksterPalette.AccentSecondaryDark,
        onTertiary = Color(0xFF3A2000),
        tertiaryContainer = Color(0xFF5A3A12),
        onTertiaryContainer = Color(0xFFFFDDBC),
        background = MaeksterPalette.Ink,
        onBackground = MaeksterPalette.Paper,
        surface = MaeksterPalette.Ink,
        onSurface = MaeksterPalette.Paper,
        surfaceVariant = Color(0xFF2B2926),
        onSurfaceVariant = Color(0xFFC4BDB6),
        surfaceTint = MaeksterPalette.AccentDark,
        inverseSurface = MaeksterPalette.Paper,
        inverseOnSurface = MaeksterPalette.TextOnLight,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8F8882),
        outlineVariant = Color(0xFF3F3B37),
        scrim = Color.Black,
        surfaceBright = Color(0xFF2F2D2A),
        surfaceDim = MaeksterPalette.Ink,
        surfaceContainerLowest = Color(0xFF090909),
        surfaceContainerLow = Color(0xFF151413),
        surfaceContainer = Color(0xFF191817),
        surfaceContainerHigh = Color(0xFF211F1E),
        surfaceContainerHighest = Color(0xFF2A2826),
    )
