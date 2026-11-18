package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeYourPlaylistsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val twoDaysAgo = System.currentTimeMillis() / 1000 - 2 * 24 * 60 * 60

    private fun playlist(
        id: String,
        changed: Long,
        songs: Long,
    ) = Playlist(sourceId = 1, id = id, name = id, comment = null, coverArt = null, songCount = songs, created = 0, changed = changed)

    @Test
    fun yourPlaylistsLeadHomeWithWhenEachWasUpdatedAndOpenTheirPlaylist() {
        val opened = mutableListOf<String>()
        var more = 0
        composeRule.setContent {
            MaeksterTheme {
                HomeScreen(
                    feed = HomeFeed(yourPlaylists = listOf(playlist("Road Trip", twoDaysAgo, 24), playlist("Undated", 0, 1))),
                    coverArt = { _, _ -> null },
                    playingSongId = null,
                    onPlaylistClick = { opened += it.id },
                    onPlaylistsMore = { more++ },
                )
            }
        }

        composeRule.onNodeWithTag(YOUR_PLAYLISTS_TAG).assertExists()
        composeRule.onNodeWithText("Your playlists").assertExists()
        composeRule.onNodeWithText("Updated 2 days ago").assertExists()
        composeRule.onNodeWithText("24 songs").assertExists()
        composeRule.onNodeWithText("1 song").assertExists()
        // A playlist with no change date has no "Updated" line.
        composeRule
            .onAllNodesWithText("Updated", substring = true)
            .fetchSemanticsNodes()
            .size
            .let { assertEquals(1, it) }

        composeRule.onNodeWithText("Road Trip").performClick()
        composeRule.onNodeWithText("Your playlists").performClick()
        assertEquals(listOf("Road Trip"), opened)
        assertEquals(1, more)
    }

    @Test
    fun aLibraryWithOnlyPlaylistsStillHasAHome() {
        val feed =
            buildHomeFeed(
                recentAlbums = emptyList(),
                recentArtists = emptyList(),
                frequentAlbums = emptyList(),
                frequentArtists = emptyList(),
                genres = emptyList(),
                decades = emptyList(),
                starredSongs = emptyList(),
                addedAlbums = emptyList(),
                rediscover = emptyList(),
                hasPlayData = false,
                playlists = listOf(playlist("Only", twoDaysAgo, 3)),
            )
        composeRule.setContent {
            MaeksterTheme { HomeScreen(feed = feed, coverArt = { _, _ -> null }, playingSongId = null) }
        }

        assertTrue(feed.yourPlaylists.isNotEmpty())
        composeRule.onNodeWithText("Only").assertExists()
        composeRule.onNodeWithText("Sync").assertDoesNotExist()
    }
}
