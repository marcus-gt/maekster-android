package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.prefs.DiscoverLayout
import com.subtracks.data.prefs.DiscoverShelf
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.home.HomeSection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun HomeShelf.title(): Int =
    when (this) {
        HomeShelf.Pinned -> R.string.home_pinned
        HomeShelf.YourPlaylists -> R.string.home_section_your_playlists
        HomeShelf.QueueBuilder -> R.string.queue_builder_title
        HomeShelf.ArtistPicks -> R.string.home_section_artist_picks
        else -> HomeSection.valueOf(name).title
    }

internal fun DiscoverShelf.title(): Int =
    when (this) {
        DiscoverShelf.FreshReleases -> R.string.fresh_releases_title
        DiscoverShelf.Stations -> R.string.discover_stations
        DiscoverShelf.SmartShuffle -> R.string.smart_shuffle_title
        DiscoverShelf.QueueBuilder -> R.string.queue_builder_title
        DiscoverShelf.Moods -> R.string.discover_moods
        DiscoverShelf.SongPath -> R.string.song_path_title
    }

private data class EditorRow(
    val key: String,
    val title: Int,
    val shown: Boolean,
    val grid: Boolean?,
)

private fun HomeLayout.rows() =
    normalized().order.map {
        EditorRow(it.name, it.title(), it !in hidden, if (it.supportsGrid) it in grids else null)
    }

private fun DiscoverLayout.rows() = normalized().order.map { EditorRow(it.name, it.title(), it !in hidden, null) }

@Composable
internal fun HomeLayoutDialog(
    layout: HomeLayout,
    onSave: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    LayoutEditor(R.string.settings_home_sections, layout.rows(), HomeLayout().rows(), { rows ->
        onSave(
            HomeLayout(
                rows.map { HomeShelf.valueOf(it.key) },
                rows.filter { !it.shown }.map { HomeShelf.valueOf(it.key) }.toSet(),
                rows.filter { it.grid == true }.map { HomeShelf.valueOf(it.key) }.toSet(),
            ),
        )
    }, onDismiss)
}

@Composable
internal fun DiscoverLayoutDialog(
    layout: DiscoverLayout,
    onSave: (DiscoverLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    LayoutEditor(R.string.settings_discover_sections, layout.rows(), DiscoverLayout().rows(), { rows ->
        onSave(
            DiscoverLayout(
                rows.map { DiscoverShelf.valueOf(it.key) },
                rows.filter { !it.shown }.map { DiscoverShelf.valueOf(it.key) }.toSet(),
            ),
        )
    }, onDismiss)
}

@Composable
private fun LayoutEditor(
    title: Int,
    initial: List<EditorRow>,
    defaults: List<EditorRow>,
    onSave: (List<EditorRow>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Follows the stored layout until the first edit, so a dialog opened before preferences load never saves defaults.
    var edited by remember { mutableStateOf<List<EditorRow>?>(null) }
    val latestInitial by rememberUpdatedState(initial)
    val draft = edited ?: initial
    val listState = rememberLazyListState()

    fun move(
        key: String,
        target: Int,
    ) {
        val current = edited ?: latestInitial
        val from = current.indexOfFirst { it.key == key }
        if (from < 0 || target !in current.indices) return
        edited = current.toMutableList().apply { add(target, removeAt(from)) }
    }
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            move(from.key as String, (edited ?: latestInitial).indexOfFirst { it.key == to.key })
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            LazyColumn(state = listState) {
                item(key = "reset") {
                    TextButton(onClick = { edited = defaults }) { Text(stringResource(R.string.settings_home_reset)) }
                }
                itemsIndexed(draft, key = { _, row -> row.key }) { index, row ->
                    val label = stringResource(row.title)
                    val up = stringResource(R.string.settings_home_up)
                    val down = stringResource(R.string.settings_home_down)
                    val reorder = stringResource(R.string.settings_home_reorder, label)
                    ReorderableItem(reorderState, key = row.key) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                reorder,
                                Modifier.size(48.dp).draggableHandle().semantics {
                                    customActions =
                                        buildList {
                                            if (index > 0) {
                                                add(
                                                    CustomAccessibilityAction(up) {
                                                        move(row.key, index - 1)
                                                        true
                                                    },
                                                )
                                            }
                                            if (index < draft.lastIndex) {
                                                add(
                                                    CustomAccessibilityAction(down) {
                                                        move(row.key, index + 1)
                                                        true
                                                    },
                                                )
                                            }
                                        }
                                },
                            )
                            Text(label, Modifier.weight(1f))
                            if (row.grid != null) {
                                val layoutLabel =
                                    stringResource(
                                        R.string.settings_home_layout,
                                        stringResource(if (row.grid) R.string.settings_home_grid else R.string.settings_home_carousel),
                                    )
                                IconButton(
                                    onClick = { edited = draft.map { if (it.key == row.key) it.copy(grid = !row.grid) else it } },
                                    modifier = Modifier.semantics { contentDescription = "$label: $layoutLabel" },
                                ) {
                                    Icon(if (row.grid) Icons.Rounded.GridView else Icons.Rounded.ViewCarousel, null)
                                }
                            }
                            Switch(
                                row.shown,
                                { shown -> edited = draft.map { if (it.key == row.key) it.copy(shown = shown) else it } },
                                Modifier.semantics { contentDescription = label },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft)
                onDismiss()
            }) { Text(stringResource(R.string.settings_home_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) } },
    )
}
