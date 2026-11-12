package com.subtracks.ui.search

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.db.SEARCH_MIN_LENGTH
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.SearchField
import com.subtracks.ui.home.AlbumListRow
import com.subtracks.ui.home.ArtistListRow
import com.subtracks.ui.library.PlaylistRow
import com.subtracks.ui.library.SongRow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchRoute(
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: SearchViewModel = koinViewModel(),
    playbackController: PlaybackController = koinInject(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var scope by rememberSaveable { mutableStateOf(SearchScope.All) }
    val actions =
        ItemActions(
            playNext = { playbackController.playNext(it.sourceId, it.kind, it.refId) },
            addToQueue = { playbackController.addToQueue(it.sourceId, it.kind, it.refId) },
            setStar = setStar,
            viewAlbum = onViewAlbum,
            viewArtist = onViewArtist,
        )
    SearchScreen(
        query = query,
        onQueryChange = viewModel::setQuery,
        results = results,
        scope = scope,
        onScopeChange = { scope = it },
        coverArt = viewModel::coverArt,
        artistArt = { viewModel.artistArt(it) },
        playingSongId = playback.item?.id,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onAlbumClick = {
            focusManager.clearFocus()
            onAlbumClick(it)
        },
        onArtistClick = {
            focusManager.clearFocus()
            onArtistClick(it)
        },
        onPlaylistClick = {
            focusManager.clearFocus()
            onPlaylistClick(it)
        },
        onSongClick = {
            focusManager.clearFocus()
            viewModel.play(it)
        },
        onLongClick = {
            focusManager.clearFocus()
            contextMenuHost?.show(it, actions)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults,
    modifier: Modifier = Modifier,
    scope: SearchScope = SearchScope.All,
    onScopeChange: (SearchScope) -> Unit = {},
    coverArt: (String?, Boolean) -> CoverArtRef? = { _, _ -> null },
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    playingSongId: String? = null,
    onBack: (() -> Unit)? = {},
    onOpenSettings: (() -> Unit)? = null,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onLongClick: (MenuTarget) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val backLabel = stringResource(R.string.navigation_back)
    val minLength = stringResource(R.string.search_min_length, SEARCH_MIN_LENGTH)
    val ranked = remember(results, query, scope) { rankSearch(results, query, scope) }
    val topLabel = stringResource(R.string.search_top_result)
    val seeAll = stringResource(R.string.search_see_all)
    val labels = SearchScope.entries.associateWith { stringResource(it.label) }
    val seeAllLabels = labels.mapValues { (_, label) -> stringResource(R.string.search_see_all_scope, label.lowercase()) }
    val row = MatchRowActions(coverArt, artistArt, playingSongId, onAlbumClick, onArtistClick, onPlaylistClick, onSongClick, onLongClick)
    // A new scope starts at the top of its list rather than wherever the previous one was scrolled.
    val listState = remember(scope) { LazyListState() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                        }
                    }
                },
                title = {
                    // As a tab root the field waits for a tap, so switching tabs never raises the keyboard.
                    SearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        onClose = { onQueryChange("") },
                        focusRequester = focusRequester,
                        showClose = query.isNotEmpty(),
                        autoFocus = onBack != null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                },
                actions = {
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.navigation_tabs_settings))
                        }
                    }
                },
            )
        },
        bottomBar = {
            SearchScopeBar(selected = scope, onSelect = onScopeChange)
        },
    ) { padding ->
        when {
            !searchReady(query) -> {
                EmptyState(
                    text = minLength,
                    modifier = Modifier.padding(padding),
                )
            }

            results.loading -> {
                LoadingState(Modifier.padding(padding))
            }

            ranked.isEmpty -> {
                EmptyState(
                    text = stringResource(R.string.search_no_results, query),
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    ranked.top?.let { top ->
                        resultHeader("top", topLabel)
                        item(key = "top-${top.key}") { SearchMatchRow(top, row) }
                    }
                    for (group in ranked.groups) {
                        resultHeader(group.scope.name, labels.getValue(group.scope))
                        items(group.matches, key = { it.key }) { SearchMatchRow(it, row) }
                        if (group.hasMore) {
                            item(key = "see-all-${group.scope.name}") {
                                val description = seeAllLabels.getValue(group.scope)
                                TextButton(
                                    onClick = { onScopeChange(group.scope) },
                                    modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = description },
                                ) { Text(seeAll) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScopeBar(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchScope.entries.forEach { scope ->
            FilterChip(
                selected = scope == selected,
                onClick = { onSelect(scope) },
                label = { Text(stringResource(scope.label)) },
            )
        }
    }
}

private fun LazyListScope.resultHeader(
    key: String,
    title: String,
) {
    item(key = "search-header-$key") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

private class MatchRowActions(
    val coverArt: (String?, Boolean) -> CoverArtRef?,
    val artistArt: (Artist) -> ArtistArt,
    val playingSongId: String?,
    val onAlbumClick: (Album) -> Unit,
    val onArtistClick: (Artist) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onSongClick: (Song) -> Unit,
    val onLongClick: (MenuTarget) -> Unit,
)

@Composable
private fun SearchMatchRow(
    match: SearchMatch,
    row: MatchRowActions,
) {
    when (match) {
        is SearchMatch.SongMatch -> {
            val item = match.item
            SongRow(
                song = item.song,
                coverArtId = item.coverArt,
                coverArt = row.coverArt,
                isPlaying = item.song.id == row.playingSongId,
                durationSeconds = item.song.duration,
                modifier =
                    Modifier.combinedClickable(
                        onClick = { row.onSongClick(item.song) },
                        onLongClick = { row.onLongClick(MenuTarget.Song(item.song, row.coverArt(item.coverArt, true))) },
                    ),
            )
        }

        is SearchMatch.AlbumMatch -> {
            val album = match.album
            AlbumListRow(
                album = album,
                coverArt = row.coverArt,
                info = null,
                onClick = { row.onAlbumClick(album) },
                onLongClick = { row.onLongClick(MenuTarget.Album(album, row.coverArt(album.coverArt, true))) },
            )
        }

        is SearchMatch.ArtistMatch -> {
            val artist = match.artist
            val art = row.artistArt(artist)
            ArtistListRow(
                artist = artist,
                art = art,
                info = null,
                onClick = { row.onArtistClick(artist) },
                onLongClick = { row.onLongClick(MenuTarget.Artist(artist, art.primary)) },
            )
        }

        is SearchMatch.PlaylistMatch -> {
            val playlist = match.playlist
            PlaylistRow(
                playlist = playlist,
                coverArt = row.coverArt,
                download = null,
                onClick = { row.onPlaylistClick(playlist) },
                onLongClick = { row.onLongClick(MenuTarget.Playlist(playlist, row.coverArt(playlist.coverArt, true))) },
            )
        }
    }
}
