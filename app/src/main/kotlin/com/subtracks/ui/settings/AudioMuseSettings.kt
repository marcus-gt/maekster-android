package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.subtracks.R
import com.subtracks.data.audiomuse.AudioMuseConfiguration
import com.subtracks.data.audiomuse.AudioMuseConfigurationState
import com.subtracks.data.audiomuse.normalizeAudioMuseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

enum class AudioMuseSettingsError { Invalid, Save }

data class AudioMuseForm(
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: AudioMuseSettingsError? = null,
)

class AudioMuseSettingsViewModel(
    private val sourceId: Long,
    private val configuration: AudioMuseConfiguration,
) : ViewModel() {
    val state = configuration.observe(sourceId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableForm = MutableStateFlow(AudioMuseForm())
    val form = mutableForm.asStateFlow()

    fun save(
        url: String,
        token: String,
        keepToken: Boolean,
    ) {
        if (mutableForm.value.busy || state.value == null) return
        mutableForm.value = AudioMuseForm(busy = true)
        viewModelScope.launch {
            try {
                val normalized = normalizeAudioMuseUrl(url)
                val secret =
                    if (keepToken) {
                        val previous = configuration.connection(sourceId)
                        require(previous != null && previous.url == normalized)
                        previous.token.orEmpty()
                    } else {
                        token
                    }
                configuration.save(sourceId, normalized, secret)
                mutableForm.value = AudioMuseForm(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                mutableForm.value = AudioMuseForm(error = AudioMuseSettingsError.Invalid)
            } catch (_: Exception) {
                mutableForm.value = AudioMuseForm(error = AudioMuseSettingsError.Save)
            }
        }
    }

    fun disconnect() {
        if (mutableForm.value.busy) return
        mutableForm.value = AudioMuseForm(busy = true)
        viewModelScope.launch {
            try {
                configuration.disconnect(sourceId)
                mutableForm.value = AudioMuseForm(saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableForm.value = AudioMuseForm(error = AudioMuseSettingsError.Save)
            }
        }
    }
}

@Composable
fun AudioMuseSettingsRoute(
    sourceId: Long,
    serverName: String,
    onDismiss: () -> Unit,
) {
    val owner =
        remember(sourceId) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val model: AudioMuseSettingsViewModel = koinViewModel { parametersOf(sourceId) }
        val state by model.state.collectAsStateWithLifecycle()
        val form by model.form.collectAsStateWithLifecycle()
        androidx.compose.runtime.key(sourceId) {
            state?.let { AudioMuseSettingsDialog(serverName, it, form, model::save, model::disconnect, onDismiss) }
        }
    }
}

@Composable
fun AudioMuseSettingsDialog(
    serverName: String,
    state: AudioMuseConfigurationState,
    form: AudioMuseForm,
    onSave: (String, String, Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember(state.url) { mutableStateOf(state.url.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var keepToken by remember(state.url, state.hasToken, state.tokenUnreadable) {
        mutableStateOf(state.hasToken && !state.tokenUnreadable)
    }
    var confirming by remember { mutableStateOf(false) }
    val sameUrl = runCatching { normalizeAudioMuseUrl(url) }.getOrNull() == state.url
    val canKeep = sameUrl && state.hasToken && !state.tokenUnreadable
    LaunchedEffect(form.saved, state.url, state.hasToken, state.tokenUnreadable) {
        if (form.saved) {
            token = ""
            keepToken = state.hasToken && !state.tokenUnreadable
        }
    }

    AlertDialog(
        onDismissRequest = { if (!form.busy) onDismiss() },
        title = { Text(stringResource(R.string.audiomuse_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(serverName)
                Text(stringResource(R.string.audiomuse_disclosure))
                if (state.tokenUnreadable) Text(stringResource(R.string.audiomuse_unreadable))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        keepToken = false
                    },
                    label = { Text(stringResource(R.string.audiomuse_url)) },
                    enabled = !form.busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("audiomuse-url"),
                )
                if (url.trim().startsWith("http://", ignoreCase = true)) Text(stringResource(R.string.audiomuse_http))
                if (canKeep) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = keepToken,
                            onCheckedChange = { keepToken = it },
                            enabled = !form.busy,
                            modifier = Modifier.testTag("audiomuse-keep-token"),
                        )
                        Text(stringResource(R.string.audiomuse_keep_token), modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.audiomuse_token)) },
                    enabled = !form.busy && !(canKeep && keepToken),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("audiomuse-token"),
                )
                Text(stringResource(R.string.audiomuse_token_hint))
                if (form.saved) Text(stringResource(R.string.audiomuse_saved))
                form.error?.let {
                    Text(
                        stringResource(
                            if (it ==
                                AudioMuseSettingsError.Invalid
                            ) {
                                R.string.audiomuse_invalid
                            } else {
                                R.string.audiomuse_save_failed
                            },
                        ),
                    )
                }
                if (state.url != null) {
                    TextButton(onClick = { confirming = true }, enabled = !form.busy) {
                        Text(stringResource(R.string.audiomuse_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token, canKeep && keepToken) }, enabled = url.isNotBlank() && !form.busy) {
                Text(stringResource(R.string.audiomuse_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !form.busy) { Text(stringResource(R.string.action_done)) } },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.audiomuse_remove_title)) },
            text = { Text(stringResource(R.string.audiomuse_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDisconnect()
                }) { Text(stringResource(R.string.audiomuse_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.actions_cancel)) } },
        )
    }
}
