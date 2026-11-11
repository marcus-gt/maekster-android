package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.net.HeaderValidator
import com.subtracks.ui.components.DismissOnRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddSourceRoute(
    onSaved: () -> Unit,
    onBack: (() -> Unit)?,
    sourceId: Long? = null,
    dismissals: Flow<Unit> = emptyFlow(),
    viewModel: AddSourceViewModel = koinViewModel(key = sourceId?.toString() ?: "new") { parametersOf(sourceId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AddSourceScreen(
        state = state,
        onNameChange = viewModel::setName,
        onAddressChange = viewModel::setAddress,
        onUsernameChange = viewModel::setUsername,
        onPasswordChange = viewModel::setPassword,
        onTokenAuthChange = viewModel::setTokenAuth,
        onAddHeader = viewModel::addHeader,
        onRemoveHeader = viewModel::removeHeader,
        onHeaderNameChange = viewModel::setHeaderName,
        onHeaderValueChange = viewModel::setHeaderValue,
        onTest = viewModel::testConnection,
        onSave = { viewModel.save(onSaved) },
        onDelete = { viewModel.delete(onSaved) },
        onBack = onBack,
        dismissals = dismissals,
    )
}

/**
 * The server's custom headers, for a reverse proxy that wants an access token. Values are hidden until revealed.
 * A row's problem shows as soon as there is one; [showErrors] also marks rows still empty after a save attempt.
 */
@Composable
private fun CustomHeaders(
    headers: List<HeaderDraft>,
    showErrors: Boolean,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onNameChange: (Long, String) -> Unit,
    onValueChange: (Long, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_servers_headers_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_servers_headers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val names = headers.map { it.name.trim().lowercase() }
        headers.forEachIndexed { index, header ->
            val duplicate = header.name.isNotBlank() && names.indexOf(names[index]) != index
            HeaderRow(
                header = header,
                nameError = headerNameError(header, duplicate, showErrors)?.let { stringResource(it) },
                valueError =
                    if (HeaderValidator.isValidValue(header.value.trim())) null else stringResource(R.string.header_error_invalid_value),
                onRemove = { onRemove(header.id) },
                onNameChange = { onNameChange(header.id, it) },
                onValueChange = { onValueChange(header.id, it) },
            )
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_servers_headers_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun headerNameError(
    header: HeaderDraft,
    duplicate: Boolean,
    showErrors: Boolean,
): Int? {
    val name = header.name.trim()
    return when {
        name.isEmpty() -> if (showErrors && header.value.isNotBlank()) R.string.header_error_invalid_name else null
        !HeaderValidator.isValidName(name) -> R.string.header_error_invalid_name
        HeaderValidator.isReserved(name) -> R.string.header_error_reserved
        duplicate -> R.string.header_error_duplicate
        else -> null
    }
}

@Composable
private fun HeaderRow(
    header: HeaderDraft,
    nameError: String?,
    valueError: String?,
    onRemove: () -> Unit,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = header.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.settings_servers_headers_name)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.settings_servers_headers_remove, header.name),
                )
            }
        }
        OutlinedTextField(
            value = header.value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.settings_servers_headers_value)) },
            singleLine = true,
            isError = valueError != null,
            supportingText = valueError?.let { { Text(it) } },
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription =
                            stringResource(
                                if (revealed) R.string.settings_servers_headers_hide else R.string.settings_servers_headers_show,
                            ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    state: AddSourceState,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTokenAuthChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dismissals: Flow<Unit> = emptyFlow(),
    onAddHeader: () -> Unit = {},
    onRemoveHeader: (Long) -> Unit = {},
    onHeaderNameChange: (Long, String) -> Unit = { _, _ -> },
    onHeaderValueChange: (Long, String) -> Unit = { _, _ -> },
) {
    val nameFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    var confirmingDelete by remember { mutableStateOf(false) }
    DismissOnRequest(dismissals) { confirmingDelete = false }
    LaunchedEffect(state.nameError, state.addressError) {
        when {
            state.nameError -> nameFocus.requestFocus()
            state.addressError -> addressFocus.requestFocus()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) {
                            stringResource(R.string.settings_servers_actions_edit)
                        } else {
                            stringResource(R.string.settings_servers_actions_add)
                        },
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.navigation_back),
                            )
                        }
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(
                            onClick = { confirmingDelete = true },
                            enabled = state.canDelete && !state.busy,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.settings_servers_actions_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding(),
            ) {
                if (state.message != null) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    OutlinedButton(onClick = onTest, enabled = !state.busy) {
                        Text(stringResource(R.string.settings_servers_actions_test_connection))
                    }
                    Button(onClick = onSave, enabled = !state.busy) {
                        Text(stringResource(R.string.settings_servers_actions_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (state.isEditing) {
                    stringResource(R.string.edit_server_intro)
                } else {
                    stringResource(R.string.add_server_intro)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isEditing && !state.canDelete) {
                Text(
                    stringResource(R.string.active_server_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.settings_servers_fields_name)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.required)) }) else null,
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.settings_servers_fields_address)) },
                placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
                singleLine = true,
                isError = state.addressError,
                supportingText = if (state.addressError) ({ Text(stringResource(R.string.required)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().focusRequester(addressFocus),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.settings_servers_fields_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.settings_servers_fields_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = state.needsSignIn && state.password.isEmpty(),
                supportingText =
                    if (state.needsSignIn) {
                        { Text(stringResource(R.string.settings_servers_fields_password_reenter)) }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.token_authentication))
                    Text(
                        stringResource(R.string.token_authentication_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.useTokenAuth,
                    onCheckedChange = onTokenAuthChange,
                    colors =
                        SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
            }
            CustomHeaders(
                headers = state.headers,
                showErrors = state.headersError,
                onAdd = onAddHeader,
                onRemove = onRemoveHeader,
                onNameChange = onHeaderNameChange,
                onValueChange = onHeaderValueChange,
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_server_title)) },
            text = { Text(stringResource(R.string.delete_server_body, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.actions_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.actions_cancel)) }
            },
        )
    }
}
