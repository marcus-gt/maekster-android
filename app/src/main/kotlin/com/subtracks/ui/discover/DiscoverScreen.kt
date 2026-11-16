package com.subtracks.ui.discover

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.model.Artist
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.library.SettingsAction
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DiscoverRoute(
    onOpenSettings: () -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplaySimilar.collectAsStateWithLifecycle()
    DiscoverScreen(
        state,
        busy,
        viewModel::smartShuffle,
        viewModel::station,
        onOpenSettings,
        viewModel::art,
        autoplaySimilar = autoplay,
        onAutoplayChange = viewModel::setAutoplaySimilar,
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
    autoplaySimilar: Boolean = false,
    onAutoplayChange: (Boolean) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.discover_title)) },
            actions = { SettingsAction(false, onOpenSettings) },
        )
        LazyColumn(
            modifier = Modifier.testTag("discoverList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Card(
                    onClick = onShuffle,
                    enabled = !busy,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Text(stringResource(R.string.smart_shuffle_title), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            stringResource(if (state.offline) R.string.discover_shuffle_offline else R.string.discover_shuffle_description),
                        )
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.mix_preparing))
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = autoplaySimilar, role = Role.Switch, onValueChange = onAutoplayChange)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.autoplay_similar), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.autoplay_similar_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoplaySimilar, onCheckedChange = null)
                }
            }
            if (!state.offline) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.discover_stations), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.discover_stations_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.artists.isEmpty()) {
                    item { Text(stringResource(R.string.discover_stations_empty)) }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.artists, key = { it.id }) { artist ->
                                Card(onClick = { onStation(artist) }, enabled = !busy, modifier = Modifier.width(152.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ArtistIcon(artist, artistArt(artist), Modifier.size(120.dp))
                                        Text(
                                            artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            minLines = 2,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
