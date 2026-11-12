package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.ui.home.HomeSection

@Composable
internal fun HomeLayoutDialog(
    layout: HomeLayout,
    onSave: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(layout.normalized()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_home_sections)) },
        text = {
            LazyColumn {
                item {
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(title, modifier = Modifier.weight(1f))
                            Switch(
                                checked = shelf !in draft.hidden,
                                onCheckedChange = { draft = draft.show(shelf, it) },
                                modifier = Modifier.semantics { contentDescription = title },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = index > 0,
                                onClick = { draft = draft.move(shelf, -1) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "$title: " + upLabel },
                            ) { Text(stringResource(R.string.settings_home_up)) }
                            TextButton(
                                enabled = index < draft.order.lastIndex,
                                onClick = { draft = draft.move(shelf, 1) },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "$title: " + downLabel },
                            ) { Text(stringResource(R.string.settings_home_down)) }
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
