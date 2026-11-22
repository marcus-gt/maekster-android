package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.recommendations.ResolvedSimilarArtist
import com.subtracks.data.recommendations.SimilarArtist
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimilarArtistsSectionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val local = Artist(1, "local-id", "Local artist", 1, null)
    private val mbid = "9ddce51c-2b75-4b3e-ac8c-1db09e7c89c6"
    private val artists =
        listOf(
            ResolvedSimilarArtist(SimilarArtist("remote", "Old name"), local),
            ResolvedSimilarArtist(SimilarArtist(null, "External artist", musicBrainzId = mbid), null),
        )

    @Test fun localAndExternalActionsUseResolvedIdsAndSafeMusicBrainzLinks() {
        var selected = ""
        var external = ""
        var refreshed = false
        composeRule.setContent {
            MaeksterTheme {
                SimilarArtistsSection(
                    SimilarArtistsState(1, false, false, artists = artists),
                    { refreshed = true },
                    { selected = it },
                    { external = it },
                )
            }
        }
        composeRule.onNodeWithText("Local artist").performClick()
        assertEquals("local-id", selected)
        composeRule.onNodeWithText("External artist").performClick()
        assertEquals("https://musicbrainz.org/artist/$mbid", external)
        composeRule.onNodeWithText("Find similar artists").performClick()
        assertEquals(true, refreshed)
        assertEquals(
            "https://musicbrainz.org/search?query=A%26B&type=artist&method=indexed",
            SimilarArtist(null, "A&B", musicBrainzId = "https://unsafe.example").musicBrainzUrl(),
        )
    }

    @Test fun anExternalPhotoBecomesTheCardImageAndLibraryArtistsUseServerArt() {
        val photo = "https://commons.wikimedia.org/wiki/Special:FilePath/External.jpg?width=500"
        val (artist, art) = artists[1].recommendation.externalArtist(1, photo)
        assertEquals("External artist", artist.name)
        assertEquals(CoverArtRef(photo, "external-artist:$photo"), art.image)
        assertEquals(
            null,
            artists[1]
                .recommendation
                .externalArtist(1, null)
                .second.image,
        )

        val asked = mutableListOf<String>()
        composeRule.setContent {
            MaeksterTheme {
                SimilarArtistsSection(
                    SimilarArtistsState(1, false, false, artists = artists, photos = mapOf(artists[1].recommendation to photo)),
                    {},
                    {},
                    {},
                    { artist ->
                        asked += artist.id
                        ArtistArt()
                    },
                )
            }
        }
        composeRule.onNodeWithText("External artist").assertExists()
        assertEquals(listOf("local-id"), asked.distinct())
    }

    @Test fun offlineEmptyIsHiddenAndOffersNoRefresh() {
        composeRule.setContent {
            MaeksterTheme { SimilarArtistsSection(SimilarArtistsState(offline = true), {}, {}, {}) }
        }
        composeRule.onNodeWithText("Similar artists").assertDoesNotExist()
        composeRule.onNodeWithText("Find similar artists").assertDoesNotExist()
    }

    @Test fun theAutomaticShelfShowsLoadingThenHidesWhenTheAnswerIsEmpty() {
        val state = androidx.compose.runtime.mutableStateOf(SimilarArtistsState(sourceId = 1, offline = false, busy = true))
        composeRule.setContent {
            MaeksterTheme { SimilarArtistsSection(state.value, null, {}, {}) }
        }
        composeRule.onNodeWithText("Similar artists").assertExists()
        composeRule.onNodeWithText("Find similar artists").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = state.value.copy(loading = false, busy = false, checked = true)
        }
        composeRule.onNodeWithText("Similar artists").assertDoesNotExist()
    }

    @Test fun normal() = render(1f)

    @Test fun largeFont() = render(2f)

    private fun render(scale: Float) {
        val externalName = "The Velvet Underground"
        val recommendations = listOf(artists.first(), ResolvedSimilarArtist(SimilarArtist(null, externalName), null))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    Surface {
                        SimilarArtistsSection(SimilarArtistsState(1, false, false, artists = recommendations), {}, {}, {})
                    }
                }
            }
        }
        val localCard = composeRule.onNode(hasClickAction() and hasText("Local artist")).getBoundsInRoot()
        val externalCard = composeRule.onNode(hasClickAction() and hasText(externalName)).getBoundsInRoot()
        assertEquals(localCard.bottom - localCard.top, externalCard.bottom - externalCard.top)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/similar_artists_$scale.png")
    }
}
