package com.subtracks.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.subtracks.data.prefs.ThemeMode

internal val LocalMaeksterDarkTheme = staticCompositionLocalOf { true }

internal val LocalMaeksterDarkColorScheme = staticCompositionLocalOf { MaeksterDarkColorScheme }

/** Whether this mode renders dark, given whether the device is in dark mode. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean =
    when (this) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

/** Material You needs Android 12 (API 31). Earlier versions always use the maekster palette. */
val materialYouSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The colour scheme for a mode: the maekster palette, or the wallpaper colours when Material You is on. */
fun maeksterColorScheme(
    dark: Boolean,
    materialYou: Boolean,
    context: Context,
): ColorScheme {
    if (materialYou && materialYouSupported) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (dark) MaeksterDarkColorScheme else MaeksterLightColorScheme
}

/**
 * The app theme: maekster colours, spacing-aligned shapes and the system-font type scale.
 *
 * The app passes the stored preferences. The defaults (dark, no Material You) are what previews and tests
 * render. Artwork-led screens wrap their content in [ArtworkTheme], which is always dark.
 */
@Composable
fun MaeksterTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    materialYou: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val colorScheme = remember(dark, materialYou, context) { maeksterColorScheme(dark, materialYou, context) }
    val darkColorScheme = remember(materialYou, context) { maeksterColorScheme(true, materialYou, context) }
    CompositionLocalProvider(
        LocalMaeksterColors provides if (dark) DarkMaeksterColors else LightMaeksterColors,
        LocalMaeksterDarkTheme provides dark,
        LocalMaeksterDarkColorScheme provides darkColorScheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaeksterTypography,
            shapes = MaeksterShapes.material,
            content = content,
        )
    }
}

/** Accessors for the tokens Material 3 has no slot for. Colours, shapes and type still come from `MaterialTheme`. */
object MaeksterTheme {
    val colors: MaeksterColors
        @Composable @ReadOnlyComposable
        get() = LocalMaeksterColors.current

    val text: MaeksterTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalMaeksterTextStyles.current

    /** True when the current surface is dark: the dark theme, or any artwork-led screen. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalMaeksterDarkTheme.current

    /** The dark scheme for the active palette (Material You aware). Artwork surfaces fall back to it. */
    val darkColorScheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalMaeksterDarkColorScheme.current
}
