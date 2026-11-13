package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
        onSongLongClick: (MenuTarget) -> Unit = {},
        onMoveSong: ((PlaylistSongItem, Long) -> Unit)? = null,
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
                    onSongLongClick = onSongLongClick,
                    onMoveSong = onMoveSong,
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
    fun theMenuOfASecondCopyKnowsWhichEntryItIs() {
        var opened: MenuTarget? = null
        render(ListTotals(songCount = 3, duration = 600), songIds = listOf("s0", "s1", "s0"), onSongLongClick = { opened = it })

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
        composeRule.onAllNodesWithText("Song 0")[1].performTouchInput { longClick() }

        assertEquals(2L, (opened as? MenuTarget.Song)?.playlistPosition)
    }

    @Test
    fun draggingASecondCopySendsOneMoveOnDrop() {
        val moves = mutableListOf<Pair<Long, Long>>()
        render(ListTotals(3, 600), listOf("s0", "s1", "s0"), onMoveSong = { item, to -> moves += item.position to to })
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
        val from = composeRule.onNodeWithTag("playlist-drag-2", useUnmergedTree = true)
        val destination =
            composeRule
                .onNodeWithTag("playlist-drag-0", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot.center.y
        val delta =
            destination -
                from
                    .fetchSemanticsNode()
                    .boundsInRoot.center.y
        from.performTouchInput {
            down(center)
            moveBy(Offset(0f, delta / 2))
            moveBy(Offset(0f, delta / 2))
        }
        assertEquals(emptyList<Pair<Long, Long>>(), moves)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/playlist_drag_active.png")
        from.performTouchInput { up() }
        assertEquals(listOf(2L to 0L), moves)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/playlist_drag_handles.png")
    }

    @Test
    fun cancelledDragSendsNoMove() {
        val moves = mutableListOf<Long>()
        render(ListTotals(3, 600), onMoveSong = { _, to -> moves += to })
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
        composeRule.onNodeWithTag("playlist-drag-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            cancel()
        }
        assertEquals(emptyList<Long>(), moves)
    }

    @Test
    @Suppress("DEPRECATION")
    fun handlesFitAtLargeFontScale() {
        val resources = composeRule.activity.resources
        val previous = android.content.res.Configuration(resources.configuration)
        resources.updateConfiguration(
            android.content.res
                .Configuration(previous)
                .apply { fontScale = 2f },
            resources.displayMetrics,
        )
        try {
            render(ListTotals(3, 600), onMoveSong = { _, _ -> })
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(PLAYLIST_SUMMARY_TAG))
            composeRule.onNodeWithTag("playlist-drag-2", useUnmergedTree = true).assertExists()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/playlist_drag_handles_2x.png")
        } finally {
            resources.updateConfiguration(previous, resources.displayMetrics)
        }
    }

    @Test
    fun noFooterUntilTheTotalsAreCounted() {
        render(totals = null)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Song 2"))
        composeRule.onNodeWithTag(PLAYLIST_SUMMARY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("playlist-drag-2", useUnmergedTree = true).assertDoesNotExist()
    }
}
