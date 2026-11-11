package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun label(entry: LibraryHubEntry) = composeRule.activity.getString(entry.label)

    @Test
    fun eachEntryOpensItsPage() {
        val opened = mutableListOf<LibraryHubEntry>()
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = LIBRARY_HUB_ENTRIES,
                    offline = false,
                    onOpenEntry = { opened += it },
                    onOpenSettings = {},
                )
            }
        }

        LIBRARY_HUB_ENTRIES.forEach { composeRule.onNodeWithText(label(it)).performClick() }

        assertEquals(LIBRARY_HUB_ENTRIES, opened)
    }

    @Test
    fun theEntriesFollowTheIosOrderAndFavoritesIsAPageNotAList() {
        assertEquals(
            listOf(
                LibraryHubEntry.Playlists,
                LibraryHubEntry.Albums,
                LibraryHubEntry.Artists,
                LibraryHubEntry.Songs,
                LibraryHubEntry.Favorites,
            ),
            LIBRARY_HUB_ENTRIES,
        )
        assertEquals(LibraryTab.Songs, LibraryHubEntry.Songs.list)
        assertEquals(LibraryTab.Albums, LibraryHubEntry.Albums.list)
        assertNull(LibraryHubEntry.Favorites.list)
    }

    @Test
    fun theSettingsActionOpensSettings() {
        var settingsOpened = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = LIBRARY_HUB_ENTRIES,
                    offline = false,
                    onOpenEntry = {},
                    onOpenSettings = { settingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.navigation_tabs_settings)).performClick()

        assertTrue(settingsOpened)
    }

    @Test
    fun offlineOnlyTheListsWithDownloadsAndFavoritesAreOffered() {
        assertEquals(
            listOf(LibraryHubEntry.Albums, LibraryHubEntry.Artists, LibraryHubEntry.Favorites),
            offlineLibraryEntries(setOf(LibraryListTab.Artists, LibraryListTab.Albums)),
        )
        assertEquals(
            listOf(LibraryHubEntry.Songs, LibraryHubEntry.Favorites),
            offlineLibraryEntries(setOf(LibraryListTab.Songs)),
        )
        assertEquals(emptyList<LibraryHubEntry>(), offlineLibraryEntries(emptySet()))
    }

    @Test
    fun offlineWithNothingDownloadedSaysSoAndOffersToGoOnline() {
        var wentOnline = 0
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = emptyList(),
                    offline = true,
                    onOpenEntry = {},
                    onOpenSettings = {},
                    onGoOnline = { wentOnline++ },
                )
            }
        }

        LIBRARY_HUB_ENTRIES.forEach { composeRule.onAllNodesWithText(label(it)).assertCountEquals(0) }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_go_online)).performClick()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.offline_mode_status)).performClick()

        assertEquals("both the empty state and the offline action should go online", 2, wentOnline)
    }
}
