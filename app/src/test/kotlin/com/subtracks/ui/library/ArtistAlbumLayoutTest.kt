package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.ArtistAlbums
import com.subtracks.data.model.ListDownloadStatus
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
@Config(qualifiers = "w400dp-h2400dp")
class ArtistAlbumLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val menus = mutableListOf<MenuTarget>()
    private val shelfPlays = mutableListOf<Pair<List<String>, Boolean>>()
    private val appearancePlays = mutableListOf<Boolean>()
    private val asList = mutableStateOf(false)

    private val albums =
        ArtistAlbums(
            albumCredits = listOf(album("hard-rain", "Hard Rain", 1976, 9), album("unplugged", "MTV Unplugged", 1995, 11)),
            appearances = listOf(album("bangladesh", "The Concert for Bangladesh", 1971, 21, "George Harrison and Friends")),
        )

    private fun render(
        list: Boolean,
        scale: Float = 1f,
        downloads: Map<String, ListDownloadStatus> = emptyMap(),
    ) {
        asList.value = list
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MaeksterTheme {
                    ArtistDetailScreen(
                        artist = Artist(sourceId = 1, id = "ar-1", name = "Bob Dylan", albumCount = 3, starred = null, coverArt = null),
                        artistName = "Bob Dylan",
                        albums = albums,
                        art = null,
                        coverArt = { _, _ -> null },
                        onBack = {},
                        onAlbumClick = { opened += it.id },
                        albumDownloads = downloads,
                        albumsAsList = asList.value,
                        onAlbumLongClick = { menus += it },
                        onPlayShelf = { shelf, shuffled -> shelfPlays += shelf.map { it.id } to shuffled },
                        onPlayAppearances = { appearancePlays += it },
                    )
                }
            }
        }
    }

    @Test
    fun theGridIsTheDefaultAndTheListReplacesItWhenChosen() {
        render(list = false)
        composeRule.onNodeWithText("Hard Rain").assertIsDisplayed()
        composeRule.onNodeWithTag(artistAlbumRowTag("hard-rain")).assertDoesNotExist()
        val gridLeft = composeRule.onNodeWithText("Hard Rain").getBoundsInRoot()
        val gridRight =
            composeRule
                .onNode(
                    hasText("MTV Unplugged") and !hasTestTag(LATEST_RELEASE_TAG) and !hasAnyAncestor(hasTestTag(LATEST_RELEASE_TAG)),
                ).getBoundsInRoot()
        assertEquals("grid cards share a row", gridLeft.top, gridRight.top)

        asList.value = true
        composeRule.waitForIdle()

        val first = composeRule.onNodeWithTag(artistAlbumRowTag("hard-rain")).assertIsDisplayed().getBoundsInRoot()
        val second = composeRule.onNodeWithTag(artistAlbumRowTag("unplugged")).assertIsDisplayed().getBoundsInRoot()
        assertTrue("list rows stack", second.top >= first.bottom)
        assertTrue("list rows fill the width", first.right - first.left > 300.dp)
    }

    @Test
    fun listRowsShowDetailsAndTheAlbumArtistOnlyForAppearances() {
        render(list = true)

        composeRule
            .onNodeWithTag(artistAlbumRowTag("hard-rain"))
            .assert(hasText("Hard Rain"))
            .assert(hasText("1976 · 9 songs"))
        composeRule
            .onNodeWithTag(artistAlbumRowTag("bangladesh"))
            .assert(hasText("The Concert for Bangladesh"))
            .assert(hasText("George Harrison and Friends · 1971 · 21 songs"))
    }

    @Test
    fun listRowsShowTheAlbumDownloadProgress() {
        render(list = true, downloads = mapOf("hard-rain" to ListDownloadStatus(total = 9, downloaded = 3, downloading = 6)))

        composeRule
            .onNodeWithTag(artistAlbumRowTag("hard-rain"))
            .assert(hasAnyDescendant(hasContentDescription("Downloading")) or hasContentDescription("Downloading"))
        composeRule
            .onNodeWithTag(artistAlbumRowTag("unplugged"))
            .assert(!hasAnyDescendant(hasContentDescription("Downloading")) and !hasContentDescription("Downloading"))
    }

    @Test
    fun listRowsKeepTapLongPressAndShelfControls() {
        render(list = true)

        composeRule.onNodeWithTag(artistAlbumRowTag("unplugged")).performClick()
        composeRule.onNodeWithTag(artistAlbumRowTag("bangladesh")).performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Play Albums").performClick()
        composeRule.onNodeWithContentDescription("Shuffle Appears on").performClick()

        assertEquals(listOf("unplugged"), opened)
        assertEquals("bangladesh", (menus.single() as MenuTarget.Album).album.id)
        assertEquals(listOf(listOf("hard-rain", "unplugged") to false), shelfPlays)
        assertEquals(listOf(true), appearancePlays)

        composeRule.onNode(hasText("ALBUMS") and hasClickAction()).performClick()
        composeRule.onNodeWithTag(artistAlbumRowTag("hard-rain")).assertDoesNotExist()
        composeRule.onNodeWithTag(artistAlbumRowTag("bangladesh")).assertExists()
    }

    @Test fun listAtDefaultFont() = renderListAt(1f)

    @Test fun listAtLargeFont() = renderListAt(2f)

    private fun renderListAt(scale: Float) {
        render(list = true, scale = scale)
        val row = composeRule.onNodeWithTag(artistAlbumRowTag("bangladesh")).assertIsDisplayed().getBoundsInRoot()
        val title = composeRule.onNodeWithText("The Concert for Bangladesh", useUnmergedTree = true).getBoundsInRoot()
        val meta = composeRule.onNodeWithText("George Harrison and Friends · 1971 · 21 songs", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the title sits inside the row", title.top >= row.top && title.bottom <= row.bottom)
        assertTrue("details sit below the title, inside the row", meta.top >= title.bottom && meta.bottom <= row.bottom)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/artist_album_list_$scale.png")
    }

    private fun album(
        id: String,
        name: String,
        year: Long,
        songs: Long,
        albumArtist: String = "Bob Dylan",
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar-1",
        name = name,
        albumArtist = albumArtist,
        created = 0,
        coverArt = null,
        genre = null,
        year = year,
        starred = null,
        songCount = songs,
    )
}
