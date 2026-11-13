package com.subtracks.ui.playlist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.subtracks.R
import com.subtracks.UiMessage

@Composable
fun PlaylistMoveRecoveryDialog(
    error: UiMessage,
    busy: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(stringResource(R.string.playlist_move_failed)) },
        text = { Text(error.resolve(LocalResources.current)) },
        confirmButton = {
            TextButton(onClick = onRetry, enabled = !busy && canRetry) { Text(stringResource(R.string.lyrics_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.rename_playlist_cancel)) }
        },
    )
}
