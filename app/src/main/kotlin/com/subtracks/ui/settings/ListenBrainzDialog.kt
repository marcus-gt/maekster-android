package com.subtracks.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.data.listenbrainz.ConnectResult
import com.subtracks.data.listenbrainz.ListenBrainzException
import com.subtracks.data.listenbrainz.ListenBrainzRepository
import com.subtracks.data.listenbrainz.ListenBrainzState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

const val LISTENBRAINZ_TOKEN_TAG = "listenbrainz-token"
const val LISTENBRAINZ_ENABLED_TAG = "listenbrainz-enabled"

enum class ListenBrainzConnectError { InvalidToken, Network, Server }

data class ListenBrainzForm(
    val busy: Boolean = false,
    val error: ListenBrainzConnectError? = null,
)

class ListenBrainzSettingsViewModel(
    private val repository: ListenBrainzRepository,
) : ViewModel() {
    val state: StateFlow<ListenBrainzState> = repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, ListenBrainzState())

    private val _form = MutableStateFlow(ListenBrainzForm())
    val form: StateFlow<ListenBrainzForm> = _form.asStateFlow()

    fun connect(
        token: String,
        root: String,
    ) {
        if (token.isBlank() || _form.value.busy) return
        _form.value = ListenBrainzForm(busy = true)
        viewModelScope.launch {
            val error =
                when (val result = repository.connect(token, root)) {
                    is ConnectResult.Connected -> {
                        null
                    }

                    ConnectResult.InvalidToken -> {
                        ListenBrainzConnectError.InvalidToken
                    }

                    is ConnectResult.Failed -> {
                        if (result.error.transient && result.error !is ListenBrainzException.Http) {
                            ListenBrainzConnectError.Network
                        } else {
                            ListenBrainzConnectError.Server
                        }
                    }
                }
            _form.value = ListenBrainzForm(error = error)
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun disconnect() {
        viewModelScope.launch { repository.disconnect() }
    }
}

/** The Playback row's summary: off, on or paused with the account, and how many listens wait. */
fun listenBrainzSummary(
    resources: Resources,
    state: ListenBrainzState,
): String {
    val base =
        when {
            !state.connected -> resources.getString(R.string.listenbrainz_off)
            state.tokenUnreadable -> resources.getString(R.string.listenbrainz_sign_in_again)
            state.enabled -> resources.getString(R.string.listenbrainz_on, state.username.orEmpty())
            else -> resources.getString(R.string.listenbrainz_paused, state.username.orEmpty())
        }
    return if (state.pending > 0) {
        resources.getQuantityString(R.plurals.listenbrainz_waiting, state.pending, base, state.pending)
    } else {
        base
    }
}

@Composable
fun ListenBrainzSettingsRoute(
    onDismiss: () -> Unit,
    viewModel: ListenBrainzSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    ListenBrainzDialog(
        state = state,
        form = form,
        onConnect = viewModel::connect,
        onEnabledChange = viewModel::setEnabled,
        onDisconnect = viewModel::disconnect,
        onDismiss = onDismiss,
    )
}

@Composable
fun ListenBrainzDialog(
    state: ListenBrainzState,
    form: ListenBrainzForm,
    onConnect: (token: String, root: String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val signedIn = state.connected && !state.tokenUnreadable
    var token by rememberSaveable { mutableStateOf("") }
    var root by rememberSaveable(state.root) { mutableStateOf(state.root) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.listenbrainz_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (signedIn) {
                    Text(
                        stringResource(R.string.listenbrainz_connected_as, state.username.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.listenbrainz_send_listens), modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = onEnabledChange,
                            modifier = Modifier.testTag(LISTENBRAINZ_ENABLED_TAG),
                        )
                    }
                    if (state.pending > 0) {
                        Text(
                            pluralStringResource(R.plurals.listenbrainz_pending, state.pending, state.pending),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.listenbrainz_duplicates), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { confirmDisconnect = true }) { Text(stringResource(R.string.listenbrainz_disconnect)) }
                } else {
                    Text(stringResource(R.string.listenbrainz_about), style = MaterialTheme.typography.bodyMedium)
                    if (state.tokenUnreadable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.listenbrainz_token_unreadable),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.listenbrainz_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag(LISTENBRAINZ_TOKEN_TAG),
                    )
                    OutlinedTextField(
                        value = root,
                        onValueChange = { root = it },
                        label = { Text(stringResource(R.string.listenbrainz_server)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.listenbrainz_token_hint), style = MaterialTheme.typography.bodySmall)
                    form.error?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(connectErrorText(error)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.listenbrainz_duplicates), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (signedIn) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            } else {
                TextButton(
                    onClick = { onConnect(token, root) },
                    enabled = token.isNotBlank() && !form.busy,
                ) {
                    Text(stringResource(if (form.busy) R.string.listenbrainz_connecting else R.string.listenbrainz_connect))
                }
            }
        },
        dismissButton = {
            if (!signedIn) TextButton(onClick = onDismiss) { Text(stringResource(R.string.actions_cancel)) }
        },
    )

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.listenbrainz_disconnect_title)) },
            text = { Text(stringResource(R.string.listenbrainz_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnect()
                }) { Text(stringResource(R.string.listenbrainz_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.actions_cancel)) }
            },
        )
    }
}

private fun connectErrorText(error: ListenBrainzConnectError): Int =
    when (error) {
        ListenBrainzConnectError.InvalidToken -> R.string.listenbrainz_error_invalid
        ListenBrainzConnectError.Network -> R.string.listenbrainz_error_network
        ListenBrainzConnectError.Server -> R.string.listenbrainz_error_server
    }

@Composable
internal fun listenBrainzSummary(state: ListenBrainzState): String = listenBrainzSummary(LocalResources.current, state)
