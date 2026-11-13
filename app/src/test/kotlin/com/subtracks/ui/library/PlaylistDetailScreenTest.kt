package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val playlist =
        Playlist(
            sourceId = 1,
            id = "p1",
            name = "Road Trip",
            comment = null,
            coverArt = null,
            songCount = 99,
            created = 0,
            // Two days ago, in epoch seconds.
            changed = System.currentTimeMillis() / 1000 - 2 * 24 * 60 * 60,
            duration = 99_999,
            owner = "alex",
        )

    private fun render(
        totals: ListTotals?,
        songIds: List<String> = listOf("s0", "s1", "s2"),
    ) {
        val items =
            songIds.mapIndexed { index, songId ->
                PlaylistSongItem(
                    song =
                        Song(
                            sourceId = 1,
                            id = songId,
                            albumId = null,
                            artistId = null,
                            title = "Song ${songId.removePrefix("s")}",
                            album = null,
                            artist = "Artist",
                            duration = 200,
                            track = null,
                            disc = null,
                            starred = null,
                        ),
                    coverArt = null,
                    position = index.toLong(),
                )
            }
        composeRule.setContent {
            MaeksterTheme {
                PlaylistDetailScreen(
                    playlist = playlist,
                    songs = remember { MutableStateFlow(PagingData.from(items)) }.collectAsLazyPagingItems(),
                    coverArt = { _, _ -> null },
                    artwork = null,
                    totals = totals,
                    onBack = {},
                    onSongClick = {},
                )
            }
        }
    }

    @Test
    fun theFooterSumsTheSongsShownAndTheHeaderNamesTheOwnerAndLastChange() {
        // The footer follows the counted rows (here, say, the downloaded ones offline), not the server's totals.
        render(ListTotals(songCount = 3, duration = 3_900))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
        composeRule.onNodeWithTag(PLAYLIST_SUMMARY_TAG).assertTextEquals("3 songs · 1 hr 5 min")
        composeRule.onNodeWithText("alex · Updated 2 days ago").assertExists()
        composeRule.onNodeWithText("99 songs", substring = true).assertDoesNotExist()
    }

    @Test
    fun aSongAddedTwiceShowsBothEntries() {
        render(ListTotals(songCount = 3, duration = 600), songIds = listOf("s0", "s1", "s0"))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
        composeRule.onAllNodesWithText("Song 0").assertCountEquals(2)
    }

    @Test
    fun noFooterUntilTheTotalsAreCounted() {
        render(totals = null)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Song 2"))
        composeRule.onNodeWithTag(PLAYLIST_SUMMARY_TAG).assertDoesNotExist()
    }
}
