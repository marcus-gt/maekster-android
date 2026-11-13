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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

const val RENAME_PLAYLIST_FIELD_TAG = "rename-playlist-field"

/** The rename dialog for one opening; its draft and ViewModel go when it closes. */
@Composable
fun RenamePlaylistRoute(
    request: RenamePlaylistRequest,
    onDismiss: () -> Unit,
) {
    PlaylistSheetScope(request) {
        val viewModel: RenamePlaylistViewModel = koinViewModel(key = request.toString()) { parametersOf(request) }
        RenamePlaylistContent(viewModel, onDismiss)
    }
}

@Composable
internal fun RenamePlaylistContent(
    viewModel: RenamePlaylistViewModel,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDismiss() } }
    RenamePlaylistDialog(ui = ui, onNameChange = viewModel::setName, onRename = viewModel::rename, onDismiss = onDismiss)
}

/** Prefilled with the current name, all of it selected, so typing replaces it. */
@Composable
fun RenamePlaylistDialog(
    ui: RenamePlaylistUi,
    onNameChange: (String) -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(ui.name, selection = TextRange(0, ui.name.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // While the rename is in flight the dialog stays: closing it would cancel the write before the mirror catches up.
    AlertDialog(
        onDismissRequest = { if (!ui.busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !ui.busy, dismissOnBackPress = !ui.busy),
        title = { Text(stringResource(R.string.rename_playlist_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    onNameChange(it.text)
                },
                label = { Text(stringResource(R.string.rename_playlist_name)) },
                singleLine = true,
                enabled = !ui.busy,
                isError = ui.error != null,
                supportingText = ui.error?.let { error -> { Text(error.resolve(resources)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ui.canRename) onRename() }),
                modifier = Modifier.focusRequester(focus).testTag(RENAME_PLAYLIST_FIELD_TAG),
            )
        },
        confirmButton = {
            TextButton(onClick = onRename, enabled = ui.canRename) { Text(stringResource(R.string.rename_playlist_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !ui.busy) { Text(stringResource(R.string.rename_playlist_cancel)) }
        },
    )
}
