package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
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
    fun tappingASongPlaysTheListFromItsRowAndShuffleIsOffered() {
        val played = mutableListOf<Pair<String, Int>>()
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
                    onShuffleSongs = { shuffled++ },
                )
            }
        }

        composeRule.onNodeWithText("Locomotion").performClick()
        composeRule.onNodeWithText("Moment's Notice").performClick()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.controls_shuffle_play)).performClick()

        assertEquals(listOf("s3" to 2, "s2" to 1), played)
        assertEquals(1, shuffled)
    }

    @Test
    fun letterJumpsToAnAbsoluteSongRow() {
        val songs = flowOf(PagingData.from((0..999).map { item("s$it", "Track $it") }))
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
    fun anEmptySongsListOffersNoShuffle() {
        composeRule.setContent {
            MaeksterTheme {
                LibraryListScreen(
                    list = LibraryTab.Songs,
                    coverArt = { _, _ -> null },
                    onSync = {},
                    onBack = {},
                    songs = flowOf(PagingData.empty()),
                )
            }
        }

        composeRule.onAllNodesWithContentDescription(composeRule.activity.getString(R.string.controls_shuffle_play)).assertCountEquals(0)
    }
}
