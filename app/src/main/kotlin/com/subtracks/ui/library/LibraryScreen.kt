package com.subtracks.ui.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.BulkDownloadAction
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.Playlist
import com.subtracks.data.prefs.AlbumSort
import com.subtracks.data.prefs.ArtistSort
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.data.prefs.ListQuery
import com.subtracks.data.prefs.PlaylistSort
import com.subtracks.data.prefs.StarredFilter
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.DismissOnRequest
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.PendingDownloadDeleteConfirmation
import com.subtracks.ui.components.SearchField
import com.subtracks.ui.components.rememberPendingDownloadDelete
import com.subtracks.ui.theme.MaeksterSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The library's lists, each opened from the Library landing list. */
enum class LibraryTab(
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Albums(R.string.resources_album_name, Icons.Rounded.Album),
    Artists(R.string.resources_artist_name, Icons.Rounded.Person),
    Playlists(R.string.resources_playlist_name, Icons.AutoMirrored.Rounded.PlaylistPlay),
}

data class SortOption(
    val value: String,
    @param:StringRes val label: Int,
    val descendingByDefault: Boolean = false,
)

fun LibraryTab.listTab(): LibraryListTab? = LibraryListTab.entries.firstOrNull { it.name == name }

/** The list a route argument names; anything unknown opens Albums. */
internal fun libraryTabFor(name: String?): LibraryTab = LibraryTab.entries.firstOrNull { it.name == name } ?: LibraryTab.Albums

fun sortOptionsFor(
    tab: LibraryTab,
    play: PlaySortAvailability = PlaySortAvailability(frequent = false, recent = false),
): List<SortOption> =
    when (tab) {
        LibraryTab.Albums -> {
            buildList {
                add(SortOption(AlbumSort.Name.name, R.string.resources_sort_by_name))
                add(SortOption(AlbumSort.Artist.name, R.string.resources_sort_by_artist))
                add(SortOption(AlbumSort.Year.name, R.string.resources_sort_by_year, descendingByDefault = true))
                add(SortOption(AlbumSort.Added.name, R.string.resources_sort_by_added, descendingByDefault = true))
                add(SortOption(AlbumSort.Starred.name, R.string.resources_filter_starred, descendingByDefault = true))
                if (play.frequent) {
                    add(
                        SortOption(AlbumSort.Frequent.name, R.string.resources_sort_by_frequently_played, descendingByDefault = true),
                    )
                }
                if (play.recent) {
                    add(
                        SortOption(AlbumSort.Recent.name, R.string.resources_sort_by_recently_played, descendingByDefault = true),
                    )
                }
            }
        }

        LibraryTab.Artists -> {
            buildList {
                add(SortOption(ArtistSort.Name.name, R.string.resources_sort_by_name))
                add(SortOption(ArtistSort.AlbumCount.name, R.string.resources_album_name, descendingByDefault = true))
                add(SortOption(ArtistSort.Starred.name, R.string.resources_filter_starred, descendingByDefault = true))
                if (play.frequent) {
                    add(
                        SortOption(ArtistSort.Frequent.name, R.string.resources_sort_by_frequently_played, descendingByDefault = true),
                    )
                }
                if (play.recent) {
                    add(
                        SortOption(ArtistSort.Recent.name, R.string.resources_sort_by_recently_played, descendingByDefault = true),
                    )
                }
            }
        }

        LibraryTab.Playlists -> {
            listOf(
                SortOption(PlaylistSort.Name.name, R.string.resources_sort_by_name),
                SortOption(PlaylistSort.Added.name, R.string.resources_sort_by_added, descendingByDefault = true),
                SortOption(PlaylistSort.Updated.name, R.string.resources_sort_by_updated, descendingByDefault = true),
            )
        }
    }

@Composable
fun LibraryListRoute(
    list: LibraryTab,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    bottomInset: Dp,
    viewModel: LibraryViewModel = koinViewModel(),
    playbackController: PlaybackController = koinInject(),
) {
    val listTab = list.listTab() ?: return
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val listQuery by viewModel.listQuery(listTab).collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val playSort by viewModel.playSortAvailability.collectAsStateWithLifecycle()
    val displayQuery = if (offline) listQuery.copy(downloaded = true) else listQuery
    val search by viewModel.search(listTab).collectAsStateWithLifecycle()
    val downloads by viewModel.downloads(listTab).collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val pendingDelete = rememberPendingDownloadDelete(contextMenuHost?.dismissals ?: emptyFlow(), viewModel::downloadedBytes)
    val itemActions =
        ItemActions(
            playNext = { playbackController.playNext(it.sourceId, it.kind, it.refId) },
            addToQueue = { playbackController.addToQueue(it.sourceId, it.kind, it.refId) },
            bulkDownload = { target, action -> pendingDelete.onAction(target, action, viewModel::onDownloadAction) },
            setStar = setStar,
            viewAlbum = onViewAlbum,
            viewArtist = onViewArtist,
        )
    LibraryListScreen(
        list = list,
        refreshing = refreshing,
        bottomInset = bottomInset,
        albums = if (list == LibraryTab.Albums) viewModel.albums else emptyFlow(),
        artists = if (list == LibraryTab.Artists) viewModel.artists else emptyFlow(),
        playlists = if (list == LibraryTab.Playlists) viewModel.playlists else emptyFlow(),
        coverArt = viewModel::coverArt,
        artistArt = { viewModel.artistArt(it) },
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPlaylistClick = onPlaylistClick,
        onSync = viewModel::sync,
        onBack = onBack,
        onItemLongClick = {
            focusManager.clearFocus()
            contextMenuHost?.show(it, itemActions)
        },
        listQuery = displayQuery,
        resetKey = "${listQuery.sort}|${listQuery.descending}|${listQuery.starred}|${listQuery.downloaded || offline}|$search",
        offline = offline,
        onExitOffline = { viewModel.setOffline(false) },
        sortOptions = sortOptionsFor(list, playSort),
        starredSupported = listTab.supportsStarred,
        onSortChange = { sort -> viewModel.setListQuery(listTab, listQuery.copy(sort = sort)) },
        onToggleSortDirection = { viewModel.setListQuery(listTab, listQuery.copy(descending = !listQuery.descending)) },
        onCycleStarred = {
            val next =
                when (listQuery.starred) {
                    StarredFilter.Any -> StarredFilter.Starred
                    StarredFilter.Starred -> StarredFilter.NotStarred
                    StarredFilter.NotStarred -> StarredFilter.Any
                }
            viewModel.setListQuery(listTab, listQuery.copy(starred = next))
        },
        onClearFilters = {
            viewModel.setListQuery(listTab, listQuery.copy(starred = StarredFilter.Any, downloaded = false))
            viewModel.setSearch(listTab, "")
        },
        search = search,
        onSearchChange = { value -> viewModel.setSearch(listTab, value) },
        onToggleDownloaded = { viewModel.setListQuery(listTab, listQuery.copy(downloaded = !listQuery.downloaded)) },
        downloads = downloads,
        dismissals = contextMenuHost?.dismissals ?: emptyFlow(),
    )

    PendingDownloadDeleteConfirmation(pendingDelete) { pending ->
        viewModel.onDownloadAction(pending.list, pending.refId, BulkDownloadAction.Delete)
    }
}

/**
 * One library list (albums, artists or playlists) pushed from the Library landing list. Its top bar carries
 * search and the sort and filter sheet; only the flow for [list] is collected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListScreen(
    list: LibraryTab,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onSync: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Lists not passed in show as settled and empty rather than loading forever.
    albums: Flow<PagingData<Album>> = remember { flowOf(PagingData.empty()) },
    artists: Flow<PagingData<Artist>> = remember { flowOf(PagingData.empty()) },
    playlists: Flow<PagingData<Playlist>> = remember { flowOf(PagingData.empty()) },
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    offline: Boolean = false,
    onExitOffline: () -> Unit = {},
    onItemLongClick: (MenuTarget) -> Unit = {},
    refreshing: Boolean = false,
    bottomInset: Dp = 0.dp,
    listQuery: ListQuery = ListQuery(""),
    resetKey: Any? = null,
    sortOptions: List<SortOption> = emptyList(),
    starredSupported: Boolean = false,
    onSortChange: (String) -> Unit = {},
    onToggleSortDirection: () -> Unit = {},
    onCycleStarred: () -> Unit = {},
    onToggleDownloaded: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    search: String = "",
    onSearchChange: (String) -> Unit = {},
    downloads: Map<String, ListDownloadStatus> = emptyMap(),
    dismissals: Flow<Unit> = emptyFlow(),
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val closeSearch = {
        searchActive = false
        onSearchChange("")
    }

    // Search stays open while a query is typed, so the query survives opening a result and coming back.
    DismissOnRequest(dismissals) { showOptions = false }
    BackHandler(enabled = searchActive, onBack = closeSearch)
    LaunchedEffect(Unit) { if (search.isNotEmpty()) searchActive = true }

    val filtersActive = listQuery.starred != StarredFilter.Any || listQuery.downloaded || search.isNotEmpty()
    val listBottomInset = bottomInset + MaeksterSpacing.l
    val open = { action: () -> Unit ->
        focusManager.clearFocus()
        action()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (searchActive) {
                SearchField(
                    value = search,
                    onValueChange = onSearchChange,
                    onClose = closeSearch,
                    focusRequester = searchFocus,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = MaeksterSpacing.m, vertical = MaeksterSpacing.s),
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(list.label)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
                        }
                    },
                    actions = {
                        if (offline) {
                            IconButton(onClick = onExitOffline) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = stringResource(R.string.offline_mode_status))
                            }
                        }
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_this_list))
                        }
                        IconButton(onClick = { showOptions = true }) {
                            BadgedBox(badge = { if (filtersActive) Badge() }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.list_options))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onSync,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (list) {
                LibraryTab.Albums -> {
                    AlbumsContent(
                        albums.collectAsLazyPagingItems(),
                        coverArt,
                        listBottomInset,
                        { open { onAlbumClick(it) } },
                        onLongClick = onItemLongClick,
                        filtered = filtersActive,
                        onClearFilters = onClearFilters,
                        resetKey = resetKey,
                        onSync = onSync,
                        downloadStatuses = downloads,
                    )
                }

                LibraryTab.Artists -> {
                    ArtistsContent(
                        artists.collectAsLazyPagingItems(),
                        coverArt,
                        listBottomInset,
                        { open { onArtistClick(it) } },
                        onLongClick = onItemLongClick,
                        filtered = filtersActive,
                        onClearFilters = onClearFilters,
                        resetKey = resetKey,
                        onSync = onSync,
                        downloadStatuses = downloads,
                        artistArt = artistArt,
                    )
                }

                LibraryTab.Playlists -> {
                    PlaylistsContent(
                        playlists.collectAsLazyPagingItems(),
                        coverArt,
                        listBottomInset,
                        { open { onPlaylistClick(it) } },
                        onLongClick = onItemLongClick,
                        filtered = filtersActive,
                        onClearFilters = onClearFilters,
                        resetKey = resetKey,
                        onSync = onSync,
                        downloadStatuses = downloads,
                    )
                }
            }
        }
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ListOptionsSheet(
                listQuery = listQuery,
                sortOptions = sortOptions,
                starredSupported = starredSupported,
                canClearFilters = filtersActive,
                onSortChange = onSortChange,
                onToggleSortDirection = onToggleSortDirection,
                onCycleStarred = onCycleStarred,
                onToggleDownloaded = onToggleDownloaded,
                onClearFilters = onClearFilters,
                onSearch = {
                    showOptions = false
                    searchActive = true
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListOptionsSheet(
    listQuery: ListQuery,
    sortOptions: List<SortOption>,
    starredSupported: Boolean,
    canClearFilters: Boolean,
    onSortChange: (String) -> Unit,
    onToggleSortDirection: () -> Unit,
    onCycleStarred: () -> Unit,
    onToggleDownloaded: () -> Unit,
    onClearFilters: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        val naturalDescending = sortOptions.firstOrNull { it.value == listQuery.sort }?.descendingByDefault == true
        val descending = if (naturalDescending) !listQuery.descending else listQuery.descending
        Surface(
            onClick = onSearch,
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.search_this_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleSortDirection) {
                Icon(
                    imageVector = if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription =
                        if (descending) {
                            stringResource(
                                R.string.sort_descending,
                            )
                        } else {
                            stringResource(R.string.sort_ascending)
                        },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            sortOptions.forEach { option ->
                FilterChip(
                    selected = listQuery.sort == option.value,
                    onClick = {
                        if (listQuery.sort == option.value) onToggleSortDirection() else onSortChange(option.value)
                    },
                    label = { Text(stringResource(option.label)) },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.filters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClearFilters,
                enabled = canClearFilters,
            ) {
                Icon(Icons.Rounded.FilterAltOff, contentDescription = stringResource(R.string.components_clear_filters))
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            FilterChip(
                selected = listQuery.downloaded,
                onClick = onToggleDownloaded,
                label = { Text(stringResource(R.string.filter_downloaded)) },
                leadingIcon =
                    if (listQuery.downloaded) {
                        {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
            if (starredSupported) {
                FilterChip(
                    selected = listQuery.starred != StarredFilter.Any,
                    onClick = onCycleStarred,
                    label = { Text(stringResource(R.string.resources_filter_starred)) },
                    leadingIcon =
                        when (listQuery.starred) {
                            StarredFilter.Any -> {
                                null
                            }

                            StarredFilter.Starred -> {
                                {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            }

                            StarredFilter.NotStarred -> {
                                {
                                    Icon(
                                        Icons.Rounded.Remove,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            }
                        },
                )
            }
        }
    }
}
