package com.subtracks.ui.downloads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.DownloadedSong
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DownloadsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val active =
        DownloadedSong(
            songId = "s1",
            sourceId = 1,
            title = "Active",
            albumId = "al1",
            albumName = "Album",
            artistId = "ar1",
            artistName = "Artist",
            status = DownloadStatus.Running,
            size = 40,
            bytes = 40,
            total = 100,
        )
    private val done = active.copy(songId = "s2", title = "Done", status = DownloadStatus.Completed)

    @Test
    fun anInProgressNodeShowsCancelAndACompletedNodeShowsDelete() {
        var cancelled: List<String>? = null
        render(
            tree =
                DownloadTree(
                    artists =
                        listOf(
                            artist("ar1", "Active", listOf(active)),
                            artist("ar2", "Done", listOf(done)),
                        ),
                ),
            onCancel = { cancelled = it },
        )

        composeRule.onAllNodesWithContentDescription("Cancel download", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Delete downloads", substring = true).assertCountEquals(1)

        composeRule.onNodeWithContentDescription("Cancel download of Active").performClick()
        assertEquals(listOf("s1"), cancelled)
    }

    @Test
    fun songsUnderAnInProgressNodeAlsoShowCancel() {
        render(tree = DownloadTree(artists = listOf(artist("ar1", "Artist", listOf(active, done)))), onCancel = {})

        expandArtistAndAlbum()

        composeRule.onAllNodesWithContentDescription("Cancel download", substring = true).assertCountEquals(3)
        composeRule.onAllNodesWithContentDescription("Delete download of Done").assertCountEquals(1)
    }

    @Test
    fun transfersShowEachStateAndOfferRetryOnlyForFailures() {
        val queued = active.copy(songId = "s3", title = "Waiting", status = DownloadStatus.Queued, bytes = 0, total = 0)
        val failed =
            active.copy(
                songId = "s4",
                title = "Broken",
                status = DownloadStatus.Failed,
                error = com.subtracks.data.model.DownloadError.ServerRefused,
            )
        val retried = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        composeRule.setContent {
            MaeksterTheme {
                DownloadsScreen(
                    tree = DownloadTree(artists = listOf(artist("ar1", "Artist", listOf(active, queued, failed, done)))),
                    loadEncoding = { null },
                    onBack = {},
                    onDelete = {},
                    onCancel = {},
                    transfers = transfersOf(listOf(failed, queued, done, active)),
                    onRetry = { retried += it.songId },
                    onCancelTransfer = { cancelled += it.songId },
                )
            }
        }

        composeRule.onNodeWithText("Transfers").assertExists()
        composeRule.onNodeWithText("Downloading").assertExists()
        composeRule.onNodeWithText("40 B of 100 B").assertExists()
        composeRule.onNodeWithText("Queued").assertExists()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.error_download_server_refused)).assertExists()
        composeRule.onAllNodesWithContentDescription("Retry download", substring = true).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Retry download of Broken").performClick()
        composeRule.onAllNodesWithContentDescription("Cancel download of Waiting")[0].performClick()
        assertEquals(listOf("s4"), retried)
        assertEquals(listOf("s3"), cancelled)
        assertEquals(listOf("s1", "s3", "s4"), transfersOf(listOf(failed, queued, done, active)).map { it.songId })
    }

    private fun artist(
        id: String,
        name: String,
        songs: List<DownloadedSong>,
    ) = DownloadArtistNode(
        id = id,
        name = name,
        bytes = 0,
        albums = listOf(DownloadAlbumNode(id = "al-$id", name = "Album", bytes = 0, songs = songs)),
    )

    private fun render(
        tree: DownloadTree,
        onCancel: (List<String>) -> Unit,
    ) {
        composeRule.setContent {
            MaeksterTheme {
                DownloadsScreen(
                    tree = tree,
                    loadEncoding = { null },
                    onBack = {},
                    onDelete = {},
                    onCancel = onCancel,
                )
            }
        }
    }

    private fun expandArtistAndAlbum() {
        composeRule.onAllNodesWithContentDescription("Expand")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Expand")[0].performClick()
    }
}
