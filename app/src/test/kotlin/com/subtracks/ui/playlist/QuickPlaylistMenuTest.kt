package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.ui.components.LocalQuickPlaylists
import com.subtracks.ui.library.SongRow
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickPlaylistMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(1, "s1", null, null, "Illusion", null, "Boys Noize", 180, null, null, null)

    private fun playlist(
        id: String,
        name: String,
    ) = Playlist(1, id, name, null, null, 3, 1)

    @Test
    fun theHeartTogglesOnTapAndOpensQuickPlaylistsOnLongPress() {
        val toggles = mutableListOf<Boolean>()
        val opened = mutableListOf<Song>()
        rule.setContent {
            MaeksterTheme {
                CompositionLocalProvider(LocalQuickPlaylists provides { s: Song -> opened += s }) {
                    SongRow(song = song, onToggleFavorite = { toggles += it })
                }
            }
        }
        rule.onNodeWithContentDescription("Add to Favorites").performClick()
        assertEquals(listOf(true), toggles)
        rule.onNodeWithContentDescription("Add to Favorites").performTouchInput { longClick() }
        assertEquals(listOf(song), opened)
        assertEquals(listOf(true), toggles)
    }

    @Test
    fun withoutQuickPlaylistsALongPressIsAnOrdinaryTap() {
        val toggles = mutableListOf<Boolean>()
        rule.setContent { MaeksterTheme { SongRow(song = song, onToggleFavorite = { toggles += it }) } }
        rule.onNodeWithContentDescription("Add to Favorites").performTouchInput { longClick() }
        assertEquals(listOf(true), toggles)
    }

    @Test
    fun theMenuOffersEachQuickPlaylistThenTheFullSheet() {
        val added = mutableListOf<String>()
        var full = 0
        var choose = 0
        rule.setContent {
            MaeksterTheme {
                QuickPlaylistMenuSheet(
                    song = song,
                    playlists = listOf(playlist("jazz", "Jazz tunes"), playlist("techno", "Techno Chill")),
                    onAdd = { added += it.id },
                    onChoose = { choose++ },
                    onAddToPlaylist = { full++ },
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Illusion").assertExists()
        rule.onNodeWithText("Add to Techno Chill").performClick()
        rule.onNodeWithText("Add to Jazz tunes").assertExists()
        rule.onNodeWithText("Choose quick playlists…").assertDoesNotExist()
        rule.onNodeWithText("Add to playlist…").performClick()
        assertEquals(listOf("techno"), added)
        assertEquals(1, full)
        assertEquals(0, choose)
    }

    @Test
    fun withNoQuickPlaylistsTheMenuOffersToChooseSomeButNotWhileLoading() {
        var choose = 0
        var playlists by mutableStateOf<List<Playlist>?>(null)
        rule.setContent {
            MaeksterTheme {
                QuickPlaylistMenuSheet(song, playlists, onAdd = {}, onChoose = { choose++ }, onAddToPlaylist = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("Choose quick playlists…").assertDoesNotExist()
        playlists = emptyList()
        rule.onNodeWithText("Choose quick playlists…").performClick()
        assertEquals(1, choose)
    }
}
