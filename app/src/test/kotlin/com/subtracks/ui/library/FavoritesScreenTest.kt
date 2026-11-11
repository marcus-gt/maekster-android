package com.subtracks.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Playlist
import com.subtracks.ui.home.FavoriteKind
import com.subtracks.ui.home.HomeListRequest
import com.subtracks.ui.home.HomeListScreen
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settled =
        LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

    private fun awaitText(text: String) =
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theSwitcherShowsFavoriteAlbumsAndArtistsAndAnEmptyListSaysWhatToDo() {
        val album =
            Album(
                sourceId = 1,
                id = "al1",
                artistId = "ar1",
                name = "Blue Train",
                albumArtist = "John Coltrane",
                created = 0,
                coverArt = null,
                genre = null,
                year = 1957,
                starred = 1,
                songCount = 5,
            )
        val artist = Artist(sourceId = 1, id = "ar1", name = "Lee Morgan", albumCount = 1, starred = 1)
        composeRule.setContent {
            MaeksterTheme {
                var kind by remember { mutableStateOf(FavoriteKind.Albums) }
                HomeListScreen(
                    request = HomeListRequest(title = "Favorites", favorites = kind),
                    albums = remember { flowOf(PagingData.from(listOf(album), settled)) }.collectAsLazyPagingItems(),
                    artists = remember { flowOf(PagingData.from(listOf(artist), settled)) }.collectAsLazyPagingItems(),
                    songs = remember { flowOf(PagingData.empty<AlbumSongItem>(settled)) }.collectAsLazyPagingItems(),
                    playlists = remember { flowOf(PagingData.empty<Playlist>(settled)) }.collectAsLazyPagingItems(),
                    topBarExtra = { FavoriteKindSelector(selected = kind, onSelect = { kind = it }) },
                )
            }
        }

        awaitText("Blue Train")

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.resources_artist_name)).performClick()
        awaitText("Lee Morgan")

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.search_songs)).performClick()
        awaitText(composeRule.activity.getString(R.string.favorites_empty))
    }
}
