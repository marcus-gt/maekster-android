package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongRowFavoriteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun song(
        id: String,
        starred: Long?,
    ) = Song(
        sourceId = 1,
        id = id,
        albumId = "al1",
        artistId = "ar1",
        title = "Song $id",
        album = "Blue Train",
        artist = "John Coltrane",
        duration = 300,
        track = id.last().digitToInt().toLong(),
        disc = 1,
        starred = starred,
    )

    @Test
    fun eachAlbumTrackHasAHeartThatTogglesItsFavorite() {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        composeRule.setContent {
            MaeksterTheme {
                AlbumDetailScreen(
                    album =
                        Album(
                            sourceId = 1,
                            id = "al1",
                            artistId = "ar1",
                            name = "Blue Train",
                            albumArtist = "John Coltrane",
                            created = 0,
                            coverArt = null,
                            genre = null,
                            year = 1957,
                            starred = null,
                            songCount = 2,
                        ),
                    songs = listOf(song("s1", starred = null), song("s2", starred = 100)),
                    coverArt = { _, _ -> null },
                    artwork = null,
                    onBack = {},
                    onSongClick = {},
                    onToggleFavorite = { song, favorite -> toggled += song.id to favorite },
                )
            }
        }

        val list = composeRule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasContentDescription("Add to Favorites"))
        composeRule.onNodeWithContentDescription("Add to Favorites").performClick()
        // The second track sits below the album header on a small screen.
        list.performScrollToNode(hasContentDescription("Remove from Favorites"))
        composeRule.onNodeWithContentDescription("Remove from Favorites").performClick()

        assertEquals(listOf("s1" to true, "s2" to false), toggled)
    }

    @Test
    fun withoutAToggleTheRowShowsNoHeart() {
        composeRule.setContent { MaeksterTheme { SongRow(song = song("s1", starred = null), durationSeconds = 300) } }

        composeRule.onAllNodesWithContentDescription("Add to Favorites").assertCountEquals(0)
    }
}
