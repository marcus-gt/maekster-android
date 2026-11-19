package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.ui.components.LocalQuickPlaylists
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp")
class TopSongsSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val plays = mutableListOf<Pair<Int, Boolean>>()
    private val menu = mutableListOf<MenuTarget>()
    private val download = SongDownload(1, "2", DownloadStatus.Completed)

    private fun render(count: Int) {
        val songs =
            (1..count).map { n ->
                val song = Song(1, "$n", null, "artist", "Track $n", null, "Artist", 60, null, null, null)
                AlbumSongItem(song, null)
            }
        composeRule.setContent {
            MaeksterTheme {
                Column {
                    Text("Latest release")
                    TopSongsSection(
                        state = TopSongsState(1, songs),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        onPlay = { index, shuffle -> plays += index to shuffle },
                        onLongClick = { menu += it },
                        downloads = mapOf(download.songId to download),
                    )
                    Text("Albums")
                }
            }
        }
    }

    @Test fun carouselUsesThreeRowsAndPassesPlaybackIndexAndDownloadToActions() {
        render(5)
        val first = composeRule.onNodeWithText("Track 1").assertIsDisplayed().getBoundsInRoot()
        val third = composeRule.onNodeWithText("Track 3").assertIsDisplayed().getBoundsInRoot()
        assertEquals(first.left, third.left)
        assertTrue(third.top > first.bottom)
        composeRule.onNodeWithContentDescription("Play Top songs").performClick()
        composeRule.onNodeWithContentDescription("Shuffle Top songs").performClick()
        composeRule.onNodeWithText("Track 2").performTouchInput { longClick() }
        assertEquals(download, (menu.single() as MenuTarget.Song).download)
        composeRule.onNodeWithTag(TOP_SONGS_TAG).performScrollToIndex(1)
        composeRule.onNodeWithText("Track 4").performClick()
        assertEquals(listOf(0 to false, 0 to true, 3 to false), plays)
    }

    @Test fun songHeartTogglesFavoriteAndLongPressOpensQuickPlaylists() {
        val song = Song(1, "1", null, "artist", "Track 1", null, "Artist", 60, null, null, null)
        val current = mutableStateOf(song)
        val favorites = mutableListOf<Pair<String, Boolean>>()
        val quickPlaylists = mutableListOf<String>()
        composeRule.setContent {
            MaeksterTheme {
                CompositionLocalProvider(LocalQuickPlaylists provides { quickPlaylists += it.id }) {
                    TopSongsSection(
                        state = TopSongsState(1, listOf(AlbumSongItem(current.value, null))),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        onPlay = { index, shuffle -> plays += index to shuffle },
                        onLongClick = { menu += it },
                        onToggleFavorite = { selected, favorite ->
                            favorites += selected.id to favorite
                            current.value = selected.copy(starred = if (favorite) 100L else null)
                        },
                    )
                }
            }
        }
        val add = composeRule.activity.getString(R.string.actions_star)
        val remove = composeRule.activity.getString(R.string.actions_unstar)
        composeRule.onNodeWithContentDescription(add).performTouchInput { longClick() }
        assertEquals(listOf("1"), quickPlaylists)
        assertTrue(favorites.isEmpty())
        composeRule.onNodeWithContentDescription(add).performClick()
        composeRule.onNodeWithContentDescription(remove).performClick()
        assertEquals(listOf("1" to true, "1" to false), favorites)
        assertTrue(plays.isEmpty())
        assertTrue(menu.isEmpty())
    }

    @Test fun emptyStateLeavesNoSpaceBetweenArtistSections() {
        render(0)
        composeRule.onNodeWithText("Top songs").assertDoesNotExist()
        val release = composeRule.onNodeWithText("Latest release").getBoundsInRoot()
        val albums = composeRule.onNodeWithText("Albums").getBoundsInRoot()
        assertEquals(release.bottom, albums.top)
    }
}
