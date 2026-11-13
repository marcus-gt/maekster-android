package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickPlaylistsDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val jazz = Playlist(1, "jazz", "Jazz tunes", null, null, 3, 1)
    private val techno = Playlist(1, "techno", "Techno Chill", null, null, 3, 1)

    @Test
    fun reorderingRemovingChoosingAndTheFavoriteSwitchReachTheirCallbacks() {
        val orders = mutableListOf<List<String>>()
        val removed = mutableListOf<String>()
        val favorites = mutableListOf<Boolean>()
        var choose = 0
        rule.setContent {
            MaeksterTheme {
                QuickPlaylistsDialog(
                    playlists = listOf(jazz, techno),
                    alsoFavorite = true,
                    canChoose = true,
                    onOrder = { orders += it },
                    onRemove = { removed += it.id },
                    onAlsoFavorite = { favorites += it },
                    onChoose = { choose++ },
                    onDismiss = {},
                )
            }
        }
        val handle = rule.onNodeWithContentDescription("Reorder Jazz tunes").fetchSemanticsNode()
        rule.runOnIdle { handle.config[SemanticsActions.CustomActions].single { it.label == "Move down" }.action() }
        assertEquals(listOf(listOf("techno", "jazz")), orders)
        rule.onNodeWithTag(quickPlaylistRemoveTag("techno")).performClick()
        assertEquals(listOf("techno"), removed)
        rule.onNodeWithText("Choose playlists…").performClick()
        assertEquals(1, choose)
        rule.onNodeWithText("Also add to Favorites").performClick()
        assertEquals(listOf(false), favorites)
    }

    @Test
    fun offlineChoosingIsUnavailableAndAnEmptyListSaysSo() {
        rule.setContent {
            MaeksterTheme {
                QuickPlaylistsDialog(emptyList(), false, canChoose = false, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("No quick playlists yet.").assertExists()
        rule.onNodeWithText("Choose playlists…").assertIsNotEnabled()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertDoesNotExist()
    }
}
