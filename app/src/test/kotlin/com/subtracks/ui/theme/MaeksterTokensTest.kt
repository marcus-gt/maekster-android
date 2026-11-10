package com.subtracks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.subtracks.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaeksterTokensTest {
    @Test
    fun theAccentAndSurfacesMatchTheIosTokens() {
        assertEquals(Color(0xFFD96B1F), MaeksterLightColorScheme.primary)
        assertEquals(Color(0xFFFF8C4B), MaeksterDarkColorScheme.primary)
        assertEquals(Color(0xFFFAF8F5), MaeksterLightColorScheme.background)
        assertEquals(Color(0xFF0E0E0E), MaeksterDarkColorScheme.background)
        assertEquals(Color(0xFFF5A06B), LightMaeksterColors.accentSecondary)
        assertEquals(Color(0xFFFFB980), DarkMaeksterColors.accentSecondary)
    }

    @Test
    fun bodyTextIsReadableOnEverySurface() {
        for ((name, scheme) in listOf("light" to MaeksterLightColorScheme, "dark" to MaeksterDarkColorScheme)) {
            for (surface in surfacesOf(scheme)) {
                assertTrue("$name onSurface", contrast(scheme.onSurface, surface) >= 7f)
                assertTrue("$name onSurfaceVariant", contrast(scheme.onSurfaceVariant, surface) >= 4.5f)
            }
            assertTrue("$name onSecondaryContainer", contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 7f)
            assertTrue("$name onPrimaryContainer", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 7f)
            assertTrue("$name onError", contrast(scheme.onErrorContainer, scheme.errorContainer) >= 7f)
        }
    }

    @Test
    fun theAccentStandsOutAndItsLabelIsReadable() {
        // Dark: ink on the light-orange accent. Light: white on the deeper accent, large text and controls only (iOS rule).
        assertTrue(contrast(MaeksterDarkColorScheme.onPrimary, MaeksterDarkColorScheme.primary) >= 4.5f)
        assertTrue(contrast(MaeksterLightColorScheme.onPrimary, MaeksterLightColorScheme.primary) >= 3f)
        for (scheme in listOf(MaeksterLightColorScheme, MaeksterDarkColorScheme)) {
            assertTrue("accent against the page", contrast(scheme.primary, scheme.background) >= 3f)
            assertTrue("outline against the page", contrast(scheme.outline, scheme.background) >= 3f)
        }
    }

    @Test
    fun theLosslessBadgeIsReadableOnItsSurfaces() {
        assertTrue(contrast(LightMaeksterColors.lossless, MaeksterLightColorScheme.background) >= 4f)
        assertTrue(contrast(DarkMaeksterColors.lossless, MaeksterDarkColorScheme.background) >= 4f)
    }

    @Test
    fun coversGetAShadowInLightAndABorderInDark() {
        assertEquals(Color.Transparent, LightMaeksterColors.coverBorder)
        assertTrue(LightMaeksterColors.coverShadow.alpha > 0f)
        assertEquals(Color.Transparent, DarkMaeksterColors.coverShadow)
        assertTrue(DarkMaeksterColors.coverBorder.alpha > 0f)
    }

    @Test
    fun darkSurfacesGetLighterAsTheyRise() {
        val dark = MaeksterDarkColorScheme
        val steps =
            listOf(
                dark.surfaceContainerLowest,
                dark.surfaceContainerLow,
                dark.surfaceContainer,
                dark.surfaceContainerHigh,
                dark.surfaceContainerHighest,
            ).map { it.luminance() }
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun spacingFollowsTheFourPointGrid() {
        val steps =
            listOf(
                MaeksterSpacing.xs,
                MaeksterSpacing.s,
                MaeksterSpacing.m,
                MaeksterSpacing.l,
                MaeksterSpacing.xl,
                MaeksterSpacing.xxl,
                MaeksterSpacing.xxxl,
                MaeksterSpacing.xxxxl,
            )
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 48.dp), steps)
        assertEquals(32.dp, MaeksterSpacing.section)
    }

    @Test
    fun controlsKeepAnAndroidTouchTarget() {
        assertTrue(MaeksterControlSize.regular >= 48.dp)
        assertTrue(MaeksterControlSize.compact < MaeksterControlSize.regular)
        assertTrue(MaeksterControlSize.regular < MaeksterControlSize.large)
    }

    @Test
    fun trackNumbersAndMetadataUseTabularDigits() {
        val styles = maeksterTextStyles()

        assertEquals("tnum", styles.trackNumber.fontFeatureSettings)
        assertEquals("tnum", styles.metadata.fontFeatureSettings)
        assertTrue(styles.heroTitle.fontSize > styles.detailTitle.fontSize)
        assertTrue(styles.detailTitle.fontSize > styles.body.fontSize)
        assertTrue(styles.body.fontSize > styles.caption.fontSize)
    }

    @Test
    fun aThemeModeDecidesDarkFromTheDeviceOnlyWhenFollowingTheSystem() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
    }

    private fun surfacesOf(scheme: ColorScheme): List<Color> =
        listOf(
            scheme.background,
            scheme.surface,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
        )

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
