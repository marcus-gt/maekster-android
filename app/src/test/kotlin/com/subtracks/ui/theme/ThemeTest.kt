package com.subtracks.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private data class Resolved(
        val scheme: ColorScheme,
        val colors: MaeksterColors,
        val dark: Boolean,
    )

    private fun resolve(
        mode: ThemeMode,
        materialYou: Boolean = false,
    ): Resolved {
        var resolved: Resolved? = null
        composeRule.setContent {
            MaeksterTheme(themeMode = mode, materialYou = materialYou) {
                resolved = Resolved(MaterialTheme.colorScheme, MaeksterTheme.colors, MaeksterTheme.isDark)
            }
        }
        composeRule.waitForIdle()
        return requireNotNull(resolved)
    }

    @Test
    fun defaultSchemeIsNotMonochrome() {
        var scheme: ColorScheme? = null
        composeRule.setContent {
            MaeksterTheme {
                scheme = MaterialTheme.colorScheme
            }
        }
        val resolved = requireNotNull(scheme)
        assertNotEquals(Color.White, resolved.primary)
        assertNotEquals(Color.Black, resolved.background)
    }

    @Test
    fun darkModeUsesInkAndTheDarkAccent() {
        val theme = resolve(ThemeMode.Dark)

        assertTrue(theme.dark)
        assertEquals(MaeksterPalette.Ink, theme.scheme.background)
        assertEquals(MaeksterPalette.AccentDark, theme.scheme.primary)
        assertEquals(DarkMaeksterColors, theme.colors)
    }

    @Test
    fun lightModeUsesPaperAndTheLightAccent() {
        val theme = resolve(ThemeMode.Light)

        assertFalse(theme.dark)
        assertEquals(MaeksterPalette.Paper, theme.scheme.background)
        assertEquals(MaeksterPalette.AccentLight, theme.scheme.primary)
        assertEquals(LightMaeksterColors, theme.colors)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemModeIsDarkWhenTheDeviceIsInDarkMode() {
        val theme = resolve(ThemeMode.System)

        assertTrue(theme.dark)
        assertEquals(MaeksterPalette.Ink, theme.scheme.background)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun systemModeIsLightWhenTheDeviceIsInLightMode() {
        val theme = resolve(ThemeMode.System)

        assertFalse(theme.dark)
        assertEquals(MaeksterPalette.Paper, theme.scheme.background)
    }

    @Test
    @Config(qualifiers = "night")
    fun aForcedLightThemeIgnoresTheDeviceDarkMode() {
        val theme = resolve(ThemeMode.Light)

        assertFalse(theme.dark)
        assertEquals(MaeksterPalette.Paper, theme.scheme.background)
    }

    @Test
    fun materialYouReplacesTheBrandPaletteButNotTheFormatColour() {
        val theme = resolve(ThemeMode.Dark, materialYou = true)

        assertNotEquals(MaeksterPalette.AccentDark, theme.scheme.primary)
        assertEquals(MaeksterPalette.LosslessDark, theme.colors.lossless)
    }

    @Test
    fun artworkSurfacesStayDarkInTheLightTheme() {
        var scheme: ColorScheme? = null
        var colors: MaeksterColors? = null
        var dark: Boolean? = null
        composeRule.setContent {
            MaeksterTheme(themeMode = ThemeMode.Light) {
                ArtworkTheme(null) {
                    scheme = MaterialTheme.colorScheme
                    colors = MaeksterTheme.colors
                    dark = MaeksterTheme.isDark
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue("artwork surface is dark", requireNotNull(scheme).background.luminance() < 0.1f)
        assertEquals(DarkMaeksterColors, colors)
        assertEquals(true, dark)
    }

    @Test
    fun plainTextOnAnArtworkSurfaceIsWhiteInTheLightTheme() {
        var plainText: Color? = null
        var pageText: Color? = null
        composeRule.setContent {
            MaeksterTheme(themeMode = ThemeMode.Light) {
                pageText = LocalContentColor.current
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    ArtworkTheme(null) {
                        plainText = LocalContentColor.current
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(MaeksterDarkColorScheme.onBackground, requireNotNull(plainText))
        assertNotEquals(requireNotNull(pageText), requireNotNull(plainText))
    }

    @Test
    fun alwaysDarkScreensAskForWhiteStatusBarIconsOnlyWhileShown() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) WhiteStatusBarIcons()
        }
        composeRule.waitForIdle()
        assertTrue(StatusBarIcons.white)

        shown = false
        composeRule.waitForIdle()
        assertFalse(StatusBarIcons.white)
    }

    @Test
    fun twoAlwaysDarkScreensKeepTheIconsWhiteUntilBothLeave() {
        var first by mutableStateOf(true)
        var second by mutableStateOf(true)
        composeRule.setContent {
            if (first) WhiteStatusBarIcons()
            if (second) WhiteStatusBarIcons()
        }
        composeRule.waitForIdle()

        first = false
        composeRule.waitForIdle()
        assertTrue(StatusBarIcons.white)

        second = false
        composeRule.waitForIdle()
        assertFalse(StatusBarIcons.white)
    }
}
