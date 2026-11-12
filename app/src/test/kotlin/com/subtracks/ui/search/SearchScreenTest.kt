package com.subtracks.ui.search

import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class SearchScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resultsAreGroupedByTypeAndTappingASongOrAlbumFiresItsCallback() {
        var played: Song? = null
        var opened: Album? = null
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "road",
                    onQueryChange = {},
                    results =
                        SearchResults(
                            albums = listOf(album("al-1", "Road Album")),
                            artists = listOf(artist("ar-1", "Road Artist")),
                            playlists = listOf(playlist("pl-1", "Road Trip")),
                            songs = listOf(AlbumSongItem(song("s1", "Road Song"), coverArt = null)),
                        ),
                    onSongClick = { played = it },
                    onAlbumClick = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Road Song").assertIsDisplayed()
        composeRule.onNodeWithText("Road Album").assertIsDisplayed()
        composeRule.onNodeWithText("Road Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()

        composeRule.onNodeWithText("Road Song").performClick()
        composeRule.onNodeWithText("Road Album").performClick()

        assertEquals("s1", played?.id)
        assertEquals("al-1", opened?.id)
    }

    @Test
    fun aQueryShorterThanThreeCharactersPromptsForMoreInput() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(query = "ro", onQueryChange = {}, results = SearchResults())
            }
        }

        composeRule.onNodeWithText("Type at least 3 characters to search.").assertIsDisplayed()
    }

    @Test
    fun aQueryWithNoResultsSaysSo() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(query = "zzz", onQueryChange = {}, results = SearchResults())
            }
        }

        composeRule.onNodeWithText("No results", substring = true).assertIsDisplayed()
    }

    @Test
    fun typingKeepsTheCursorAtTheEndAfterTheFieldIsRecreated() {
        var query by mutableStateOf("road")
        var show by mutableStateOf(true)
        composeRule.setContent {
            if (show) {
                MaeksterTheme {
                    SearchScreen(query = query, onQueryChange = { query = it }, results = SearchResults())
                }
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("s")
        assertEquals("roads", query)

        composeRule.runOnIdle { show = false }
        composeRule.runOnIdle { show = true }
        composeRule.onNode(hasSetTextAction()).performTextInput("!")
        assertEquals("roads!", query)

        composeRule.runOnIdle { composeRule.activity.currentFocus?.clearFocus() }
        composeRule.runOnIdle { show = false }
        composeRule.waitForIdle()
        // performTextInput opens the IME; close it after the field is gone so later tests in the JVM
        // do not inherit a visible keyboard.
        composeRule.activity
            .getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(composeRule.activity.window.decorView.windowToken, 0)
    }

    @Test
    fun restoringTheScreenKeepsFocusOnTheQuery() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaeksterTheme {
                SearchScreen(query = "road", onQueryChange = {}, results = SearchResults())
            }
        }

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun idsSharedAcrossTypesDoNotCollide() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "road",
                    onQueryChange = {},
                    results =
                        SearchResults(
                            albums = listOf(album("1", "Road Album")),
                            artists = listOf(artist("1", "Road Artist")),
                            playlists = listOf(playlist("1", "Road Trip")),
                            songs = listOf(AlbumSongItem(song("1", "Road Song"), coverArt = null)),
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Road Song").assertIsDisplayed()
        composeRule.onNodeWithText("Road Album").assertIsDisplayed()
        composeRule.onNodeWithText("Road Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Road Trip").assertIsDisplayed()
    }

    @Test
    fun aTypeScopeShowsOnlyThatType() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "road",
                    onQueryChange = {},
                    results =
                        SearchResults(
                            albums = listOf(album("al-1", "Road Album")),
                            songs = listOf(AlbumSongItem(song("s1", "Road Song"), coverArt = null)),
                        ),
                    scope = SearchScope.Albums,
                )
            }
        }

        composeRule.onNodeWithText("Road Album").assertIsDisplayed()
        composeRule.onNodeWithText("Road Song").assertDoesNotExist()
        composeRule.onNodeWithText("Top result").assertDoesNotExist()
    }

    @Test
    fun tappingAScopeChipSelectsIt() {
        var selected: SearchScope? = null
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "ro",
                    onQueryChange = {},
                    results = SearchResults(),
                    onScopeChange = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Playlists").performClick()
        assertEquals(SearchScope.Playlists, selected)
        composeRule.onNodeWithText("All").performClick()
        assertEquals(SearchScope.All, selected)
    }

    @Test
    fun theBestMatchLeadsAsTheTopResultAndTypesFollowTheirBestMatch() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "life",
                    onQueryChange = {},
                    results =
                        SearchResults(
                            artists = listOf(artist("ar-1", "Lifehouse")),
                            albums = listOf(album("al-1", "A Day in the Life")),
                            songs = listOf(AlbumSongItem(song("s1", "Life"), coverArt = null)),
                        ),
                )
            }
        }

        fun top(text: String) =
            composeRule
                .onNodeWithText(text)
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(top("Top result") < top("Life"))
        assertTrue("the exact song leads", top("Life") < top("Lifehouse"))
        assertTrue("the artist prefix beats the album substring", top("Lifehouse") < top("A Day in the Life"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/search_top_result.png")
    }

    @Test
    fun allPreviewsFiveSongsAndSeeAllOpensTheSongsScope() {
        var selected: SearchScope? = null
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "road",
                    onQueryChange = {},
                    results = SearchResults(songs = (1..7).map { AlbumSongItem(song("s$it", "Road $it"), coverArt = null) }),
                    onScopeChange = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Road 6").assertExists()
        composeRule.onNodeWithText("Road 7").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/search_all_preview.png")
        composeRule.onNodeWithContentDescription("See all songs").performScrollTo().performClick()
        assertEquals(SearchScope.Songs, selected)
    }

    @Test
    fun aBlankQueryShowsRecentItemsThatOpenAndClearAfterConfirming() {
        var opened: PinnedItem? = null
        var cleared = 0
        val recent =
            listOf(
                PinnedItem(1, PinKind.Album, "al-1", "Recent Album", "Some Artist", null),
                PinnedItem(1, PinKind.Artist, "ar-1", "Recent Artist", null, null),
            )
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "",
                    onQueryChange = {},
                    results = SearchResults(),
                    recent = recent,
                    onRecentClick = { opened = it },
                    onClearRecent = { cleared++ },
                )
            }
        }

        composeRule.onNodeWithText("Recent").assertIsDisplayed()
        composeRule.onNodeWithText("Album · Some Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Recent Artist").performClick()
        assertEquals(recent[1], opened)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/search_recent.png")

        composeRule.onNodeWithText("Clear").performClick()
        composeRule.onNodeWithText("Clear search history?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, cleared)
        composeRule.onNodeWithText("Clear").performClick()
        composeRule.onAllNodesWithText("Clear").onLast().performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun recentItemsRemainReadableAtLargeFontScale() {
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit
                        .Density(density.density, 2f),
            ) {
                MaeksterTheme {
                    SearchScreen(
                        query = "",
                        onQueryChange = {},
                        results = SearchResults(),
                        recent = listOf(PinnedItem(1, PinKind.Album, "al-1", "Recent Album", "Some Artist", null)),
                    )
                }
            }
        }

        composeRule.onNodeWithText("Recent Album").assertIsDisplayed()
        composeRule.onNodeWithText("Album · Some Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Clear").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/search_recent_large_font.png")
    }

    @Test
    fun typingHidesRecentItems() {
        composeRule.setContent {
            MaeksterTheme {
                SearchScreen(
                    query = "ro",
                    onQueryChange = {},
                    results = SearchResults(),
                    recent = listOf(PinnedItem(1, PinKind.Playlist, "pl-1", "Recent Playlist", null, null)),
                )
            }
        }

        composeRule.onNodeWithText("Recent Playlist").assertDoesNotExist()
        composeRule.onNodeWithText("Type at least 3 characters to search.").assertIsDisplayed()
    }

    private fun album(
        id: String,
        name: String,
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar-1",
        name = name,
        albumArtist = "Artist",
        created = 0,
        coverArt = null,
        genre = null,
        year = null,
        starred = null,
        songCount = 1,
    )

    private fun artist(
        id: String,
        name: String,
    ) = Artist(
        sourceId = 1,
        id = id,
        name = name,
        albumCount = 1,
        starred = null,
        coverArt = null,
    )

    private fun playlist(
        id: String,
        name: String,
    ) = Playlist(
        sourceId = 1,
        id = id,
        name = name,
        comment = null,
        coverArt = null,
        songCount = 1,
        created = 0,
        changed = 0,
        duration = 100,
    )

    private fun song(
        id: String,
        title: String,
    ) = Song(
        sourceId = 1,
        id = id,
        albumId = "al-1",
        artistId = "ar-1",
        title = title,
        album = "Album",
        artist = "Artist",
        duration = 100,
        track = 1,
        disc = 1,
        starred = null,
    )
}
