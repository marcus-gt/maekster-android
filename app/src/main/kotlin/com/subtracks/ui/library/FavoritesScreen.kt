package com.subtracks.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.prefs.FavoriteSort
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.source.StarType
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.home.FavoriteKind
import com.subtracks.ui.home.HomeListRequest
import com.subtracks.ui.home.HomeListRoute
import com.subtracks.ui.theme.MaeksterSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FavoritesRoute(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    setStar: (StarType, String, Boolean) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    preferences: UserPreferences = koinInject(),
) {
    var kind by rememberSaveable { mutableStateOf(FavoriteKind.Songs) }
    val sortFlow = remember(preferences) { preferences.favoriteSort() }
    val sort by sortFlow.collectAsStateWithLifecycle(initialValue = FavoriteSort.Starred)
    val scope = rememberCoroutineScope()
    val request = HomeListRequest(title = stringResource(R.string.resources_filter_starred), favorites = kind, favoriteSort = sort)
    key(kind, sort) {
        HomeListRoute(
            request = request,
            onBack = onBack,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onViewAlbum = onViewAlbum,
            onViewArtist = onViewArtist,
            onGenreClick = {},
            onDecadeClick = {},
            contextMenuHost = contextMenuHost,
            setStar = setStar,
            viewModel = koinViewModel(key = "favorites-${kind.name}-${sort.name}") { parametersOf(request) },
            topBarExtra = { FavoriteKindSelector(selected = kind, onSelect = { kind = it }) },
            topBarActions = { FavoriteSortMenu(sort) { scope.launch { preferences.setFavoriteSort(it) } } },
        )
    }
}

@Composable
internal fun FavoriteSortMenu(
    selected: FavoriteSort,
    onSelect: (FavoriteSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_by))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FavoriteSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label())) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                    leadingIcon =
                        if (selected == sort) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

private fun FavoriteSort.label(): Int =
    when (this) {
        FavoriteSort.Name -> R.string.resources_sort_by_name
        FavoriteSort.Starred -> R.string.resources_sort_by_recently_favorited
        FavoriteSort.Recent -> R.string.resources_sort_by_recently_played
    }

@Composable
internal fun FavoriteKindSelector(
    selected: FavoriteKind,
    onSelect: (FavoriteKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaeksterSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(MaeksterSpacing.s),
    ) {
        FavoriteKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(stringResource(kind.title)) },
            )
        }
    }
}
