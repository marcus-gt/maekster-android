package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import com.subtracks.ui.components.SECTION_OPEN_ARROW_TAG
import com.subtracks.ui.discover.QueueBuilderActions
import com.subtracks.ui.discover.QueueBuilderSection
import com.subtracks.ui.discover.QueueBuilderState
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSectionArrowsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun headersOpenTheirSectionWithAnArrowAndASpecificLabel() {
        val opened = mutableListOf<HomeSection>()
        renderHome(onMore = { opened += it })

        rule.onNodeWithText("More").assertDoesNotExist()
        header("Your favorite albums").assertOpens("Open Your favorite albums")
        header("Your favorite albums").performClick()
        header("Your favorite songs").performScrollTo().assertOpens("Open Your favorite songs")
        header("Your favorite songs").performClick()
        assertEquals(listOf(HomeSection.FavoriteAlbums, HomeSection.RecentlyStarredSongs), opened)

        rule.onNodeWithText("Artist picks").performScrollTo().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        assertEquals("Only headers with a destination get an arrow", 2, arrows())
    }

    @Test
    fun arrowFollowsTheTrailingEdgeInRightToLeft() {
        renderHome(rtl = true)
        val title = rule.onNodeWithText("Your favorite albums", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = arrowIn("Your favorite albums")
        assertTrue("The arrow sits before the title in RTL", arrow.right <= title.left)
    }

    @Test
    fun arrowStaysVisibleAtLargeFont() {
        renderHome(fontScale = 2f)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("Your favorite albums", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = arrowIn("Your favorite albums")
        assertTrue(arrow.left >= title.right && arrow.right <= root.right)
        rule.onRoot().captureRoboImage("src/test/screenshots/home_section_arrows_2.0.png")
    }

    @Test
    fun queueBuilderHeaderUsesTheSameArrowAndTarget() {
        var opened = 0
        setContent { QueueBuilderSection(QueueBuilderState(loading = false), null, false, QueueBuilderActions(open = { opened++ })) }
        header("Queue Builder").assertOpens("Open Queue Builder")
        header("Queue Builder").performClick()
        assertEquals(1, opened)
        assertEquals(1, arrows())
    }

    private fun header(title: String) = rule.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))

    private fun SemanticsNodeInteraction.assertOpens(label: String) {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assert(SemanticsMatcher("click label is $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label })
        val height = fetchSemanticsNode().boundsInRoot.height
        val minimum = with(rule.density) { 48 * density }
        assertTrue("Header touch target is $height px, needs $minimum", height >= minimum - 0.5f)
    }

    private fun arrows() = rule.onAllNodesWithTag(SECTION_OPEN_ARROW_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun arrowIn(title: String): Rect {
        val header = header(title).fetchSemanticsNode().boundsInRoot
        return rule
            .onAllNodesWithTag(SECTION_OPEN_ARROW_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .single { it.center.y in header.top..header.bottom }
    }

    private fun renderHome(
        rtl: Boolean = false,
        fontScale: Float = 1f,
        onMore: (HomeSection) -> Unit = {},
    ) {
        setContent(rtl, fontScale) {
            HomeScreen(
                feed = HomeFeed(favoriteAlbums = listOf(album()), recentlyStarredSongs = listOf(song())),
                coverArt = { _, _ -> null },
                playingSongId = null,
                artistPicks =
                    listOf(
                        Artist(sourceId = 1, id = "ar", name = "Picked artist", coverArt = null, albumCount = 1, starred = null),
                    ),
                onMore = onMore,
            )
        }
    }

    private fun setContent(
        rtl: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaeksterTheme { Surface { content() } }
            }
        }
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
            songCount = 3,
        )

    private fun song() =
        AlbumSongItem(
            song =
                Song(
                    sourceId = 1,
                    id = "song",
                    albumId = "al",
                    artistId = "ar",
                    title = "Favorite song",
                    album = "Favorite record",
                    artist = "Artist",
                    duration = 185,
                    track = 1,
                    disc = 1,
                    starred = 1,
                    genre = null,
                ),
            coverArt = null,
        )
}
