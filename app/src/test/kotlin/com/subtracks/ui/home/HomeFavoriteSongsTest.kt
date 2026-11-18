package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeFavoriteSongsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun columnsOfThreeAtNormalFont() = longList(1f)

    @Test fun columnsOfThreeAtLargeFont() = longList(2f)

    private fun longList(fontScale: Float) {
        var played: Song? = null
        render(songs(7), fontScale) { played = it }

        val first = bounds("Favorite 1")
        val second = bounds("Favorite 2")
        val third = bounds("Favorite 3")
        assertEquals(first.left, third.left, 1f)
        assertTrue(second.top >= first.bottom && third.top >= second.bottom)
        val fourth = rule.onNodeWithText("Favorite 4").fetchSemanticsNode().boundsInRoot
        assertTrue("The fourth favorite starts a new column", fourth.left > first.right - 1f)
        assertEquals(first.top, fourth.top, 1f)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_favorite_songs_$fontScale.png")

        rule.onNodeWithTag(FAVORITE_SONGS_TAG).performScrollToIndex(2)
        rule.onNodeWithText("Favorite 7").performClick()
        assertEquals("song-7", played?.id)
    }

    @Test
    fun shortListIsOneFullWidthColumn() {
        var played: Song? = null
        render(songs(2), 1f) { played = it }

        val first = bounds("Favorite 1")
        val second = bounds("Favorite 2")
        assertTrue(second.top >= first.bottom)
        assertEquals(first.left, second.left, 1f)
        rule.onNodeWithText("Favorite 2").performClick()
        assertEquals("song-2", played?.id)
    }

    private fun bounds(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun render(
        songs: List<AlbumSongItem>,
        fontScale: Float,
        onPlay: (Song) -> Unit,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaeksterTheme {
                    Surface {
                        HomeScreen(
                            feed = HomeFeed(recentlyStarredSongs = songs),
                            coverArt = { _, _ -> null },
                            playingSongId = null,
                            onPlayStarred = onPlay,
                        )
                    }
                }
            }
        }
    }

    private fun songs(count: Int) =
        (1..count).map { index ->
            AlbumSongItem(
                song =
                    Song(
                        sourceId = 1,
                        id = "song-$index",
                        albumId = "album",
                        artistId = "artist",
                        title = "Favorite $index",
                        album = "Album",
                        artist = "Artist",
                        duration = 185,
                        track = index.toLong(),
                        disc = 1,
                        starred = 1000L - index,
                        genre = null,
                    ),
                coverArt = null,
            )
        }
}
