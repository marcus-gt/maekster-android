package com.subtracks.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.model.Artist
import com.subtracks.data.prefs.DiscoverLayout
import com.subtracks.data.prefs.DiscoverShelf
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.library.SettingsAction
import com.subtracks.ui.theme.artworkColorsFromSeed
import com.subtracks.ui.theme.placeholderSeed
import com.subtracks.ui.theme.rememberArtworkColors
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DiscoverRoute(
    onOpenSettings: () -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
    freshViewModel: FreshReleasesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val fresh by freshViewModel.state.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val startingMood by viewModel.startingMood.collectAsStateWithLifecycle()
    val songPath = if (DiscoverShelf.SongPath in layout.visible) rememberSongPathCard(viewModel) else null
    DiscoverScreen(
        state,
        busy,
        viewModel::smartShuffle,
        viewModel::station,
        onOpenSettings,
        viewModel::art,
        freshReleases = fresh,
        showReleaseCovers = !state.offline,
        layout = layout,
        queueBuilderContent = { QueueBuilderRoute() },
        startingMood = startingMood,
        onMood = viewModel::moodRadio,
        songPathContent = songPath,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverState,
    busy: Boolean,
    onShuffle: () -> Unit,
    onStation: (Artist) -> Unit,
    onOpenSettings: () -> Unit,
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    queueBuilder: QueueBuilderState = QueueBuilderState(loading = false),
    queueBuilderFocus: String? = null,
    queueBuilderBuilding: Boolean = false,
    queueBuilderActions: QueueBuilderActions = QueueBuilderActions(),
    freshReleases: FreshReleasesState = FreshReleasesState(),
    showReleaseCovers: Boolean = true,
    layout: DiscoverLayout = DiscoverLayout(),
    queueBuilderContent: (@Composable () -> Unit)? = null,
    startingMood: RadioMood? = null,
    onMood: (RadioMood) -> Unit = {},
    songPathContent: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.discover_title)) }, actions = { SettingsAction(false, onOpenSettings) })
        LazyColumn(
            modifier = Modifier.testTag("discoverList"),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            for (section in layout.visible) {
                when (section) {
                    DiscoverShelf.FreshReleases -> {
                        if (freshReleases.enabled && freshReleases.releases.isNotEmpty()) {
                            item(key = section.name) { FreshReleasesShelf(freshReleases.releases, showCovers = showReleaseCovers) }
                        }
                    }

                    DiscoverShelf.SmartShuffle -> {
                        item(key = section.name) { SmartShuffleCard(state.offline, busy, onShuffle) }
                    }

                    DiscoverShelf.QueueBuilder -> {
                        item(key = section.name) {
                            if (queueBuilderContent != null) {
                                queueBuilderContent()
                            } else {
                                QueueBuilderSection(queueBuilder, queueBuilderFocus, queueBuilderBuilding, queueBuilderActions)
                            }
                        }
                    }

                    DiscoverShelf.Stations -> {
                        if (!state.offline) {
                            item(key = section.name) { StationsSection(state.artists, busy, artistArt, onStation) }
                        }
                    }

                    DiscoverShelf.SongPath -> {
                        if (!state.offline && state.moods && songPathContent != null) {
                            item(key = section.name) { songPathContent() }
                        }
                    }

                    DiscoverShelf.Moods -> {
                        if (!state.offline && state.moods) {
                            item(key = section.name) { MoodsSection(busy, startingMood, onMood) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartShuffleCard(
    offline: Boolean,
    busy: Boolean,
    onShuffle: () -> Unit,
) {
    val brush = smartShuffleColors().discoverCardBrush()
    Card(
        onClick = onShuffle,
        enabled = !busy,
        colors = vividCardColors(),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(brush).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Text(stringResource(R.string.smart_shuffle_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(if (offline) R.string.discover_shuffle_offline else R.string.discover_shuffle_description),
                color = VividCardText.copy(alpha = 0.92f),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = VividCardText)
                    Text(stringResource(R.string.mix_preparing))
                }
            }
        }
    }
}

@Composable
private fun StationsSection(
    artists: List<Artist>,
    busy: Boolean,
    art: (Artist) -> ArtistArt,
    onStation: (Artist) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.discover_stations), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.discover_stations_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (artists.isEmpty()) {
            Text(stringResource(R.string.discover_stations_empty), Modifier.padding(horizontal = 20.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(artists, key = { it.id }) { artist ->
                    val artistArt = art(artist)
                    val colors = rememberArtworkColors(artistArt.primary, fallbackName = artist.name)
                    val brush = (colors ?: artworkColorsFromSeed(placeholderSeed(artist.name))).discoverCardBrush()
                    VividTileCard(
                        onClick = { onStation(artist) },
                        enabled = !busy,
                        brush = brush,
                        title = artist.name,
                        caption = null,
                        modifier = Modifier.width(VIVID_TILE),
                    ) {
                        ArtistIcon(artist, artistArt, Modifier.size(104.dp).clip(CircleShape))
                    }
                }
            }
        }
    }
}

internal fun RadioMood.title(): Int =
    when (this) {
        RadioMood.NIGHT -> R.string.mood_night
        RadioMood.ENERGETIC -> R.string.mood_energetic
        RadioMood.WORKOUT -> R.string.mood_workout
        RadioMood.CHILL -> R.string.mood_chill
        RadioMood.FOCUS -> R.string.mood_focus
    }

private fun RadioMood.icon(): ImageVector =
    when (this) {
        RadioMood.NIGHT -> Icons.Rounded.NightsStay
        RadioMood.ENERGETIC -> Icons.Rounded.Bolt
        RadioMood.WORKOUT -> Icons.Rounded.FitnessCenter
        RadioMood.CHILL -> Icons.Rounded.Spa
        RadioMood.FOCUS -> Icons.Rounded.Headphones
    }

@Composable
private fun MoodsSection(
    busy: Boolean,
    startingMood: RadioMood?,
    onMood: (RadioMood) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.discover_moods), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.discover_moods_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            modifier = Modifier.testTag("moodRadios"),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(RadioMood.entries, key = { it.key }) { mood ->
                val brush = remember(mood) { mood.cardColors().discoverCardBrush() }
                VividTileCard(
                    onClick = { onMood(mood) },
                    enabled = !busy,
                    brush = brush,
                    title = stringResource(mood.title()),
                    caption = stringResource(R.string.mood_radio),
                    modifier = Modifier.width(VIVID_TILE).testTag("moodRadio-${mood.key}"),
                ) {
                    Icon(
                        mood.icon(),
                        contentDescription = null,
                        tint = VividCardText,
                        modifier = Modifier.size(56.dp),
                    )
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                        if (startingMood == mood) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp).testTag("moodRadioStarting"),
                                strokeWidth = 2.dp,
                                color = VividCardText,
                            )
                        } else {
                            Icon(Icons.Rounded.Radio, contentDescription = null, tint = VividCardText, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
