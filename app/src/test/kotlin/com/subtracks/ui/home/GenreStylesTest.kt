package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Playlist
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenreStylesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allAndStylesOpenDistinctCollectionsAtLargeFont() {
        val selection = mutableListOf<String?>()
        showStyles(listOf(AlbumCollectionSummary("Indie Rock", 2, null)), selection::add)
        composeRule.onNodeWithText("All Rock").performClick()
        composeRule.onNodeWithText("Styles").assertIsDisplayed()
        composeRule.onNodeWithText("Indie Rock").performClick()
        assertEquals(listOf(null, "Indie Rock"), selection)
    }

    @Test
    fun allRemainsAvailableWithoutStyles() {
        val selection = mutableListOf<String?>()
        showStyles(emptyList(), selection::add)
        composeRule.onNodeWithText("All Rock").performClick()
        composeRule.onNodeWithText("Styles").assertDoesNotExist()
        assertEquals(listOf<String?>(null), selection)
    }

    private fun showStyles(
        styles: List<AlbumCollectionSummary>,
        onClick: (String?) -> Unit,
    ) {
        val stylePages = flowOf(PagingData.from(styles))
        val albums = flowOf(PagingData.empty<Album>())
        val artists = flowOf(PagingData.empty<Artist>())
        val songs = flowOf(PagingData.empty<AlbumSongItem>())
        val playlists = flowOf(PagingData.empty<Playlist>())
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MaeksterTheme {
                    HomeListScreen(
                        request = HomeListRequest(title = "Rock", showGenreStyles = true),
                        albums = albums.collectAsLazyPagingItems(),
                        artists = artists.collectAsLazyPagingItems(),
                        songs = songs.collectAsLazyPagingItems(),
                        playlists = playlists.collectAsLazyPagingItems(),
                        genreStyles = stylePages.collectAsLazyPagingItems(),
                        onGenreStyleClick = onClick,
                    )
                }
            }
        }
    }
}
