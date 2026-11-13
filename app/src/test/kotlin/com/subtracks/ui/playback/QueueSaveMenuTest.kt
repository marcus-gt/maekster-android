package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QueueSaveMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        canSave: Boolean,
        onSave: () -> Unit = {},
    ) {
        val song = Song(1, "s1", "al1", "ar1", "Queued song", "Album", "Artist", 200, 1, 1, null)
        rule.setContent {
            MaeksterTheme {
                QueueScreen(
                    rows = listOf(QueueRow(1, 0, AlbumSongItem(song, null))),
                    ready = true,
                    currentPosition = 0,
                    currentSongId = "s1",
                    coverArt = { _, _ -> null },
                    onBack = {},
                    onPlay = {},
                    onRemove = {},
                    onReorder = { _, _ -> },
                    onMove = { _, _ -> false },
                    onLoadOlder = {},
                    onLoadNewer = {},
                    onUndo = {},
                    canSaveAsPlaylist = canSave,
                    onSaveAsPlaylist = onSave,
                )
            }
        }
    }

    @Test
    fun theQueueMenuOffersSavingAsAPlaylist() {
        var opened = 0
        show(canSave = true) { opened++ }
        rule.onNodeWithContentDescription("Queue options").performClick()
        rule.onNodeWithText("Save queue as playlist…").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun offlineOrEmptyTheMenuDoesNotOfferIt() {
        show(canSave = false)
        rule.onNodeWithContentDescription("Queue options").assertDoesNotExist()
    }
}
