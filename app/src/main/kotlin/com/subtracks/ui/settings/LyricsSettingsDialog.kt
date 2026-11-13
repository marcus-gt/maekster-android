package com.subtracks.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.lyrics.LyricsSource
import com.subtracks.data.prefs.LYRICS_TIMING_OFFSET_LIMIT_MS
import com.subtracks.data.prefs.LyricsPreferences
import kotlin.math.roundToInt

@Composable
internal fun lyricsSourceLabel(source: LyricsSource): String =
    stringResource(
        when (source) {
            LyricsSource.Auto -> R.string.lyrics_source_auto
            LyricsSource.Server -> R.string.lyrics_source_server
            LyricsSource.Lrclib -> R.string.lyrics_source_lrclib
        },
    )

@Composable
internal fun LyricsSettingsDialog(
    settings: LyricsPreferences,
    onSource: (LyricsSource) -> Unit,
    onAllowLrclib: (Boolean) -> Unit,
    onShowCurrentLine: (Boolean) -> Unit,
    onTimingOffset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timingLabel = stringResource(R.string.lyrics_timing_correction)
    var offset by remember(settings.timingOffsetMs) { mutableFloatStateOf(settings.timingOffsetMs.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.lyrics_source_title), style = MaterialTheme.typography.titleSmall)
                Column {
                    LyricsSource.entries.forEach { source ->
                        val enabled = source != LyricsSource.Lrclib || settings.allowLrclib
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = settings.source == source,
                                        enabled = enabled,
                                        role = Role.RadioButton,
                                        onClick = { onSource(source) },
                                    ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lyricsSourceLabel(source), Modifier.weight(1f))
                            RadioButton(selected = settings.source == source, onClick = null, enabled = enabled)
                        }
                    }
                }
                LyricsSwitchRow(stringResource(R.string.lyrics_allow_lrclib), settings.allowLrclib, onAllowLrclib)
                Text(stringResource(R.string.lyrics_privacy_disclosure), style = MaterialTheme.typography.bodySmall)
                LyricsSwitchRow(stringResource(R.string.lyrics_current_line_setting), settings.showCurrentLine, onShowCurrentLine)
                Text(stringResource(R.string.lyrics_timing_correction), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.lyrics_timing_value, offset.roundToInt()))
                Slider(
                    modifier = Modifier.semantics { contentDescription = timingLabel },
                    value = offset,
                    onValueChange = { offset = it },
                    onValueChangeFinished = { onTimingOffset(offset.roundToInt()) },
                    valueRange = -LYRICS_TIMING_OFFSET_LIMIT_MS.toFloat()..LYRICS_TIMING_OFFSET_LIMIT_MS.toFloat(),
                    steps = 59,
                )
                Text(stringResource(R.string.lyrics_timing_description), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    offset = 0f
                    onTimingOffset(0)
                }) {
                    Text(stringResource(R.string.settings_replay_gain_preamp_reset))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_close)) }
        },
    )
}

@Composable
private fun LyricsSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
