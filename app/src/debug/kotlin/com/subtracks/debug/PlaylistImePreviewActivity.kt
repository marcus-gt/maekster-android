package com.subtracks.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.subtracks.data.model.Playlist
import com.subtracks.ui.playlist.AddToPlaylistSheet
import com.subtracks.ui.playlist.AddToPlaylistUi
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** A debug-only, in-memory sheet for manual keyboard checks without server or repository access. */
class PlaylistImePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var ui by remember { mutableStateOf(AddToPlaylistUi()) }
            val playlists =
                remember {
                    (1..30).map { Playlist(-1, "preview-$it", "Test playlist $it", null, null, 0, 0) }
                }
            val data =
                remember(ui.query) {
                    MutableStateFlow(PagingData.from(playlists.filter { it.name.contains(ui.query, ignoreCase = true) }))
                }
            MaeksterTheme {
                AddToPlaylistSheet(
                    ui = ui,
                    destinations = data.collectAsLazyPagingItems(),
                    coverArt = { null },
                    onQuery = { ui = ui.copy(query = it) },
                    onNewName = { ui = ui.copy(newName = it) },
                    onCreate = { finish() },
                    onPick = { finish() },
                    onResolveDuplicates = {},
                    onDismissDuplicates = {},
                    onDismiss = { finish() },
                )
            }
        }
    }
}
