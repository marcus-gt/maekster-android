package com.subtracks.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.ArtistAlbums
import com.subtracks.ui.library.ArtistDetailScreen
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeroBackButtonTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theBackButtonIsVisibleOverLightArtworkBeforeScrolling() {
        renderScaffold(Color.White)

        assertBackButtonStandsOut(Color.White)
    }

    @Test
    fun theBackButtonIsVisibleOverDarkArtworkBeforeScrolling() {
        renderScaffold(Color.Black)

        assertBackButtonStandsOut(Color.Black)
    }

    @Test
    fun theBackButtonKeepsItsLabelTargetAndAction() {
        var backs = 0
        renderScaffold(Color.White, onBack = { backs++ })

        composeRule
            .onNodeWithContentDescription(BACK)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, backs)
    }

    @Test
    fun theArtistBackButtonIsVisibleOverMissingArtBeforeScrolling() {
        composeRule.setContent {
            MaeksterTheme {
                ArtistDetailScreen(
                    artist = null,
                    artistName = "Artist",
                    albums = ArtistAlbums(),
                    albumsLoaded = true,
                    bio = null,
                    bioLoading = false,
                    bioUnavailable = false,
                    bioPending = false,
                    bioExpanded = false,
                    art = null,
                    coverArt = { _, _ -> null },
                    onBack = {},
                    onAlbumClick = {},
                    onExpandBio = {},
                    onCollapseBio = {},
                )
            }
        }

        assertBackButtonStandsOut(null)
    }

    private fun renderScaffold(
        heroColor: Color,
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaeksterTheme {
                HeroDetailScaffold(
                    artwork = null,
                    title = "Album",
                    onBack = onBack,
                    header = { _, _ -> Box(Modifier.fillMaxWidth().height(400.dp).background(heroColor)) },
                    content = { rowModifier ->
                        items(20) { index -> Box(rowModifier.height(48.dp)) { Text("Row $index") } }
                    },
                )
            }
        }
    }

    /**
     * The button must draw something that differs from what lies behind it. Without a background
     * colour, it compares against the most common colour in the button's area.
     */
    private fun assertBackButtonStandsOut(background: Color?) {
        val pixels = composeRule.onNodeWithContentDescription(BACK).captureToImage().toPixelMap()
        val all = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }
        val reference = background ?: mostCommon(all)
        val bright = all.count { it.luminance() > 0.8f }
        val differing = all.count { it.distanceTo(reference) > 0.25f }
        assertTrue("the back button area is a flat copy of the hero ($differing of ${all.size})", differing > all.size / 50)
        assertTrue("the back arrow is not drawn in a light colour ($bright of ${all.size})", bright > all.size / 50)
    }

    private companion object {
        const val BACK = "Back"
    }

    private fun mostCommon(colors: List<Color>): Color {
        val counts = colors.groupingBy { it }.eachCount()
        return counts.maxBy { it.value }.key
    }

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private fun Color.distanceTo(other: Color): Float = maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))
}
