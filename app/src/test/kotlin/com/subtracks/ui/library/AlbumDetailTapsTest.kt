package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.AlbumFacets
import com.subtracks.data.model.CoverArtRef
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
class AlbumDetailTapsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val album =
        Album(
            sourceId = 1,
            id = "al-kid-a",
            artistId = "ar-radiohead",
            name = "Kid A",
            albumArtist = "Radiohead",
            created = 0,
            coverArt = "art-al-kid-a",
            genre = null,
            year = 2000,
            starred = null,
            songCount = 1,
        )

    private fun song(
        id: String,
        duration: Long,
        suffix: String?,
    ) = Song(
        sourceId = 1,
        id = id,
        albumId = album.id,
        artistId = "ar-radiohead",
        title = id,
        album = album.name,
        artist = "Radiohead",
        duration = duration,
        track = null,
        disc = 1,
        starred = null,
        suffix = suffix,
    )

    private fun render(
        onArtistClick: (() -> Unit)? = null,
        songs: List<Song> = emptyList(),
        facets: AlbumFacets = AlbumFacets(),
        onCollectionClick: ((AlbumCollectionKind, String) -> Unit)? = null,
    ) {
        composeRule.setContent {
            MaeksterTheme {
                AlbumDetailScreen(
                    album = album,
                    songs = songs,
                    facets = facets,
                    onCollectionClick = onCollectionClick,
                    coverArt = { id, _ -> id?.let { CoverArtRef(it, "test:$it") } },
                    artwork = null,
                    onBack = {},
                    onSongClick = {},
                    onArtistClick = onArtistClick,
                )
            }
        }
    }

    @Test
    fun theSummaryLineGivesYearSongsAndLengthWithOneBadgePerFormat() {
        render(songs = listOf(song("a", 200, "flac"), song("b", 220, "mp3"), song("c", 300, "FLAC"), song("d", 0, "ogg")))

        composeRule.onNodeWithText("2000 · 4 songs · 12 min").assertExists()
        composeRule.onNodeWithContentDescription("FLAC, lossless").assertExists()
        composeRule.onNodeWithContentDescription("MP3").assertExists()
        // A mixed-format album shows at most two badges.
        composeRule.onAllNodesWithText("OGG").assertCountEquals(0)
        // The year moved from the artist line to the summary.
        composeRule.onAllNodesWithText("Radiohead · 2000").assertCountEquals(0)
    }

    @Test
    fun genreAndLabelChipsOpenTheirCollectionsWithTheStoredNames() {
        val opened = mutableListOf<Pair<AlbumCollectionKind, String>>()
        render(
            facets = AlbumFacets(genres = listOf("Electronic", " Art Rock"), label = "Parlophone"),
            onCollectionClick = { kind, name -> opened += kind to name },
        )

        composeRule.onNodeWithText("Art Rock").performClick()
        composeRule.onNodeWithText("Parlophone").performClick()

        assertEquals(listOf(AlbumCollectionKind.Genre to " Art Rock", AlbumCollectionKind.Label to "Parlophone"), opened)
        composeRule.onAllNodesWithTag("album-chip-genre").assertCountEquals(2)
        // A 32dp capsule with a 48dp touch target.
        composeRule.onNodeWithText("Parlophone").assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("Record label", useUnmergedTree = true).assertExists()
    }

    @Test
    fun anAlbumWithoutGenresOrLabelsHasNoChips() {
        render(onCollectionClick = { _, _ -> })

        composeRule.onAllNodesWithTag("album-chip-genre").assertCountEquals(0)
        composeRule.onAllNodesWithTag("album-chip-label").assertCountEquals(0)
    }

    @Test
    fun tappingTheArtistOpensTheArtist() {
        var opened = false
        render(onArtistClick = { opened = true })

        composeRule.onNodeWithText("Radiohead").performClick()

        assertTrue("the artist name should open the artist", opened)
    }

    @Test
    fun theArtistTargetCoversTheLineAndItsGaps() {
        render(onArtistClick = {})

        composeRule.onNodeWithText("Radiohead").assertHeightIsAtLeast(32.dp)
    }

    @Test
    fun theArtistTargetHugsTheTextRatherThanTheRow() {
        render(onArtistClick = {})

        val target =
            composeRule
                .onNodeWithText("Radiohead")
                .fetchSemanticsNode()
                .boundsInRoot.width
        val screen =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.width

        assertTrue("the target should not span the row", target < screen)
    }

    @Test
    fun theArtistTargetKeepsClearOfTheControls() {
        render(onArtistClick = {})

        val target = composeRule.onNodeWithText("Radiohead").fetchSemanticsNode().boundsInRoot
        val controls = composeRule.onNodeWithContentDescription("Download").fetchSemanticsNode().boundsInRoot
        val gap = controls.top - target.bottom

        assertTrue("expected a clear gap above the controls, was ${gap}px", gap >= with(composeRule.density) { 12.dp.roundToPx() })
    }

    @Test
    fun theAlbumTitleIsNotATapTarget() {
        render(onArtistClick = {})

        // The app bar repeats the album name, so match the hero title by having no click.
        composeRule.onAllNodesWithText("Kid A").filter(hasClickAction().not()).assertCountEquals(1)
    }
}
