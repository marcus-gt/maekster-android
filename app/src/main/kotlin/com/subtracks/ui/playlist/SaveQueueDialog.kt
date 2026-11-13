package com.subtracks.ui.playlist

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import org.koin.compose.viewmodel.koinViewModel

const val SAVE_QUEUE_FIELD_TAG = "save-queue-field"

/** The save-queue dialog for one opening ([key]); its capture and ViewModel go when it closes. */
@Composable
fun SaveQueueRoute(
    key: Any,
    onDismiss: () -> Unit,
) {
    PlaylistSheetScope(key) {
        val viewModel: SaveQueueViewModel = koinViewModel(key = "save-queue-$key")
        SaveQueueContent(viewModel, onDismiss)
    }
}

@Composable
internal fun SaveQueueContent(
    viewModel: SaveQueueViewModel,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDismiss() } }
    SaveQueueDialog(ui = ui, onNameChange = viewModel::setName, onSave = viewModel::save, onDismiss = onDismiss)
}

/** Asks for the new playlist's name; after a failure the name is fixed and the action retries the same capture. */
@Composable
fun SaveQueueDialog(
    ui: SaveQueueUi,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // While saving the dialog stays: closing it would cancel the save between batches.
    AlertDialog(
        onDismissRequest = { if (!ui.busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !ui.busy, dismissOnBackPress = !ui.busy),
        title = { Text(stringResource(R.string.save_queue_title)) },
        text = {
            val note = ui.error?.resolve(resources) ?: if (ui.retained) stringResource(R.string.save_queue_retained) else null
            OutlinedTextField(
                value = ui.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.rename_playlist_name)) },
                singleLine = true,
                enabled = !ui.busy && !ui.retained,
                isError = ui.error != null,
                supportingText = note?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ui.canSave) onSave() }),
                modifier = Modifier.focusRequester(focus).testTag(SAVE_QUEUE_FIELD_TAG),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = ui.canSave) {
                Text(stringResource(if (ui.retained) R.string.save_queue_retry else R.string.save_queue_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !ui.busy) { Text(stringResource(R.string.rename_playlist_cancel)) }
        },
    )
}
