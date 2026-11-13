package com.subtracks.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemContextMenuRemoveTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(1, "s1", null, null, "Song", null, "Artist", 60, null, null, null)

    private fun show(
        target: MenuTarget.Song,
        remove: ((MenuTarget.Song) -> Unit)?,
    ) {
        composeRule.setContent {
            MaeksterTheme {
                ItemContextMenu(target = target, actions = ItemActions(removeFromPlaylist = remove), onDismiss = {})
            }
        }
    }

    @Test
    fun aSongFromAnEditablePlaylistCanBeRemovedAtItsPosition() {
        var removed: MenuTarget.Song? = null
        show(MenuTarget.Song(song, playlistPosition = 2), remove = { removed = it })

        composeRule.onNodeWithText("Remove from playlist").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(2L, removed?.playlistPosition)
        assertEquals("s1", removed?.song?.id)
    }

    @Test
    fun noRemoveWhenThePlaylistCannotBeEdited() {
        show(MenuTarget.Song(song, playlistPosition = 2), remove = null)

        composeRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    @Test
    fun noRemoveForASongOpenedOutsideAPlaylist() {
        show(MenuTarget.Song(song), remove = {})

        composeRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }
}
