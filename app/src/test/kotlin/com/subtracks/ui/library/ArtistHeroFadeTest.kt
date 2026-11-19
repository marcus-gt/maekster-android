package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.ArtistAlbums
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.artworkColorsFromSeed
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistHeroFadeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun thePhotoMeltsIntoThePageWithoutAHardEdge() {
        render(artworkColorsFromSeed(0xFFE0C040.toInt()))

        assertNoEdgeAtHeroBottom()
    }

    @Test
    fun theMissingArtPlaceholderAlsoMeltsIntoThePage() {
        render(null)

        assertNoEdgeAtHeroBottom()
    }

    private fun render(artwork: com.subtracks.ui.theme.ArtworkColors?) {
        composeRule.setContent {
            MaeksterTheme {
                ArtistDetailScreen(
                    artist = null,
                    artistName = "Placeholder Artist",
                    albums = ArtistAlbums(),
                    art = null,
                    artwork = artwork,
                    coverArt = { _, _ -> null },
                    onBack = {},
                    onAlbumClick = {},
                )
            }
        }
    }

    /**
     * Compares a strip just inside the hero's bottom edge with a strip just below it, at the left margin
     * where no text sits. A hard edge shows as a jump in colour between the two.
     */
    private fun assertNoEdgeAtHeroBottom() {
        val density = composeRule.activity.resources.displayMetrics.density
        val bottom =
            composeRule
                .onNodeWithTag(ARTIST_HERO_ART_TAG)
                .getBoundsInRoot()
                .bottom.value * density
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val gap = (2 * density).roundToInt()
        val inside = (bottom - gap).roundToInt()
        val below = (bottom + gap).roundToInt()
        val xs = (0 until (10 * density).roundToInt())
        val jump = xs.maxOf { x -> image[x, inside].distanceTo(image[x, below]) }
        assertTrue("the hero bottom jumps by $jump against the page below it", jump < 0.04f)
    }

    private fun Color.distanceTo(other: Color): Float = maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))
}
