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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.subtracks.data.mix.GenreTaxonomy
import com.subtracks.data.mix.QueueBuilderColumn
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPill
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

    @Test fun theCardPicksGenresSubGenresAndDecadesInPlaceAndSummarisesThem() {
        val calls = mutableListOf<String>()
        var builder by mutableStateOf(builderState(QueueBuilderSelection(tags = setOf(contemporary, fusion), decades = setOf(1970, 1990))))
        composeRule.setContent {
            MaeksterTheme {
                DiscoverScreen(
                    DiscoverState(),
                    false,
                    {},
                    {},
                    {},
                    queueBuilder = builder,
                    queueBuilderFocus = "Jazz",
                    queueBuilderActions = recordingActions(calls),
                )
            }
        }
        composeRule.onNodeWithText("Contemporary Jazz and Fusion · 1990s and 1970s").assertExists()
        // Jazz is picked through its sub-genres, and its sub-genres show in the second row.
        composeRule.onNodeWithText("Jazz").assertIsSelected()
        composeRule.onNodeWithText("Electronic").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Fusion").assertIsSelected()
        composeRule.onNodeWithText("Post Bop").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("1990s").assertIsSelected().performClick()
        composeRule.onNodeWithContentDescription("Play queue").performClick()
        composeRule.onNodeWithContentDescription("Reset").performClick()
        composeRule.onNodeWithText("Queue Builder").performClick()
        assertEquals(listOf("tapMain Electronic", "pill $postBop Jazz", "decade 1990", "build", "reset", "open"), calls)

        builder = builderState(QueueBuilderSelection(mains = setOf("Jazz"), decades = setOf(1970, 1990, 2000)))
        composeRule.onNodeWithText("Jazz · 2000s, 1990s and 1970s").assertExists()

        builder = builderState(QueueBuilderSelection(), matchCount = 0)
        composeRule.onNodeWithText("Anything in your library").assertExists()
        composeRule.onNodeWithContentDescription("Play queue").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Reset").assertDoesNotExist()
    }

    @Test fun withNothingDownloadedOfflineTheCardSaysSoInsteadOfOfferingAnything() {
        composeRule.setContent {
            MaeksterTheme {
                DiscoverScreen(
                    DiscoverState(offline = true),
                    false,
                    {},
                    {},
                    {},
                    queueBuilder = QueueBuilderState(loading = false, offline = true, matchCount = 0),
                )
            }
        }
        composeRule.onNodeWithText("Offline: download music to build queues from it here.").assertExists()
        composeRule.onNodeWithText("Anything in your library").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play queue").assertIsNotEnabled()
    }

    @Test fun thePageShowsGenreColumnsWithTheirSubGenresAndBuildsOnlyWhenSomethingMatches() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf(builderState(QueueBuilderSelection(mains = setOf("Jazz")), matchCount = 12))
        composeRule.setContent {
            MaeksterTheme { Surface { QueueBuilderContent(state, building = false, actions = recordingActions(calls)) } }
        }
        // A wholly picked genre shows every sub-genre under it as picked.
        composeRule.onNodeWithText("Jazz (40)").assertIsSelected()
        composeRule.onNodeWithText("Fusion (15)").assertIsSelected()
        composeRule
            .onNodeWithText("Electronic (30)")
            .performScrollTo()
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithText("Techno (25)").performScrollTo().performClick()
        composeRule.onNodeWithText("1990s (30)").performScrollTo().performClick()
        composeRule.onNodeWithText("12 matching songs").assertExists()
        composeRule.onNodeWithText("Build queue").performClick()
        assertEquals(listOf("main Electronic", "pill $techno Electronic", "decade 1990", "build"), calls)

        composeRule.onNodeWithText("Filter genres").performScrollTo().performTextInput("tech")
        composeRule.onNodeWithText("Techno (25)").assertExists()
        composeRule.onNodeWithText("House (5)").assertDoesNotExist()
        composeRule.onNodeWithText("Fusion (15)").assertDoesNotExist()

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
                            builderState(QueueBuilderSelection(tags = setOf(contemporary), decades = setOf(1990)), matchCount = 18),
                            building = false,
                            actions = QueueBuilderActions(),
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun recordingActions(calls: MutableList<String>) =
        QueueBuilderActions(
            open = { calls += "open" },
            toggleMain = { calls += "main $it" },
            tapMainInCard = { calls += "tapMain $it" },
            togglePill = { pill, main -> calls += "pill $pill $main" },
            toggleDecade = { calls += "decade $it" },
            reset = { calls += "reset" },
            build = { calls += "build" },
        )

    private val contemporary = key("Contemporary Jazz")
    private val fusion = key("Fusion")
    private val postBop = key("Post Bop")
    private val techno = key("Techno")

    private fun key(genre: String) = GenreTaxonomy.DISCOGS.resolve(genre).key

    private fun builderState(
        selection: QueueBuilderSelection,
        matchCount: Long = 40,
    ) = QueueBuilderState(
        loading = false,
        index =
            QueueBuilderIndex(
                columns =
                    listOf(
                        QueueBuilderColumn(
                            "Jazz",
                            40,
                            listOf(
                                QueueBuilderPill(contemporary, "Contemporary Jazz", 20),
                                QueueBuilderPill(fusion, "Fusion", 15),
                                QueueBuilderPill(postBop, "Post Bop", 8),
                            ),
                        ),
                        QueueBuilderColumn(
                            "Electronic",
                            30,
                            listOf(QueueBuilderPill(techno, "Techno", 25), QueueBuilderPill(key("House"), "House", 5)),
                        ),
                        QueueBuilderColumn(GenreTaxonomy.OTHER, 3, listOf(QueueBuilderPill("norway", "Norway", 3))),
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

    @Test fun freshReleasesOptInCanBeToggled() {
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            MaeksterTheme {
                FreshReleasesSwitch(enabled, false, { enabled = it })
            }
        }
        composeRule
            .onNodeWithText("Fresh releases")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithText("Fresh releases").performClick().assertIsOff()
    }

    @Test fun freshReleasesNormal() = renderFresh(1f)

    @Test fun freshReleasesLargeFont() = renderFresh(2f)

    private fun renderFresh(scale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        FreshReleasesShelf(
                            listOf(
                                com.subtracks.data.model.FreshRelease(
                                    1,
                                    "release",
                                    0,
                                    "A new release",
                                    "Library artist",
                                    "2026-11-01",
                                    "Album",
                                ),
                            ),
                            showCovers = false,
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("A new release").assertExists()
        composeRule.onNodeWithText("Library artist").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/fresh_releases_$scale.png")
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
                            queueBuilder =
                                builderState(
                                    QueueBuilderSelection(tags = setOf(contemporary, fusion), decades = setOf(1970, 2000)),
                                ),
                            queueBuilderFocus = "Jazz",
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
