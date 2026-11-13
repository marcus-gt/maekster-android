package com.subtracks.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The delete confirmation for one opening; [onDeleted] runs once the playlist is gone. */
@Composable
fun DeletePlaylistRoute(
    request: DeletePlaylistRequest,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    PlaylistSheetScope(request) {
        val viewModel: DeletePlaylistViewModel = koinViewModel(key = request.toString()) { parametersOf(request) }
        val ui by viewModel.ui.collectAsStateWithLifecycle()
        LaunchedEffect(viewModel) { viewModel.done.collect { onDeleted() } }
        DeletePlaylistDialog(request = request, ui = ui, onDelete = viewModel::delete, onDismiss = onDismiss)
    }
}

/** As on iOS: with downloads, the user chooses whether they go too; otherwise one Delete. */
@Composable
fun DeletePlaylistDialog(
    request: DeletePlaylistRequest,
    ui: DeletePlaylistUi,
    onDelete: (withDownloads: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val destructive = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    // While the delete is in flight the dialog stays: closing it would cancel the call before the mirror catches up.
    AlertDialog(
        onDismissRequest = { if (!ui.busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !ui.busy, dismissOnBackPress = !ui.busy),
        title = { Text(stringResource(R.string.delete_playlist_title, request.name)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (request.hasDownloads) R.string.delete_playlist_message_downloads else R.string.delete_playlist_message,
                    ),
                )
                ui.error?.let { error ->
                    Text(error.resolve(resources), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        // Three choices don't fit one row, so with downloads they stack; otherwise the usual Cancel, Delete row.
        confirmButton = {
            if (request.hasDownloads) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onDelete(false) }, enabled = !ui.busy, colors = destructive) {
                        Text(stringResource(R.string.delete_playlist_only))
                    }
                    TextButton(onClick = { onDelete(true) }, enabled = !ui.busy, colors = destructive) {
                        Text(stringResource(R.string.delete_playlist_and_downloads))
                    }
                    TextButton(onClick = onDismiss, enabled = !ui.busy) { Text(stringResource(R.string.rename_playlist_cancel)) }
                }
            } else {
                TextButton(onClick = { onDelete(false) }, enabled = !ui.busy, colors = destructive) {
                    Text(stringResource(R.string.delete_playlist_confirm))
                }
            }
        },
        dismissButton =
            if (request.hasDownloads) {
                null
            } else {
                { TextButton(onClick = onDismiss, enabled = !ui.busy) { Text(stringResource(R.string.rename_playlist_cancel)) } }
            },
    )
}
