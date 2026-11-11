package com.subtracks.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.theme.MaeksterShapes
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.MaeksterTheme
import org.koin.compose.viewmodel.koinViewModel

private val ICON_TILE = 36.dp
private val ICON_SIZE = 20.dp

@Composable
fun LibraryHubRoute(
    onOpenEntry: (LibraryHubEntry) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryHubViewModel = koinViewModel(),
) {
    val entries = viewModel.entries.collectAsStateWithLifecycle().value ?: return
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    LibraryHubScreen(
        entries = entries,
        offline = offline,
        onOpenEntry = onOpenEntry,
        onOpenSettings = onOpenSettings,
        syncing = syncing,
        refreshing = refreshing,
        onSync = viewModel::sync,
        onGoOnline = viewModel::goOnline,
    )
}

/**
 * The Library tab's root: a list of the library's sections, each opening its own list. Offline, it lists only the
 * sections with something downloaded, and says so when there is nothing at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHubScreen(
    entries: List<LibraryHubEntry>,
    offline: Boolean,
    onOpenEntry: (LibraryHubEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    syncing: Boolean = false,
    refreshing: Boolean = false,
    onSync: () -> Unit = {},
    onGoOnline: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.navigation_tabs_library)) },
            actions = {
                if (offline) {
                    IconButton(onClick = onGoOnline) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = stringResource(R.string.offline_mode_status))
                    }
                }
                SettingsAction(syncing = syncing, onClick = onOpenSettings)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onSync,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.library_offline_empty),
                    actionLabel = stringResource(R.string.library_go_online),
                    onAction = onGoOnline,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = MaeksterSpacing.s),
                ) {
                    items(entries, key = { it.name }) { entry ->
                        LibraryHubRow(entry = entry, onClick = { onOpenEntry(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHubRow(
    entry: LibraryHubEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = MaeksterSpacing.l, vertical = MaeksterSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaeksterSpacing.l),
    ) {
        Box(
            modifier =
                Modifier
                    .size(ICON_TILE)
                    .clip(MaeksterShapes.standard)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Text(
            text = stringResource(entry.label),
            style = MaeksterTheme.text.cellTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The Settings action every tab root carries; a spinner rings it while a sync runs. */
@Composable
internal fun SettingsAction(
    syncing: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.navigation_tabs_settings),
            )
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
