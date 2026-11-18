package com.subtracks.ui.discover

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Artist
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.prefs.DiscoverLayout
import com.subtracks.data.prefs.DiscoverShelf
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render smoke test for the Discover cards: writes PNGs for local review; it does not verify pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp-xhdpi")
class DiscoverCardsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun cardsNormal() = render(1f)

    @Test fun cardsLargeFont() = render(2f)

    private fun render(fontScale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaeksterTheme {
                    Surface(Modifier.fillMaxSize()) {
                        DiscoverScreen(
                            state = DiscoverState(artists = ARTISTS, moods = true, sourceId = 1),
                            busy = false,
                            onShuffle = {},
                            onStation = {},
                            onOpenSettings = {},
                            layout =
                                DiscoverLayout(
                                    order =
                                        listOf(
                                            DiscoverShelf.SmartShuffle,
                                            DiscoverShelf.Stations,
                                            DiscoverShelf.Moods,
                                            DiscoverShelf.SongPath,
                                            DiscoverShelf.FreshReleases,
                                        ),
                                    hidden = setOf(DiscoverShelf.QueueBuilder),
                                ),
                            freshReleases = FreshReleasesState(enabled = true, releases = RELEASES),
                            showReleaseCovers = false,
                            songPathContent = {
                                SongPathCard(rememberSongPath(1), busy = false, building = false, cover = { null }) { _, _, _ -> }
                            },
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Smart Shuffle").assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/discover_cards_$fontScale.png")
    }

    private companion object {
        val RELEASES =
            listOf("Das letzte Bollwerk" to "Extrawelt", "Situation" to "2 Chainz", "Trouble Mountain" to "Aesop Rock").mapIndexed {
                index,
                (title, artist),
                ->
                FreshRelease(1, "rg-$index", index, title, artist, "2026-11-14", "Album")
            }

        val ARTISTS =
            listOf("Web Web", "Max Richter", "The Prodigy").mapIndexed { index, name ->
                Artist(sourceId = 1, id = "ar-$index", name = name, albumCount = 3, starred = null)
            }
    }
}
