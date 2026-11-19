package com.subtracks.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Playlist
import com.subtracks.ui.library.ALBUM_COVER_TAG
import com.subtracks.ui.library.LibraryListScreen
import com.subtracks.ui.library.LibraryTab
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullingDownRefreshesAndBackLeavesTheList() {
        var synced = false
        var wentBack = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Albums,
                    albums = remember { flowOf(PagingData.from(albums())) },
                    coverArt = { _, _ -> null },
                    onSync = { synced = true },
                    onBack = { wentBack = true },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ALBUM_COVER_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        // Start below the top bar, which does not scroll, so the drag reaches the list.
        composeRule.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 600) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.navigation_back)).performClick()

        assertTrue("pulling down should trigger a sync", synced)
        assertTrue("the back arrow should leave the list", wentBack)
    }

    @Test
    fun theTopBarNamesTheListAndSitsAtTheTop() {
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Playlists,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                )
            }
        }
        val options = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.list_options))
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        composeRule.onNodeWithText(composeRule.activity.getString(LibraryTab.Playlists.label)).assertExists()
        assertTrue(
            "the list options should sit in the top bar",
            options.getUnclippedBoundsInRoot().top < (root.bottom - root.top) / 4,
        )
    }

    @Test
    fun anEmptyListOffersSync() {
        var synced = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Albums,
                    albums = remember { flowOf(PagingData.empty<Album>(settledLoadStates)) },
                    coverArt = { _, _ -> null },
                    onSync = { synced = true },
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sync").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sync").performClick()

        assertTrue("an empty library should offer a sync", synced)
    }

    @Test
    fun onlyTheShownListCollectsItsPagingFlow() {
        val collected = CopyOnWriteArrayList<String>()
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Artists,
                    albums =
                        flow<PagingData<Album>> {
                            collected += "albums"
                            emit(PagingData.empty())
                        },
                    artists =
                        flow<PagingData<Artist>> {
                            collected += "artists"
                            emit(PagingData.from(artists()))
                        },
                    playlists =
                        flow<PagingData<Playlist>> {
                            collected += "playlists"
                            emit(PagingData.empty())
                        },
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { collected.contains("artists") }
        composeRule.waitForIdle()

        assertEquals(listOf("artists"), collected.toList())
    }

    @Test
    fun artistLayoutCanBeChangedWhileSearching() {
        val asGrid = mutableStateOf(false)
        val search = mutableStateOf("Artist")
        var opened: Artist? = null
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Artists,
                    artists = remember { flowOf(PagingData.from(artists().take(3))) },
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    search = search.value,
                    onSearchChange = { search.value = it },
                    artistsAsGrid = asGrid.value,
                    onArtistsLayoutChange = { asGrid.value = it },
                    onArtistClick = { opened = it },
                )
            }
        }

        try {
            val first = composeRule.onNodeWithText("Artist 1")
            val second = composeRule.onNodeWithText("Artist 2")
            assertTrue(first.getUnclippedBoundsInRoot().top < second.getUnclippedBoundsInRoot().top)

            composeRule.onNodeWithContentDescription("Show artists as a grid").assertIsDisplayed().performClick()
            assertTrue(first.getUnclippedBoundsInRoot().right < second.getUnclippedBoundsInRoot().left)
            second.performClick()
            assertEquals("Artist 2", opened?.name)

            composeRule.onNodeWithContentDescription("Show artists as a list").assertIsDisplayed().performClick()
            assertTrue(first.getUnclippedBoundsInRoot().top < second.getUnclippedBoundsInRoot().top)
            assertEquals("Artist", search.value)
        } finally {
            composeRule.onNodeWithContentDescription("Close search").performClick()
        }
    }

    @Test
    fun albumLayoutCanBeChangedWhileSearching() {
        val asList = mutableStateOf(false)
        val search = mutableStateOf("Album")
        var opened: Album? = null
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Albums,
                    albums = remember { flowOf(PagingData.from(albums().take(1))) },
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    search = search.value,
                    onSearchChange = { search.value = it },
                    albumsAsList = asList.value,
                    onAlbumsLayoutChange = { asList.value = it },
                    onAlbumClick = { opened = it },
                )
            }
        }

        try {
            val showList = composeRule.onNodeWithContentDescription("Show albums as a list")
            val showGrid = composeRule.onNodeWithContentDescription("Show albums as a grid")
            val title = albums().first().name
            showList.assertIsDisplayed().performClick()
            composeRule.onNodeWithText(title).assertIsDisplayed().performClick()
            assertEquals(title, opened?.name)

            showGrid.assertIsDisplayed().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(ALBUM_COVER_TAG).fetchSemanticsNodes().isNotEmpty()
            }
            showList.performClick()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            assertEquals("Album", search.value)
        } finally {
            composeRule.onNodeWithContentDescription("Close search").performClick()
        }
    }

    @Test
    fun theSearchActionOpensAndClosesTheListSearch() {
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Albums,
                    albums = remember { flowOf(PagingData.from(albums())) },
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                )
            }
        }
        val searchLabel = composeRule.activity.getString(R.string.search_this_list)
        val closeLabel = composeRule.activity.getString(R.string.close_search)

        composeRule.onNodeWithContentDescription(searchLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(closeLabel).assertExists()

        // Closing also stops the focused field's cursor blink, which would otherwise outlive the test and stall
        // frame dispatch for the next Compose tests in this JVM.
        composeRule.onNodeWithContentDescription(closeLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(searchLabel).assertExists()
    }

    @Test
    fun openingAResultKeepsTheListSearchQuery() {
        val search = mutableStateOf("xyz")
        val show = mutableStateOf(true)
        var opened: Album? = null
        composeRule.setContent {
            if (show.value) {
                MaeksterTheme {
                    LibraryListScreen(
                        list = LibraryTab.Albums,
                        albums = remember { flowOf(PagingData.from(albums())) },
                        coverArt = { _, _ -> null },
                        onAlbumClick = { opened = it },
                        onSync = {},
                        onBack = {},
                        search = search.value,
                        onSearchChange = { search.value = it },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ALBUM_COVER_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag(ALBUM_COVER_TAG)[0].performClick()
        composeRule.waitForIdle()

        assertEquals("al-1", opened?.id)
        assertEquals("xyz", search.value)

        composeRule.runOnIdle { show.value = false }
        composeRule.waitForIdle()
    }

    @Test
    fun backClosesTheListSearchBeforeLeavingTheList() {
        val search = mutableStateOf("xyz")
        var wentBack = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Albums,
                    albums = remember { flowOf(PagingData.from(albums())) },
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = { wentBack = true },
                    search = search.value,
                    onSearchChange = { search.value = it },
                )
            }
        }

        val closeLabel = composeRule.activity.getString(R.string.close_search)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(closeLabel).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals("back should clear the list search", "", search.value)
        composeRule.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
        assertFalse("back should close the search first", wentBack)
        assertFalse("back should not leave the app", composeRule.activity.isFinishing)
    }

    @Test
    fun theOfflineIndicatorGoesOnline() {
        var exited = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Playlists,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    offline = true,
                    onExitOffline = { exited = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.offline_mode_status))
            .performClick()
        assertTrue("the offline indicator should leave offline mode", exited)
    }

    private val settledLoadStates =
        LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

    private fun albums() =
        (1..60).map { index ->
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

    private fun artists() =
        (1..60).map { index ->
            Artist(
                sourceId = 1,
                id = "ar-$index",
                name = "Artist $index",
                albumCount = 1,
                starred = null,
                coverArt = null,
            )
        }
}
