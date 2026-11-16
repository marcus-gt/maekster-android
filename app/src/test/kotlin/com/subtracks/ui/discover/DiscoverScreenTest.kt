package com.subtracks.ui.discover

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.mix.QueueBuilderGenre
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderSelection
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
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(4)
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
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(2)
        composeRule
            .onNodeWithText("Autoplay similar")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithText("Autoplay similar").performClick().assertIsOff()
    }

    @Test fun theQueueBuilderCardSummarisesThePicksAndPlaysOrOpensThem() {
        var opened = 0
        var played = 0
        var builder by mutableStateOf(builderState(QueueBuilderSelection(genres = setOf("techno", "jazz"), decades = setOf(1970, 1990))))
        composeRule.setContent {
            MaeksterTheme {
                DiscoverScreen(
                    DiscoverState(),
                    false,
                    {},
                    {},
                    {},
                    queueBuilder = builder,
                    onOpenQueueBuilder = { opened++ },
                    onPlayQueueBuilder = { played++ },
                )
            }
        }
        // Genres follow the index order (biggest first), decades newest first.
        composeRule.onNodeWithText("Jazz, Techno · 1990s, 1970s").assertExists()
        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Edit picks").performClick()
        composeRule.onNodeWithText("Queue Builder").performClick()
        assertEquals(1, played)
        assertEquals(2, opened)

        builder = builderState(QueueBuilderSelection(), matchCount = 0)
        composeRule.onNodeWithText("Anything in your library").assertExists()
        composeRule.onNodeWithText("Play").assertIsNotEnabled()
    }

    @Test fun thePickerTogglesChipsFiltersGenresAndBuildsOnlyWhenSomethingMatches() {
        val toggledGenres = mutableListOf<String>()
        val toggledDecades = mutableListOf<Int>()
        var builds = 0
        var state by mutableStateOf(builderState(QueueBuilderSelection(genres = setOf("jazz")), matchCount = 12))
        composeRule.setContent {
            MaeksterTheme {
                Surface {
                    QueueBuilderContent(
                        state,
                        building = false,
                        actions =
                            QueueBuilderActions(
                                toggleGenre = { toggledGenres += it },
                                toggleDecade = { toggledDecades += it },
                                build = { builds++ },
                            ),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Jazz (40)").assertIsSelected()
        composeRule.onNodeWithText("Techno (25)").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("1990s (30)").performScrollTo().performClick()
        assertEquals(listOf("techno"), toggledGenres)
        assertEquals(listOf(1990), toggledDecades)
        composeRule.onNodeWithText("12 matching songs").assertExists()
        composeRule.onNodeWithText("Build queue").performClick()
        assertEquals(1, builds)

        composeRule.onNodeWithText("Filter genres").performTextInput("tech")
        composeRule.onNodeWithText("Techno (25)").assertExists()
        // A picked genre stays visible while filtered out, so it can still be unpicked.
        composeRule.onNodeWithText("Jazz (40)").assertExists()
        composeRule.onNodeWithText("House (5)").assertDoesNotExist()

        state = state.copy(matchCount = 0)
        composeRule.onNodeWithText("0 matching songs").assertExists()
        composeRule.onNodeWithText("Build queue").assertIsNotEnabled()
    }

    @Test fun pickerNormal() = renderPicker(1f, "queue_builder")

    @Test fun pickerLargeFont() = renderPicker(2f, "queue_builder_2x")

    private fun renderPicker(
        scale: Float,
        name: String,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        QueueBuilderContent(
                            builderState(QueueBuilderSelection(genres = setOf("jazz"), decades = setOf(1990)), matchCount = 18),
                            building = false,
                            actions = QueueBuilderActions(),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun builderState(
        selection: QueueBuilderSelection,
        matchCount: Long = 40,
    ) = QueueBuilderState(
        loading = false,
        index =
            QueueBuilderIndex(
                genres =
                    listOf(
                        QueueBuilderGenre("jazz", "Jazz", 40),
                        QueueBuilderGenre("techno", "Techno", 25),
                        QueueBuilderGenre("house", "House", 5),
                    ),
                decades = mapOf(1970 to 10L, 1990 to 30L, 2000 to 25L),
            ),
        selection = selection,
        matchCount = matchCount,
    )

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
                            queueBuilder = builderState(QueueBuilderSelection(genres = setOf("jazz"), decades = setOf(1990))),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_queue_builder.png")
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(2)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_autoplay.png")
        composeRule.onNodeWithTag("discoverList").performScrollToIndex(4)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_stations.png")
    }
}
