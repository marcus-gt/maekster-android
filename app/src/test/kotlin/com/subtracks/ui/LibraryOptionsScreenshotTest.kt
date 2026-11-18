package com.subtracks.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.prefs.ListQuery
import com.subtracks.ui.library.ALBUM_COVER_TAG
import com.subtracks.ui.library.LibraryListScreen
import com.subtracks.ui.library.LibraryTab
import com.subtracks.ui.library.sortOptionsFor
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Render smoke test: asserts the awaited node exists and writes a PNG for local review; it does not verify pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class LibraryOptionsScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun resetFontScale() = RuntimeEnvironment.setFontScale(1f)

    @Test
    fun libraryLayoutsAndOptions() = captureLayoutsAndOptions(1f, "")

    @Test
    fun libraryLayoutsAndOptionsAtLargeFontScale() = captureLayoutsAndOptions(2f, "_large_font")

    private fun captureLayoutsAndOptions(
        fontScale: Float,
        suffix: String,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val asList = mutableStateOf(false)
        val artistsAsGrid = mutableStateOf(false)
        val tab = mutableStateOf(LibraryTab.Albums)
        composeRule.setContent {
            MaeksterTheme {
                key(tab.value) {
                    LibraryListScreen(
                        list = tab.value,
                        albums = remember { flowOf(PagingData.from(albums())) },
                        artists = remember { flowOf(PagingData.from(artists())) },
                        coverArt = { _, _ -> null },
                        onSync = {},
                        onBack = {},
                        listQuery = ListQuery("Name"),
                        sortOptions = sortOptionsFor(tab.value),
                        starredSupported = true,
                        albumsAsList = asList.value,
                        onAlbumsLayoutChange = { asList.value = it },
                        artistsAsGrid = artistsAsGrid.value,
                        onArtistsLayoutChange = { artistsAsGrid.value = it },
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ALBUM_COVER_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        capture("library_albums_grid$suffix")

        composeRule.onNodeWithContentDescription("Show albums as a list").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Album 1").assertIsDisplayed()
        capture("library_albums_list$suffix")

        openOptions()
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        capture("library_options$suffix")

        composeRule.runOnIdle { tab.value = LibraryTab.Artists }
        composeRule.onNodeWithText("Artist 1").assertIsDisplayed()
        capture("library_artists_list$suffix")

        composeRule.onNodeWithContentDescription("Show artists as a grid").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Artist 1").assertIsDisplayed()
        capture("library_artists_grid$suffix")

        openOptions()
        composeRule.onNodeWithText("Album count").assertIsDisplayed()
        capture("library_artists_options$suffix")
    }

    private fun openOptions() {
        composeRule.onNodeWithContentDescription("List options").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun artists() =
        (1..7).map { index ->
            Artist(
                sourceId = 1,
                id = "ar-$index",
                name = "Artist $index",
                albumCount = index.toLong(),
                starred = null,
                coverArt = null,
            )
        }

    private fun albums() =
        (1..7).map { index ->
            Album(
                sourceId = 1,
                id = "al-$index",
                artistId = "ar-1",
                name = "Album $index",
                albumArtist = "Artist",
                created = 0,
                coverArt = null,
                genre = null,
                year = null,
                starred = null,
                songCount = 1,
            )
        }
}
