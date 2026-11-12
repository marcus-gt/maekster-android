package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.home.HomeSection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun HomeLayoutDialog(
    layout: HomeLayout,
    onSave: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(layout.normalized()) }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            val shelf = draft.order.firstOrNull { it.name == from.key }
            val target = draft.order.indexOfFirst { it.name == to.key }
            if (shelf != null && target >= 0) {
                draft = draft.move(shelf, target - draft.order.indexOf(shelf))
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_home_sections)) },
        text = {
            LazyColumn(state = listState) {
                item(key = "reset") {
                    TextButton(onClick = { draft = HomeLayout() }) { Text(stringResource(R.string.settings_home_reset)) }
                }
                itemsIndexed(draft.order, key = { _, shelf -> shelf.name }) { index, shelf ->
                    val title =
                        stringResource(
                            if (shelf ==
                                HomeShelf.YourPlaylists
                            ) {
                                R.string.home_section_your_playlists
                            } else {
                                HomeSection.valueOf(shelf.name).title
                            },
                        )
                    val upLabel = stringResource(R.string.settings_home_up)
                    val downLabel = stringResource(R.string.settings_home_down)
                    val reorderLabel = stringResource(R.string.settings_home_reorder, title)
                    ReorderableItem(reorderState, key = shelf.name) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    contentDescription = reorderLabel,
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .draggableHandle()
                                            .semantics {
                                                customActions =
                                                    buildList {
                                                        if (index > 0) {
                                                            add(
                                                                CustomAccessibilityAction(upLabel) {
                                                                    draft = draft.move(shelf, -1)
                                                                    true
                                                                },
                                                            )
                                                        }
                                                        if (index <
                                                            draft.order.lastIndex
                                                        ) {
                                                            add(
                                                                CustomAccessibilityAction(downLabel) {
                                                                    draft =
                                                                        draft.move(shelf, 1)
                                                                    ; true
                                                                },
                                                            )
                                                        }
                                                    }
                                            },
                                )
                                Text(title, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = shelf !in draft.hidden,
                                    onCheckedChange = { draft = draft.show(shelf, it) },
                                    modifier = Modifier.semantics { contentDescription = title },
                                )
                            }
                            if (shelf.supportsGrid) {
                                val label =
                                    stringResource(
                                        R.string.settings_home_layout,
                                        stringResource(
                                            if (shelf in
                                                draft.grids
                                            ) {
                                                R.string.settings_home_grid
                                            } else {
                                                R.string.settings_home_carousel
                                            },
                                        ),
                                    )
                                TextButton(
                                    onClick = { draft = draft.grid(shelf, shelf !in draft.grids) },
                                    modifier = Modifier.semantics { contentDescription = "$title: $label" },
                                ) { Text(label) }
                            }
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
