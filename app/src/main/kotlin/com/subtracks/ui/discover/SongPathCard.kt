package com.subtracks.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subtracks.R
import com.subtracks.data.audiomuse.AudioMuseMood
import com.subtracks.data.audiomuse.AudioMusePathEnd
import com.subtracks.data.audiomuse.SongPathBuilder
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.CoverArtRef
import com.subtracks.ui.components.CoverArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal sealed interface SongPathChoice {
    val endpoint: AudioMusePathEnd

    data class Song(
        val id: String,
        val title: String,
        val artist: String?,
        val cover: String?,
    ) : SongPathChoice {
        override val endpoint get() = AudioMusePathEnd.Song(id)
    }

    data class Mood(
        val mood: AudioMuseMood,
    ) : SongPathChoice {
        override val endpoint get() = AudioMusePathEnd.Mood(mood)
    }
}

internal enum class SongPathSide(
    val label: Int,
) {
    From(R.string.song_path_from),
    To(R.string.song_path_to),
}

internal class SongPathDraft {
    private var chosenFrom by mutableStateOf<SongPathChoice?>(null)
    private var usePlaying by mutableStateOf(true)
    var to by mutableStateOf<SongPathChoice?>(null)
        private set
    var length by mutableIntStateOf(SongPathBuilder.DEFAULT_LENGTH)
    var editing by mutableStateOf<SongPathSide?>(null)

    fun from(playing: SongPathChoice.Song?) = if (usePlaying) playing else chosenFrom

    fun choose(
        side: SongPathSide,
        choice: SongPathChoice?,
    ) {
        if (side == SongPathSide.From) {
            chosenFrom = choice
            usePlaying = false
        } else {
            to = choice
        }
        editing = null
    }

    fun swap(playing: SongPathChoice.Song?) {
        val oldFrom = from(playing)
        choose(SongPathSide.From, to)
        to = oldFrom
    }
}

@Composable
internal fun rememberSongPath(sourceId: Long?) = remember(sourceId) { SongPathDraft() }

@Composable
internal fun SongPathCard(
    path: SongPathDraft,
    busy: Boolean,
    building: Boolean,
    cover: (String?) -> CoverArtRef?,
    playing: SongPathChoice.Song? = null,
    onPlay: (AudioMusePathEnd, AudioMusePathEnd, Int) -> Unit,
) {
    val from = path.from(playing)
    val to = path.to
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("songPathCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.AltRoute, contentDescription = null)
                Text(stringResource(R.string.song_path_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { path.swap(playing) }, enabled = !building && (from != null || to != null)) {
                    Icon(Icons.Rounded.SwapVert, stringResource(R.string.song_path_swap))
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                SongPathEndpointRow(SongPathSide.From, from, cover, !building) { path.editing = SongPathSide.From }
                HorizontalDivider()
                SongPathEndpointRow(SongPathSide.To, to, cover, !building) { path.editing = SongPathSide.To }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.song_path_song_count, path.length, path.length),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val lengthLabel = stringResource(R.string.song_path_length)
                    Slider(
                        value = path.length.toFloat(),
                        onValueChange = { path.length = it.roundToInt() },
                        valueRange = SongPathBuilder.MIN_LENGTH.toFloat()..SongPathBuilder.MAX_LENGTH.toFloat(),
                        steps = SongPathBuilder.MAX_LENGTH - SongPathBuilder.MIN_LENGTH - 1,
                        enabled = !building,
                        modifier = Modifier.semantics { contentDescription = lengthLabel },
                    )
                }
                FilledIconButton(
                    onClick = { if (from != null && to != null) onPlay(from.endpoint, to.endpoint, path.length) },
                    enabled = !busy && !building && SongPathBuilder.canBuild(from?.endpoint, to?.endpoint),
                    modifier = Modifier.size(48.dp),
                ) {
                    if (building) {
                        val label = stringResource(R.string.song_path_building)
                        CircularProgressIndicator(Modifier.size(22.dp).semantics { contentDescription = label }, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.song_path_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun SongPathEndpointRow(
    side: SongPathSide,
    choice: SongPathChoice?,
    cover: (String?) -> CoverArtRef?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val sideLabel = stringResource(side.label)
    val title =
        when (choice) {
            is SongPathChoice.Song -> choice.title
            is SongPathChoice.Mood -> stringResource(choice.mood.title())
            null -> stringResource(R.string.song_path_choose)
        }
    val detail =
        when (choice) {
            is SongPathChoice.Song -> choice.artist
            is SongPathChoice.Mood -> stringResource(R.string.song_path_mood)
            null -> null
        }
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (detail.isNullOrBlank()) sideLabel else stringResource(R.string.song_path_endpoint_detail, sideLabel, detail),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (choice is SongPathChoice.Song) {
                CoverArt(cover(choice.cover), choice.title, Modifier.size(36.dp))
            } else {
                Icon(if (choice == null) Icons.Rounded.Add else Icons.Rounded.MusicNote, null, Modifier.size(36.dp))
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
internal fun SongPathPickerHost(
    draft: SongPathDraft,
    sourceId: Long,
    viewModel: DiscoverViewModel,
    playing: SongPathChoice.Song?,
) {
    val side = draft.editing ?: return
    val from = draft.from(playing)
    SongPathPicker(
        side = side,
        current = if (side == SongPathSide.From) from else draft.to,
        other = if (side == SongPathSide.From) draft.to else from,
        playing = playing,
        cover = viewModel::pathCover,
        suggestions = { viewModel.pathSuggestions(sourceId) },
        search = { viewModel.pathSearch(sourceId, it) },
        surprise = { viewModel.pathSurprise(sourceId, it) },
        onDismiss = { draft.editing = null },
        onChoice = { draft.choose(side, it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongPathPicker(
    side: SongPathSide,
    current: SongPathChoice?,
    other: SongPathChoice?,
    playing: SongPathChoice.Song?,
    cover: (String?) -> CoverArtRef?,
    suggestions: () -> Flow<List<Pair<Int, List<AlbumSongItem>>>>,
    search: (String) -> Flow<List<AlbumSongItem>>,
    surprise: suspend (String?) -> AlbumSongItem?,
    onDismiss: () -> Unit,
    onChoice: (SongPathChoice) -> Unit,
) {
    val moodAllowed = other !is SongPathChoice.Mood
    val exclude = (other as? SongPathChoice.Song)?.id
    var moodTab by remember { mutableStateOf(current is SongPathChoice.Mood) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var surprising by remember { mutableStateOf(false) }
    var surpriseError by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(side.label),
                style = MaterialTheme.typography.titleLarge,
            )
            if (moodAllowed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!moodTab, { moodTab = false }, { Text(stringResource(R.string.song_path_choose_song)) })
                    FilterChip(moodTab, { moodTab = true }, { Text(stringResource(R.string.song_path_mood)) })
                }
            }
            if (moodTab && moodAllowed) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(AudioMuseMood.entries) { mood ->
                        ListItem(
                            headlineContent = { Text(stringResource(mood.title())) },
                            modifier = Modifier.clickable { onChoice(SongPathChoice.Mood(mood)) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.song_path_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val trimmed = query.trim()
                val results by key(trimmed) {
                    produceState<SongPickerContent>(SongPickerContent.Loading) {
                        try {
                            when {
                                trimmed.isEmpty() -> {
                                    suggestions().collect { value = SongPickerContent.Songs(it) }
                                }

                                trimmed.length < 2 -> {
                                    value = SongPickerContent.Hint(R.string.song_path_search_prompt)
                                }

                                else -> {
                                    delay(250)
                                    search(trimmed).collect { value = SongPickerContent.Songs(listOf(null to it)) }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            value = SongPickerContent.Hint(R.string.song_path_picker_failed)
                        }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (trimmed.isEmpty()) {
                        if (playing != null && playing.id != exclude) {
                            item { SongPickerHeading(R.string.song_path_now_playing) }
                            item { SongPickerRow(playing, cover, onChoice) }
                        }
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.song_path_surprise)) },
                                trailingContent = {
                                    if (surprising) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                },
                                modifier =
                                    Modifier.clickable(enabled = !surprising) {
                                        surprising = true
                                        surpriseError = null
                                        scope.launch {
                                            try {
                                                val song = surprise(exclude)
                                                if (song == null) {
                                                    surpriseError = R.string.song_path_surprise_empty
                                                } else {
                                                    onChoice(song.choice())
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                surpriseError = R.string.song_path_picker_failed
                                            } finally {
                                                surprising = false
                                            }
                                        }
                                    },
                            )
                        }
                        surpriseError?.let { message -> item { SongPickerHeading(message) } }
                    }
                    when (val content = results) {
                        SongPickerContent.Loading -> {
                            item {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                            }
                        }

                        is SongPickerContent.Hint -> {
                            item { SongPickerHeading(content.message) }
                        }

                        is SongPickerContent.Songs -> {
                            val groups = content.groups.map { (heading, songs) -> heading to songs.filter { it.song.id != exclude } }
                            if (trimmed.isNotEmpty() && groups.all { it.second.isEmpty() }) {
                                item { Text(stringResource(R.string.search_no_results, trimmed), Modifier.padding(12.dp)) }
                            }
                            for ((heading, songs) in groups) {
                                if (heading != null && songs.isNotEmpty()) item { SongPickerHeading(heading) }
                                items(songs) { SongPickerRow(it.choice(), cover, onChoice) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SongPickerContent {
    data object Loading : SongPickerContent

    data class Hint(
        val message: Int,
    ) : SongPickerContent

    data class Songs(
        val groups: List<Pair<Int?, List<AlbumSongItem>>>,
    ) : SongPickerContent
}

@Composable
private fun SongPickerHeading(title: Int) {
    Text(stringResource(title), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun SongPickerRow(
    song: SongPathChoice.Song,
    cover: (String?) -> CoverArtRef?,
    onChoice: (SongPathChoice) -> Unit,
) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = song.artist?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { CoverArt(cover(song.cover), song.title, Modifier.size(40.dp)) },
        modifier = Modifier.clickable { onChoice(song) },
    )
}

private fun AlbumSongItem.choice() = SongPathChoice.Song(song.id, song.title, song.artist, coverArt)

private fun AudioMuseMood.title(): Int =
    when (this) {
        AudioMuseMood.HAPPY -> R.string.queue_builder_mood_happy
        AudioMuseMood.SAD -> R.string.queue_builder_mood_sad
        AudioMuseMood.AGGRESSIVE -> R.string.queue_builder_mood_aggressive
        AudioMuseMood.RELAXED -> R.string.queue_builder_mood_relaxed
        AudioMuseMood.DANCEABLE -> R.string.queue_builder_mood_danceable
    }
