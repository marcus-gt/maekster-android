package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySongsListTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun item(
        id: String,
        title: String,
    ) = AlbumSongItem(
        song =
            Song(
                sourceId = 1,
                id = id,
                albumId = "al1",
                artistId = "ar1",
                title = title,
                album = "Blue Train",
                artist = "John Coltrane",
                duration = 300,
                track = 1,
                disc = 1,
                starred = null,
            ),
        coverArt = null,
    )

    @Test
    fun rowClicksAndBothPlaybackActionsAreReachable() {
        val played = mutableListOf<Pair<String, Int>>()
        var playAll = 0
        var shuffled = 0
        val songs = flowOf(PagingData.from(listOf(item("s1", "Blue Train"), item("s2", "Moment's Notice"), item("s3", "Locomotion"))))
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Songs,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    songs = songs,
                    onSongClick = { song, index -> played += song.id to index },
                    onPlaySongs = { playAll++ },
                    onShuffleSongs = { shuffled++ },
                )
            }
        }

        composeRule.onNodeWithText("Locomotion").performClick()
        composeRule.onNodeWithText("Moment's Notice").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.controls_play)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.controls_shuffle)).performClick()

        assertEquals(listOf("s3" to 2, "s2" to 1), played)
        assertEquals(1, playAll)
        assertEquals(1, shuffled)
    }

    @Test
    fun letterJumpsToAnAbsoluteSongRow() {
        val songs = MutableStateFlow(PagingData.from((0..999).map { item("s$it", "Track $it") }))
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Songs,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    songs = songs,
                    letterBuckets =
                        listOf(
                            com.subtracks.data.db
                                .LetterBucket("B", 900),
                        ),
                )
            }
        }
        composeRule.onNodeWithTag("letter-rail-B").performClick()
        composeRule.onNodeWithText("Track 900").assertExists()
    }

    @Test
    fun playbackActionsFollowResultChangesWhileSearching() {
        var search by mutableStateOf("Blue Train")
        var songs by mutableStateOf<Flow<PagingData<AlbumSongItem>>>(flowOf(PagingData.from(listOf(item("s1", "Blue Train")))))

        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Songs,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    search = search,
                    songs = songs,
                )
            }
        }

        val play = composeRule.onNodeWithText(composeRule.activity.getString(R.string.controls_play))
        val shuffle = composeRule.onNodeWithText(composeRule.activity.getString(R.string.controls_shuffle))
        play.assertExists()
        shuffle.assertExists()

        composeRule.runOnIdle {
            search = "No matching song"
            songs = flowOf(PagingData.empty())
        }
        play.assertDoesNotExist()
        shuffle.assertDoesNotExist()

        composeRule.runOnIdle {
            search = "coltrane"
            songs = flowOf(PagingData.from(listOf(item("s3", "Locomotion"))))
        }
        composeRule.onNodeWithText("Locomotion").assertExists()
        play.assertExists()
        shuffle.assertExists()
    }
}
