package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.ArtistAlbums
import com.subtracks.data.model.Song
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
@Config(qualifiers = "w400dp-h1400dp")
class ArtistLatestReleaseTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val menus = mutableListOf<MenuTarget>()

    private fun render(
        albums: ArtistAlbums,
        topSongs: (@Composable () -> Unit)? = null,
        likedSongs: (@Composable () -> Unit)? = null,
    ) {
        composeRule.setContent {
            MaeksterTheme {
                ArtistDetailScreen(
                    artist = Artist(sourceId = 1, id = "ar-1", name = "Bob Dylan", albumCount = 3, starred = null, coverArt = null),
                    artistName = "Bob Dylan",
                    albums = albums,
                    art = null,
                    coverArt = { _, _ -> null },
                    onBack = {},
                    onAlbumClick = { opened += it.id },
                    onAlbumLongClick = { menus += it },
                    topSongs = topSongs,
                    likedSongs = likedSongs,
                )
            }
        }
    }

    @Test
    fun theNewestOwnAlbumShowsAboveTheDiscographyAndOpensTheAlbum() {
        render(
            ArtistAlbums(
                albumCredits = listOf(album("old", "Blonde on Blonde", 1966), album("new", "Rough and Rowdy Ways", 2020)),
                appearances = listOf(album("guest", "Guest Spot", 2024)),
            ),
        )

        val card = composeRule.onNodeWithTag(LATEST_RELEASE_TAG)
        card.assertIsDisplayed()
        composeRule.onNodeWithText("Latest release").assertIsDisplayed()
        card.assert(hasText("Rough and Rowdy Ways")).assert(hasText("2020")).assert(hasText("10 songs"))
        val cardBottom = card.getBoundsInRoot().bottom
        val shelfTop = composeRule.onNodeWithTag("discography-Album").getBoundsInRoot().top
        assertTrue("the card sits above the discography", cardBottom <= shelfTop)

        card.performClick()
        assertEquals(listOf("new"), opened)
    }

    @Test
    fun longPressingTheCardOpensTheAlbumMenu() {
        render(ArtistAlbums(albumCredits = listOf(album("new", "Rough and Rowdy Ways", 2020))))

        composeRule.onNodeWithTag(LATEST_RELEASE_TAG).performTouchInput { longClick() }

        assertEquals("new", (menus.single() as MenuTarget.Album).album.id)
    }

    @Test
    fun theCardIsHiddenWhenNoAlbumHasAYear() {
        render(ArtistAlbums(albumCredits = listOf(album("a", "Undated", null))))

        composeRule.onNodeWithTag(LATEST_RELEASE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Latest release").assertDoesNotExist()
    }

    @Test
    fun appearancesAloneDoNotMakeALatestRelease() {
        render(ArtistAlbums(appearances = listOf(album("guest", "Guest Spot", 2024))))

        composeRule.onNodeWithTag(LATEST_RELEASE_TAG).assertDoesNotExist()
    }

    @Test
    fun likedSongsAppearBelowTopSongsAndDispatchPlaybackFromTheShelf() {
        val song = Song(1, "liked", null, "ar-1", "Liked track", null, "Bob Dylan", 60, null, null, 1)
        val plays = mutableListOf<Pair<Int, Boolean>>()
        render(
            albums = ArtistAlbums(),
            topSongs = { Text("Top songs") },
            likedSongs = {
                ArtistSongsSection(
                    songs = listOf(AlbumSongItem(song, null)),
                    title = R.string.artist_liked_songs,
                    carouselTag = LIKED_SONGS_TAG,
                    coverArt = { _, _ -> null },
                    playingSongId = null,
                    onPlay = { index, shuffled -> plays += index to shuffled },
                    onLongClick = {},
                )
            },
        )
        val top = composeRule.onNodeWithText("Top songs").getBoundsInRoot()
        val liked = composeRule.onNodeWithText("Liked songs").assertIsDisplayed().getBoundsInRoot()
        assertTrue(liked.top > top.bottom)
        composeRule.onNodeWithContentDescription("Play Liked songs").performClick()
        composeRule.onNodeWithContentDescription("Shuffle Liked songs").performClick()
        composeRule.onNodeWithText("Liked track").performClick()
        assertEquals(listOf(0 to false, 0 to true, 0 to false), plays)
    }

    private fun album(
        id: String,
        name: String,
        year: Long?,
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar-1",
        name = name,
        albumArtist = "Bob Dylan",
        created = 0,
        coverArt = null,
        genre = null,
        year = year,
        starred = null,
        songCount = 10,
    )
}
