package com.subtracks.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import kotlin.math.roundToInt

/** One line for the card: picked genres in index order, then decades newest first, e.g. "Jazz, Techno · 1990s". */
@Composable
fun queueBuilderSummary(state: QueueBuilderState): String {
    val genres =
        state.index.genres
            .filter { it.key in state.selection.genres }
            .map { it.name }
    val decades =
        state.selection.decades
            .sortedDescending()
            .map { stringResource(R.string.queue_builder_decade, it) }
    val parts = listOfNotNull(genres.takeIf { it.isNotEmpty() }?.joinToString(), decades.takeIf { it.isNotEmpty() }?.joinToString())
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.queue_builder_anything) }
}

@Composable
fun QueueBuilderCard(
    state: QueueBuilderState,
    building: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
                Text(stringResource(R.string.queue_builder_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(queueBuilderSummary(state), style = MaterialTheme.typography.bodyLarge)
            if (state.offline) {
                Text(
                    stringResource(R.string.queue_builder_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                FilledTonalButton(onClick = onPlay, enabled = state.canBuild && !building) {
                    if (building) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    }
                    Text(
                        stringResource(if (building) R.string.queue_builder_building else R.string.queue_builder_play),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.queue_builder_edit)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBuilderSheet(
    state: QueueBuilderState,
    building: Boolean,
    actions: QueueBuilderActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        QueueBuilderContent(state, building, actions)
    }
}

/** What the picker can do; grouped so the sheet and its tests take one value. */
class QueueBuilderActions(
    val toggleGenre: (String) -> Unit = {},
    val toggleDecade: (Int) -> Unit = {},
    val clearGenres: () -> Unit = {},
    val clearDecades: () -> Unit = {},
    val reset: () -> Unit = {},
    val setLength: (Int) -> Unit = {},
    val build: () -> Unit = {},
)

/** The picker itself, without the sheet: genres, decades, length, then the match count and Build. */
@Composable
fun QueueBuilderContent(
    state: QueueBuilderState,
    building: Boolean,
    actions: QueueBuilderActions,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.queue_builder_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = actions.reset, enabled = state.selection != QueueBuilderSelection()) {
                Text(stringResource(R.string.queue_builder_reset))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("queueBuilderPicks"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(R.string.queue_builder_genres, clearable = state.selection.genres.isNotEmpty(), onClear = actions.clearGenres)
            if (state.index.genres.isEmpty()) {
                Hint(stringResource(R.string.queue_builder_genres_empty))
            } else {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.queue_builder_filter_genres)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val query = filter.trim()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.index.genres
                        .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.key in state.selection.genres }
                        .forEach { genre ->
                            FilterChip(
                                selected = genre.key in state.selection.genres,
                                onClick = { actions.toggleGenre(genre.key) },
                                label = { Text(stringResource(R.string.queue_builder_chip, genre.name, genre.songCount)) },
                            )
                        }
                }
                Hint(
                    stringResource(
                        if (state.selection.genres.isEmpty()) R.string.queue_builder_genres_none else R.string.queue_builder_genres_some,
                    ),
                )
            }
            SectionHeader(R.string.queue_builder_decades, clearable = state.selection.decades.isNotEmpty(), onClear = actions.clearDecades)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.index.decades.keys.sortedDescending().forEach { decade ->
                    FilterChip(
                        selected = decade in state.selection.decades,
                        onClick = { actions.toggleDecade(decade) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.queue_builder_chip,
                                    stringResource(R.string.queue_builder_decade, decade),
                                    state.index.decades.getValue(decade),
                                ),
                            )
                        },
                    )
                }
            }
            Hint(
                stringResource(
                    if (state.selection.decades.isEmpty()) R.string.queue_builder_decades_none else R.string.queue_builder_decades_some,
                ),
            )
            Text(stringResource(R.string.queue_builder_length), style = MaterialTheme.typography.titleMedium)
            val lengthLabel = pluralStringResource(R.plurals.queue_builder_songs, state.length, state.length)
            val lengthTitle = stringResource(R.string.queue_builder_length)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Slider(
                    value = state.length.toFloat(),
                    onValueChange = { actions.setLength(it.roundToInt()) },
                    valueRange = QueueBuilderPicks.MIN_LENGTH.toFloat()..QueueBuilderPicks.MAX_LENGTH.toFloat(),
                    steps = (QueueBuilderPicks.MAX_LENGTH - QueueBuilderPicks.MIN_LENGTH) / QueueBuilderPicks.LENGTH_STEP - 1,
                    modifier =
                        Modifier.weight(1f).semantics {
                            contentDescription = lengthTitle
                            stateDescription = lengthLabel
                        },
                )
                Text(lengthLabel, style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.matchCount?.let {
                Text(
                    pluralStringResource(R.plurals.queue_builder_matching, it.toInt(), it.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = actions.build, enabled = state.canBuild && !building, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (building) R.string.queue_builder_building else R.string.queue_builder_build))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: Int,
    clearable: Boolean,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (clearable) TextButton(onClick = onClear) { Text(stringResource(R.string.queue_builder_clear)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
