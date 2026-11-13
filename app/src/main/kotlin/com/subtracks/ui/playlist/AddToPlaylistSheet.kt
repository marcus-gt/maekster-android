package com.subtracks.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Playlist
import com.subtracks.data.repo.SourceRepository
import com.subtracks.ui.components.CoverArt
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

const val ADD_TO_PLAYLIST_TAG = "add-to-playlist"

fun addToPlaylistRowTag(id: String) = "add-to-playlist-$id"

@Composable
fun AddToPlaylistRoute(
    request: AddToPlaylistRequest,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = koinViewModel(key = request.toString()) { parametersOf(request) },
    sources: SourceRepository = koinInject(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val destinations = viewModel.destinations.collectAsLazyPagingItems()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDismiss() } }
    AddToPlaylistSheet(
        ui = ui,
        destinations = destinations,
        coverArt = { sources.coverArt(it, thumbnail = true) },
        onQuery = viewModel::setQuery,
        onNewName = viewModel::setNewName,
        onCreate = viewModel::create,
        onPick = viewModel::addTo,
        onResolveDuplicates = viewModel::resolveDuplicates,
        onDismissDuplicates = viewModel::dismissDuplicates,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    ui: AddToPlaylistUi,
    destinations: LazyPagingItems<Playlist>,
    coverArt: (String?) -> CoverArtRef?,
    onQuery: (String) -> Unit,
    onNewName: (String) -> Unit,
    onCreate: () -> Unit,
    onPick: (Playlist) -> Unit,
    onResolveDuplicates: (skip: Boolean) -> Unit,
    onDismissDuplicates: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag(ADD_TO_PLAYLIST_TAG),
    ) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().testTag("playlist-destinations")) {
            item {
                Text(
                    stringResource(R.string.add_to_playlist_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ui.newName,
                        onValueChange = onNewName,
                        label = { Text(stringResource(R.string.add_to_playlist_new_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (ui.canCreate) onCreate() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(onClick = onCreate, enabled = ui.canCreate) {
                        if (ui.busy == AddToPlaylistUi.CREATING) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.add_to_playlist_create))
                        }
                    }
                }
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(R.string.add_to_playlist_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ui.error?.let { error ->
                    val resources = LocalResources.current
                    Column(
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(error.resolve(resources), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.add_to_playlist_retry_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (ui.recent.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.add_to_playlist_recent)) }
                ui.recent.forEach { playlist ->
                    item(key = "recent-${playlist.id}") { DestinationRow(playlist, coverArt, ui.busy, onPick) }
                }
                item { SectionLabel(stringResource(R.string.add_to_playlist_all)) }
            }
            items(destinations.itemCount, key = destinations.itemKey { it.id }) { index ->
                destinations[index]?.let { DestinationRow(it, coverArt, ui.busy, onPick) }
            }
            if (destinations.itemCount == 0 && ui.recent.isEmpty() && ui.query.isBlank()) {
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
    ui.duplicatesIn?.let { playlist ->
        AlertDialog(
            onDismissRequest = onDismissDuplicates,
            title = { Text(stringResource(R.string.add_to_playlist_duplicates_title)) },
            text = { Text(stringResource(R.string.add_to_playlist_duplicates_body, playlist.name)) },
            confirmButton = { TextButton(onClick = { onResolveDuplicates(true) }) { Text(stringResource(R.string.add_to_playlist_skip)) } },
            dismissButton = {
                TextButton(onClick = { onResolveDuplicates(false) }) { Text(stringResource(R.string.add_to_playlist_add_anyway)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DestinationRow(
    playlist: Playlist,
    coverArt: (String?) -> CoverArtRef?,
    busy: String?,
    onPick: (Playlist) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag(addToPlaylistRowTag(playlist.id))
                .clickable(enabled = busy == null) { onPick(playlist) }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CoverArt(
                ref = coverArt(playlist.coverArt),
                name = playlist.name,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (busy == playlist.id) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.resources_song_count, playlist.songCount.toInt(), playlist.songCount.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
