package com.subtracks.ui.discover

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Artist
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val artist = Artist(1, "ar", "Station artist", 1, null)

    @Test fun actionsStartShuffleAndTheSelectedStation() {
        var shuffled = false
        var selected: Artist? = null
        composeRule.setContent {
            MaeksterTheme {
                DiscoverScreen(DiscoverState(artists = listOf(artist)), false, { shuffled = true }, { selected = it }, {})
            }
        }
        composeRule.onNodeWithText("Smart Shuffle").performClick()
        assertEquals(true, shuffled)
        composeRule.onNodeWithText("Start station").assertDoesNotExist()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithText(artist.name).performClick()
        assertEquals(artist, selected)
    }

    @Test fun autoplaySwitchCanBeEnabledAndDisabled() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaeksterTheme {
                DiscoverScreen(
                    DiscoverState(),
                    false,
                    {},
                    {},
                    {},
                    autoplaySimilar = enabled,
                    onAutoplayChange = { enabled = it },
                )
            }
        }
        composeRule
            .onNodeWithText("Autoplay similar")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithText("Autoplay similar").performClick().assertIsOff()
    }

    @Test fun offlineHidesStations() {
        composeRule.setContent {
            MaeksterTheme { DiscoverScreen(DiscoverState(true, listOf(artist)), false, {}, {}, {}) }
        }
        composeRule.onNodeWithText("Stations").assertDoesNotExist()
        composeRule.onNodeWithText("Smart Shuffle").assertExists()
        composeRule.onNodeWithText("Offline: a fresh mix from your downloaded music.").assertExists()
    }

    @Test fun normal() = render(1f, "discover")

    @Test fun largeFont() = render(2f, "discover_2x")

    private fun render(
        scale: Float,
        name: String,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        DiscoverScreen(
                            DiscoverState(
                                artists = listOf(artist.copy(name = "Weval"), artist.copy(id = "long", name = "Takuya Nakamura")),
                            ),
                            false,
                            {},
                            {},
                            {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_autoplay.png")
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(3)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_stations.png")
    }
}
