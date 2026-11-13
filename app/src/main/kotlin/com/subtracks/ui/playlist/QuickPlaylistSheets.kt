package com.subtracks.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.ui.components.MenuItem
import com.subtracks.ui.components.artistSubtitle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

const val QUICK_PLAYLIST_MENU_TAG = "quick-playlist-menu"
const val QUICK_PLAYLIST_PICKER_TAG = "quick-playlist-picker"

fun quickPlaylistPickerRowTag(id: String) = "quick-playlist-picker-$id"

/** The heart's long-press menu for one opening; its ViewModel goes when it closes. */
@Composable
fun QuickPlaylistMenuRoute(
    song: Song,
    onDismiss: () -> Unit,
    onChoose: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    PlaylistSheetScope(song) {
        val viewModel: QuickPlaylistMenuViewModel = koinViewModel(key = "quick-${song.sourceId}-${song.id}") { parametersOf(song) }
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        QuickPlaylistMenuSheet(
            song = song,
            playlists = playlists,
            onAdd = { playlist ->
                viewModel.add(playlist)
                onDismiss()
            },
            onChoose = onChoose,
            onAddToPlaylist = { onAddToPlaylist(song) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * "Add to <name>" for each quick playlist, or "Choose quick playlists…" while there are none, then the full
 * add-to-playlist sheet. [playlists] is null while loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlaylistMenuSheet(
    song: Song,
    playlists: List<Playlist>?,
    onAdd: (Playlist) -> Unit,
    onChoose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag(QUICK_PLAYLIST_MENU_TAG),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 8.dp)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                song.artistSubtitle()?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            playlists?.forEach { playlist ->
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.quick_playlist_add, playlist.name)) {
                    onAdd(playlist)
                }
            }
            if (playlists?.isEmpty() == true) {
                MenuItem(Icons.Rounded.Bolt, stringResource(R.string.quick_playlists_choose), onClick = onChoose)
            }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), onClick = onAddToPlaylist)
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** The picker for one opening on [sourceId]. */
@Composable
fun QuickPlaylistPickerRoute(
    sourceId: Long,
    onDismiss: () -> Unit,
) {
    PlaylistSheetScope("quick-picker-$sourceId") {
        val viewModel: QuickPlaylistPickerViewModel = koinViewModel(key = "quick-picker-$sourceId") { parametersOf(sourceId) }
        val chosen by viewModel.chosen.collectAsStateWithLifecycle()
        QuickPlaylistPickerSheet(
            destinations = viewModel.destinations.collectAsLazyPagingItems(),
            chosen = chosen,
            onToggle = viewModel::toggle,
            onDismiss = onDismiss,
        )
    }
}

/** The user's own playlists by name, ticked when quick; tapping one adds it at the end or takes it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlaylistPickerSheet(
    destinations: LazyPagingItems<Playlist>,
    chosen: Set<String>,
    onToggle: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag(QUICK_PLAYLIST_PICKER_TAG),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.quick_playlists_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_playlists_done)) }
                }
                Text(
                    stringResource(R.string.quick_playlists_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            items(destinations.itemCount, key = destinations.itemKey { it.id }) { index ->
                destinations[index]?.let { playlist ->
                    val isChosen = playlist.id in chosen
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag(quickPlaylistPickerRowTag(playlist.id))
                                .semantics { selected = isChosen }
                                .clickable { onToggle(playlist) }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isChosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (destinations.itemCount == 0) {
                item {
                    Text(
                        stringResource(R.string.add_to_playlist_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}
