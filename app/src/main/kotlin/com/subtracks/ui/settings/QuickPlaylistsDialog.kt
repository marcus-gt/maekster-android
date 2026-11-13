package com.subtracks.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.data.model.Playlist
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.QuickPlaylistRepository
import com.subtracks.ui.playlist.QuickPlaylistPickerRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

const val QUICK_PLAYLISTS_SETTINGS_TAG = "quick-playlists-settings"

fun quickPlaylistRemoveTag(id: String) = "quick-playlist-remove-$id"

/** Settings for the heart's long-press playlists on one server. */
class QuickPlaylistsSettingsViewModel(
    private val sourceId: Long,
    private val quick: QuickPlaylistRepository,
    private val preferences: UserPreferences,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = quick.quickPlaylists(sourceId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val alsoFavorite: StateFlow<Boolean> =
        preferences.quickPlaylistsAlsoFavorite().stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setOrder(ids: List<String>) {
        viewModelScope.launch { quick.setOrder(sourceId, ids) }
    }

    fun remove(playlist: Playlist) {
        viewModelScope.launch { quick.remove(sourceId, playlist.id) }
    }

    fun setAlsoFavorite(enabled: Boolean) {
        viewModelScope.launch { preferences.setQuickPlaylistsAlsoFavorite(enabled) }
    }
}

@Composable
internal fun QuickPlaylistsSettingsRoute(
    sourceId: Long,
    offline: Boolean,
    onDismiss: () -> Unit,
) {
    val viewModel: QuickPlaylistsSettingsViewModel = koinViewModel(key = "quick-settings-$sourceId") { parametersOf(sourceId) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val alsoFavorite by viewModel.alsoFavorite.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    QuickPlaylistsDialog(
        playlists = playlists,
        alsoFavorite = alsoFavorite,
        canChoose = !offline,
        onOrder = viewModel::setOrder,
        onRemove = viewModel::remove,
        onAlsoFavorite = viewModel::setAlsoFavorite,
        onChoose = { choosing = true },
        onDismiss = onDismiss,
    )
    if (choosing) QuickPlaylistPickerRoute(sourceId = sourceId, onDismiss = { choosing = false })
}

/**
 * The quick playlists in their order: drag a handle (or use its up and down actions) to reorder, remove one,
 * choose more (online only, as on iOS), and whether a quick add also adds the song to Favorites.
 */
@Composable
internal fun QuickPlaylistsDialog(
    playlists: List<Playlist>,
    alsoFavorite: Boolean,
    canChoose: Boolean,
    onOrder: (List<String>) -> Unit,
    onRemove: (Playlist) -> Unit,
    onAlsoFavorite: (Boolean) -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    // While dragging the order lives here and is stored once the drag ends, so each step doesn't wait for Room.
    var draft by remember(playlists) { mutableStateOf(playlists) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = draft.indexOfFirst { it.id == from.key }
            val toIndex = draft.indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                draft = draft.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }

    fun step(
        index: Int,
        by: Int,
    ): Boolean {
        val moved = draft.toMutableList().apply { add(index + by, removeAt(index)) }
        draft = moved
        onOrder(moved.map { it.id })
        return true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(QUICK_PLAYLISTS_SETTINGS_TAG),
        title = { Text(stringResource(R.string.quick_playlists_title)) },
        text = {
            LazyColumn(state = listState) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.quick_playlists_picker_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (draft.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.quick_playlists_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(draft, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val upLabel = stringResource(R.string.settings_home_up)
                    val downLabel = stringResource(R.string.settings_home_down)
                    ReorderableItem(reorderState, key = playlist.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = stringResource(R.string.settings_home_reorder, playlist.name),
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .draggableHandle(onDragStopped = { onOrder(draft.map { it.id }) })
                                        .semantics {
                                            customActions =
                                                buildList {
                                                    if (index > 0) add(CustomAccessibilityAction(upLabel) { step(index, -1) })
                                                    if (index <
                                                        draft.lastIndex
                                                    ) {
                                                        add(CustomAccessibilityAction(downLabel) { step(index, 1) })
                                                    }
                                                }
                                        },
                            )
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemove(playlist) }, modifier = Modifier.testTag(quickPlaylistRemoveTag(playlist.id))) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.quick_playlists_remove, playlist.name),
                                )
                            }
                        }
                    }
                }
                item(key = "choose") {
                    TextButton(onClick = onChoose, enabled = canChoose) { Text(stringResource(R.string.quick_playlists_choose_playlists)) }
                }
                item(key = "favorite") {
                    val label = stringResource(R.string.quick_playlists_also_favorite)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onAlsoFavorite(!alsoFavorite) }.padding(vertical = 8.dp),
                    ) {
                        Text(label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = alsoFavorite,
                            onCheckedChange = onAlsoFavorite,
                            modifier = Modifier.semantics { contentDescription = label },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_playlists_done)) } },
    )
}
