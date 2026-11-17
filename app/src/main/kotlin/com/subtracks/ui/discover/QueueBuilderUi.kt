package com.subtracks.ui.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.mix.QueueBuilderColumn
import com.subtracks.data.mix.QueueBuilderPicks
import kotlin.math.roundToInt

/** What the Queue Builder card and page can do; grouped so screens and tests pass one value. */
class QueueBuilderActions(
    val open: () -> Unit = {},
    val toggleMain: (String) -> Unit = {},
    val tapMainInCard: (String) -> Unit = {},
    val togglePill: (pill: String, main: String) -> Unit = { _, _ -> },
    val toggleDecade: (Int) -> Unit = {},
    val clearGenres: () -> Unit = {},
    val clearDecades: () -> Unit = {},
    val reset: () -> Unit = {},
    val setLength: (Int) -> Unit = {},
    val build: () -> Unit = {},
)

/**
 * Fixed colours that tell the main genres apart; sub-genres use their main genre's tint. Every tint keeps white
 * text readable (WCAG AA) on a picked chip. Other, decades and moods stay neutral, so colour always means genre.
 */
object GenreTint {
    private val tints =
        mapOf(
            "Electronic" to 0xFF4B5BD6,
            "Rock" to 0xFFC0392B,
            "Jazz" to 0xFFA6611C,
            "Hip Hop" to 0xFF7D3C98,
            "Funk / Soul" to 0xFFB03A70,
            "Pop" to 0xFFC2185B,
            "Folk, World, & Country" to 0xFF5E6E22,
            "Blues" to 0xFF1F5FA8,
            "Reggae" to 0xFF2E7D32,
            "Classical" to 0xFF566573,
            "Latin" to 0xFF00796B,
            "Stage & Screen" to 0xFF455A64,
            "Non-Music" to 0xFF6B6B6B,
            "Brass & Military" to 0xFF7B5E52,
            "Children's" to 0xFF00737E,
        )

    fun of(main: String): Color? = tints[main]?.let { Color(it) }
}

/** "A", "A and B", "A, B and C". */
@Composable
private fun joinNames(names: List<String>): String =
    when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(R.string.queue_builder_list_two, names.dropLast(1).joinToString(", "), names.last())
    }

/** One line for the card: picked genres in column order (a whole genre by name, else its picked sub-genres), then decades newest first. */
@Composable
fun queueBuilderSummary(state: QueueBuilderState): String {
    val genres =
        state.index.columns.flatMap { column ->
            if (column.main in state.selection.mains) {
                listOf(column.main)
            } else {
                column.pills.filter { it.key in state.selection.tags }.map { it.name }
            }
        }
    val decades =
        state.selection.decades
            .sortedDescending()
            .map { stringResource(R.string.queue_builder_decade, it) }
    val parts = listOf(joinNames(genres), joinNames(decades)).filter { it.isNotEmpty() }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.queue_builder_anything) }
}

/** A Queue Builder chip: tinted by its main genre when it has one, the accent when picked otherwise. */
@Composable
private fun BuilderChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color?,
    modifier: Modifier = Modifier,
    count: Long? = null,
    expanded: Boolean = false,
    prominent: Boolean = false,
    shape: Shape = CircleShape,
) {
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = if (count == null) title else stringResource(R.string.queue_builder_chip, title, count),
                style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = if (prominent || count != null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        },
        trailingIcon =
            if (expanded) {
                { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
        shape = shape,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = tint?.copy(alpha = 0.16f) ?: neutral,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconColor = MaterialTheme.colorScheme.onSurface,
                selectedContainerColor = tint ?: MaterialTheme.colorScheme.primary,
                selectedLabelColor = if (tint != null) Color.White else MaterialTheme.colorScheme.onPrimary,
                selectedTrailingIconColor = if (tint != null) Color.White else MaterialTheme.colorScheme.onPrimary,
            ),
        // An outline only on unpicked tinted chips, so the genre reads even before anything is picked.
        border = BorderStroke(1.dp, if (tint != null && !selected) tint.copy(alpha = 0.55f) else Color.Transparent),
        modifier = modifier.heightIn(min = 40.dp),
    )
}

/**
 * The Queue Builder on Discover: pick and play without opening the page. Rows: main genres, the focused genre's
 * sub-genres, decades; then a summary of the picks.
 */
@Composable
fun QueueBuilderSection(
    state: QueueBuilderState,
    focusedMain: String?,
    building: Boolean,
    actions: QueueBuilderActions,
    modifier: Modifier = Modifier,
) {
    val rowPadding = PaddingValues(horizontal = 20.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val openLabel = stringResource(R.string.queue_builder_open)
            Row(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClickLabel = openLabel, onClick = actions.open)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.queue_builder_title), style = MaterialTheme.typography.titleLarge)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
            if (!state.selection.isEmpty) {
                FilledTonalIconButton(onClick = actions.reset) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.queue_builder_reset))
                }
            }
            FilledIconButton(onClick = actions.build, enabled = state.canBuild && !building, modifier = Modifier.size(48.dp)) {
                if (building) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.queue_builder_play_queue))
                }
            }
        }
        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.index.columns, key = { it.main }) { column ->
                val showing = stringResource(R.string.queue_builder_showing_subgenres)
                BuilderChip(
                    title = column.main,
                    selected = state.isPicked(column.main),
                    onClick = { actions.tapMainInCard(column.main) },
                    tint = GenreTint.of(column.main),
                    expanded = focusedMain == column.main,
                    modifier = if (focusedMain == column.main) Modifier.semantics { stateDescription = showing } else Modifier,
                )
            }
        }
        val focused = focusedMain?.let { state.index.column(it) }
        if (focused != null && focused.pills.isNotEmpty()) {
            // Keyed by genre, so each genre's sub-genres start scrolled to the left.
            key(focused.main) {
                LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(focused.pills, key = { it.key }) { pill ->
                        BuilderChip(
                            title = pill.name,
                            selected = state.isSelected(pill.key, focused.main),
                            onClick = { actions.togglePill(pill.key, focused.main) },
                            tint = GenreTint.of(focused.main),
                        )
                    }
                }
            }
        }
        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                state.index.decades.keys
                    .sortedDescending(),
                key = { it },
            ) { decade ->
                BuilderChip(
                    title = stringResource(R.string.queue_builder_decade, decade),
                    selected = decade in state.selection.decades,
                    onClick = { actions.toggleDecade(decade) },
                    tint = null,
                )
            }
        }
        Text(
            queueBuilderSummary(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (state.offline) {
            Text(
                stringResource(R.string.queue_builder_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
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

/**
 * The full Queue Builder: genre columns (a main genre over its scrolling sub-genres), decades, length, then the
 * match count and Build.
 */
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
            TextButton(onClick = actions.reset, enabled = !state.selection.isEmpty) {
                Text(stringResource(R.string.queue_builder_reset))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .testTag("queueBuilderPicks"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(R.string.queue_builder_genres, clearable = state.selection.hasGenre, onClear = actions.clearGenres)
            if (state.index.columns.isEmpty()) {
                Hint(stringResource(R.string.queue_builder_genres_empty))
            } else {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.queue_builder_filter_genres)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                )
                val columns = visibleColumns(state, filter.trim())
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("queueBuilderColumns"),
                ) {
                    items(columns, key = { it.main }) { column -> GenreColumn(column, state, actions) }
                }
                Hint(
                    stringResource(
                        if (state.selection.hasGenre) R.string.queue_builder_genres_some else R.string.queue_builder_genres_none,
                    ),
                )
            }
            SectionHeader(R.string.queue_builder_decades, clearable = state.selection.decades.isNotEmpty(), onClear = actions.clearDecades)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                state.index.decades.keys.sortedDescending().forEach { decade ->
                    BuilderChip(
                        title = stringResource(R.string.queue_builder_decade, decade),
                        count = state.index.decades.getValue(decade),
                        selected = decade in state.selection.decades,
                        onClick = { actions.toggleDecade(decade) },
                        tint = null,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            Hint(
                stringResource(
                    if (state.selection.decades.isEmpty()) R.string.queue_builder_decades_none else R.string.queue_builder_decades_some,
                ),
            )
            Text(
                stringResource(R.string.queue_builder_length),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val lengthLabel = pluralStringResource(R.plurals.queue_builder_songs, state.length, state.length)
            val lengthTitle = stringResource(R.string.queue_builder_length)
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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

/** Columns whose genre or some sub-genre matches [query]; picked sub-genres stay visible so they can be unpicked. */
private fun visibleColumns(
    state: QueueBuilderState,
    query: String,
): List<QueueBuilderColumn> {
    if (query.isEmpty()) return state.index.columns
    return state.index.columns.mapNotNull { column ->
        if (column.main.contains(query, ignoreCase = true)) return@mapNotNull column
        val pills = column.pills.filter { it.name.contains(query, ignoreCase = true) || it.key in state.selection.tags }
        if (pills.isEmpty()) null else column.copy(pills = pills)
    }
}

/** One main genre: its title picks the whole genre, and its sub-genres scroll below it. */
@Composable
private fun GenreColumn(
    column: QueueBuilderColumn,
    state: QueueBuilderState,
    actions: QueueBuilderActions,
) {
    val tint = GenreTint.of(column.main)
    Column(
        Modifier
            .width(196.dp)
            .background((tint ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BuilderChip(
            title = column.main,
            count = column.songCount,
            selected = column.main in state.selection.mains,
            onClick = { actions.toggleMain(column.main) },
            tint = tint,
            prominent = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier
                .height(272.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            column.pills.forEach { pill ->
                BuilderChip(
                    title = pill.name,
                    count = pill.songCount,
                    selected = state.isSelected(pill.key, column.main),
                    onClick = { actions.togglePill(pill.key, column.main) },
                    tint = tint,
                    shape = RoundedCornerShape(12.dp),
                )
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
    Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (clearable) TextButton(onClick = onClear) { Text(stringResource(R.string.queue_builder_clear)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
