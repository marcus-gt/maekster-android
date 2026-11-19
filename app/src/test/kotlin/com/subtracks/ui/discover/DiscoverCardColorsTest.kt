package com.subtracks.ui.discover

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.ui.theme.ArtworkColors
import com.subtracks.ui.theme.MaeksterPalette
import com.subtracks.ui.theme.artworkColorsFromSeed
import com.subtracks.ui.theme.artworkColorsFromSeeds
import com.subtracks.ui.theme.toHsl
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCardColorsTest {
    @Test
    fun whiteTextKeepsAaContrastOnEveryDiscoverGradient() {
        val sweep =
            (0 until 360 step 15).flatMap { hue ->
                listOf(0.1f, 0.5f, 0.95f).flatMap { saturation ->
                    listOf(0.15f, 0.5f, 0.85f).map { lightness -> "seed $hue/$saturation/$lightness" to seeded(hue, saturation, lightness) }
                }
            }
        val cards =
            RadioMood.entries.map { "mood $it" to it.cardColors() } +
                listOf(
                    "song path" to songPathColors(),
                    "smart shuffle dark" to accent(MaeksterPalette.AccentDark, MaeksterPalette.AccentSecondaryDark),
                    "smart shuffle light" to accent(MaeksterPalette.AccentLight, MaeksterPalette.AccentSecondaryLight),
                ) + sweep
        for ((name, colors) in cards) {
            for (stop in colors.discoverCardStops()) {
                val ratio = contrast(Color.White, stop)
                assertTrue("$name: white on $stop is $ratio:1", ratio >= 4.5f)
            }
            val panel = colors.discoverCardStops().map { VividPanel.compositeOver(it) }
            panel.forEach { assertTrue("$name: white on the panel", contrast(Color.White, it) >= 4.5f) }
        }
    }

    @Test
    fun songPathPlayIconIsReadableOnItsWhiteButton() {
        val ratio = contrast(songPathColors().darkPrimary, Color.White)
        assertTrue("Play icon contrast is $ratio:1", ratio >= 4.5f)
    }

    @Test
    fun moodsStartFromClearlyDifferentHues() {
        val hues =
            RadioMood.entries.map {
                it
                    .cardColors()
                    .accents[0]
                    .toHsl()
                    .first
            }
        for (i in hues.indices) {
            for (j in i + 1 until hues.size) {
                val delta = kotlin.math.abs(hues[i] - hues[j]).let { minOf(it, 360f - it) }
                assertTrue("${RadioMood.entries[i]} and ${RadioMood.entries[j]} are only $delta° apart", delta >= 15f)
            }
        }
    }

    private fun seeded(
        hue: Int,
        saturation: Float,
        lightness: Float,
    ): ArtworkColors = artworkColorsFromSeed(Color.hsl(hue.toFloat(), saturation, lightness).toArgb())

    private fun accent(
        primary: Color,
        secondary: Color,
    ) = artworkColorsFromSeeds(primary.toArgb(), secondary.toArgb())

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (high, low) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (high + 0.05f) / (low + 0.05f)
    }

    private fun Color.compositeOver(background: Color): Color {
        val a = alpha
        return Color(
            red = red * a + background.red * (1 - a),
            green = green * a + background.green * (1 - a),
            blue = blue * a + background.blue * (1 - a),
        )
    }
}
