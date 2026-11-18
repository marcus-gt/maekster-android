package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.discover.DiscoverState
import com.subtracks.ui.discover.songPathSourceId
import com.subtracks.ui.settings.HomeLayoutDialog
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSongPathTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun songPathAppearsOnlyWhenShownAndAvailableAfterTheQueueBuilder() {
        var layout by mutableStateOf(HomeLayout())
        var available by mutableStateOf(true)
        rule.setContent {
            MaeksterTheme {
                Surface {
                    HomeScreen(
                        feed = HomeFeed(favoriteAlbums = listOf(album())),
                        coverArt = { _, _ -> null },
                        playingSongId = null,
                        layout = layout,
                        queueBuilderContent = { Text("Queue Builder slot", Modifier.testTag("queueBuilderSlot")) },
                        songPathContent = if (available) ({ Text("Song Path slot", Modifier.testTag("songPathSlot")) }) else null,
                    )
                }
            }
        }

        rule.onNodeWithTag("songPathSlot").assertDoesNotExist()

        rule.runOnIdle { layout = HomeLayout().show(HomeShelf.SongPath, true) }
        val queueBuilder = rule.onNodeWithTag("queueBuilderSlot").fetchSemanticsNode().boundsInRoot
        val songPath = rule.onNodeWithTag("songPathSlot").fetchSemanticsNode().boundsInRoot
        val favorites =
            rule
                .onNodeWithText("Your favorite albums")
                .performScrollTo()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(songPath.top >= queueBuilder.bottom)
        assertTrue(
            favorites.top >=
                rule
                    .onNodeWithTag("songPathSlot")
                    .fetchSemanticsNode()
                    .boundsInRoot.bottom,
        )

        // Offline or without AudioMuse the host passes no card: the shelf leaves no gap or error.
        rule.runOnIdle { available = false }
        rule.onNodeWithTag("songPathSlot").assertDoesNotExist()
        rule.onNodeWithText("Your favorite albums").assertExists()
    }

    @Test
    fun editorListsSongPathOffByDefaultAndSavesTheToggle() {
        var saved: HomeLayout? = null
        rule.setContent { MaeksterTheme { HomeLayoutDialog(HomeLayout(), { saved = it }, {}) } }
        rule
            .onNodeWithContentDescription("Song Path")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
        rule.onNodeWithContentDescription("Song Path: Layout: Carousel").assertDoesNotExist()
        rule.onNodeWithText("Save").performClick()
        assertTrue(HomeShelf.SongPath in saved!!.visible)
    }

    @Test
    fun songPathNeedsAConnectionAudioMuseAndAServer() {
        assertEquals(1L, DiscoverState(offline = false, moods = true, sourceId = 1).songPathSourceId())
        assertNull(DiscoverState(offline = true, moods = true, sourceId = 1).songPathSourceId())
        assertNull(DiscoverState(offline = false, moods = false, sourceId = 1).songPathSourceId())
        assertNull(DiscoverState(offline = false, moods = true, sourceId = null).songPathSourceId())
    }

    private fun album() =
        Album(
            sourceId = 1,
            id = "al",
            artistId = null,
            name = "Favorite record",
            albumArtist = "Artist",
            created = 0,
            coverArt = null,
            genre = null,
            year = null,
            starred = 1,
            songCount = 1,
        )
}
