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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryHubScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun label(tab: LibraryTab) = composeRule.activity.getString(tab.label)

    @Test
    fun eachEntryOpensItsList() {
        val opened = mutableListOf<LibraryTab>()
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = LIBRARY_HUB_ENTRIES,
                    offline = false,
                    onOpenList = { opened += it },
                    onOpenSettings = {},
                )
            }
        }

        LIBRARY_HUB_ENTRIES.forEach { composeRule.onNodeWithText(label(it)).performClick() }

        assertEquals(LIBRARY_HUB_ENTRIES, opened)
    }

    @Test
    fun theEntriesFollowTheIosOrder() {
        assertEquals(listOf(LibraryTab.Playlists, LibraryTab.Albums, LibraryTab.Artists), LIBRARY_HUB_ENTRIES)
    }

    @Test
    fun theSettingsActionOpensSettings() {
        var settingsOpened = false
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = LIBRARY_HUB_ENTRIES,
                    offline = false,
                    onOpenList = {},
                    onOpenSettings = { settingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.navigation_tabs_settings)).performClick()

        assertTrue(settingsOpened)
    }

    @Test
    fun offlineOnlyTheListsWithDownloadsAreOffered() {
        assertEquals(
            listOf(LibraryTab.Albums, LibraryTab.Artists),
            offlineLibraryEntries(setOf(LibraryListTab.Artists, LibraryListTab.Albums)),
        )
        assertEquals(emptyList<LibraryTab>(), offlineLibraryEntries(emptySet()))
    }

    @Test
    fun offlineWithNothingDownloadedSaysSoAndOffersToGoOnline() {
        var wentOnline = 0
        composeRule.setContent {
            MaeksterTheme {
                LibraryHubScreen(
                    entries = emptyList(),
                    offline = true,
                    onOpenList = {},
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
