package com.subtracks.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.HomeGenreShelf
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.HomeLayout
import com.subtracks.data.prefs.HomeShelf
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.CoverArt
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.SONG_CAROUSEL_ROWS
import com.subtracks.ui.components.SectionOpenArrow
import com.subtracks.ui.components.SongCarousel
import com.subtracks.ui.components.playlistUpdatedText
import com.subtracks.ui.components.sectionOpenAction
import com.subtracks.ui.components.statusBarScrim
import com.subtracks.ui.discover.QueueBuilderRoute
import com.subtracks.ui.discover.rememberSongPathCard
import com.subtracks.ui.library.SongRow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

enum class HomeSection(
    @param:StringRes val title: Int,
) {
    RecentlyPlayedAlbums(R.string.resources_sort_by_recently_played),
    RecentlyPlayedArtists(R.string.home_section_recently_played_artists),
    MostPlayedAlbums(R.string.home_section_most_played),
    MostPlayedArtists(R.string.home_section_most_played_artists),
    Genres(R.string.home_section_genres),
    Decades(R.string.home_section_decades),
    FavoriteAlbums(R.string.home_section_favorite_albums),
    RecentlyStarredSongs(R.string.home_section_recently_starred),
    RecentlyAddedAlbums(R.string.home_section_recently_added),
    Rediscover(R.string.home_section_rediscover),
    ;

    companion object {
        fun fromRoute(value: String?): HomeSection? = entries.firstOrNull { it.name == value }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    onMore: (HomeSection) -> Unit,
    onDownloadedMore: (OfflineListKind) -> Unit,
    onGenreClick: (String) -> Unit,
    onDecadeClick: (Long) -> Unit,
    onPlaylistsMore: () -> Unit = {},
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
    playbackController: PlaybackController = koinInject(),
) {
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val actions =
        ItemActions(
            playNext = { playbackController.playNext(it.sourceId, it.kind, it.refId) },
            addToQueue = { playbackController.addToQueue(it.sourceId, it.kind, it.refId) },
            setStar = setStar,
            viewAlbum = onViewAlbum,
            viewArtist = onViewArtist,
        )
    val onLongClick: (MenuTarget) -> Unit = { contextMenuHost?.show(it, actions) }
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::sync,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        HomeFeedContent(
            offline = offline,
            viewModel = viewModel,
            playingSongId = playback.item?.id,
            topInset = topInset,
            bottomInset = bottomInset,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onMore = onMore,
            onDownloadedMore = onDownloadedMore,
            onGenreClick = onGenreClick,
            onDecadeClick = onDecadeClick,
            onLongClick = onLongClick,
            onPlaylistsMore = onPlaylistsMore,
        )
        Spacer(Modifier.align(Alignment.TopStart).statusBarScrim())
        if (onOpenSettings != null) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 4.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.navigation_tabs_settings))
            }
        }
    }
}

@Composable
private fun HomeFeedContent(
    offline: Boolean,
    viewModel: HomeViewModel,
    playingSongId: String?,
    topInset: Dp,
    bottomInset: Dp,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onMore: (HomeSection) -> Unit,
    onDownloadedMore: (OfflineListKind) -> Unit,
    onGenreClick: (String) -> Unit,
    onDecadeClick: (Long) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onPlaylistsMore: () -> Unit,
) {
    val layout by viewModel.homeLayout.collectAsStateWithLifecycle()
    val genreShelves =
        key(offline) {
            remember(viewModel, offline) { viewModel.genreShelves(offline) }.collectAsStateWithLifecycle(emptyList()).value
        }
    val artistPicks by viewModel.artistPicks.collectAsStateWithLifecycle()
    val onPinnedClick: (PinnedItem) -> Unit = { item ->
        viewModel.withPinTarget(item) { target ->
            when (target) {
                is MenuTarget.Album -> onAlbumClick(target.album)
                is MenuTarget.Artist -> onArtistClick(target.artist)
                is MenuTarget.Playlist -> onPlaylistClick(target.playlist)
                is MenuTarget.Song -> Unit
            }
        }
    }
    val onPinnedLongClick: (PinnedItem) -> Unit = { item -> viewModel.withPinTarget(item, onLongClick) }
    if (offline) {
        OfflineHomeScreen(
            layout = layout,
            genreShelves = genreShelves,
            onGenreClick = onGenreClick,
            pins = viewModel.downloadedPins.collectAsLazyPagingItems(),
            queueBuilderContent = { QueueBuilderRoute() },
            onPinnedClick = onPinnedClick,
            onPinnedLongClick = onPinnedLongClick,
            albums = viewModel.downloadedAlbums.collectAsLazyPagingItems(),
            artists = viewModel.downloadedArtists.collectAsLazyPagingItems(),
            playlists = viewModel.downloadedPlaylists.collectAsLazyPagingItems(),
            songs = viewModel.downloadedSongs.collectAsStateWithLifecycle(initialValue = emptyList()).value,
            coverArt = viewModel::coverArt,
            artistArt = { viewModel.artistArt(it) },
            playingSongId = playingSongId,
            topInset = topInset,
            bottomInset = bottomInset,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onSongPlay = viewModel::playDownloaded,
            onLongClick = onLongClick,
            onMore = onDownloadedMore,
            offlineSongCount = viewModel.offlineSongCount.collectAsStateWithLifecycle().value,
            onTurnOffOffline = viewModel::turnOffOfflineMode,
        )
    } else {
        val feed by viewModel.feed.collectAsStateWithLifecycle()
        val songPath = if (HomeShelf.SongPath in layout.visible) rememberSongPathCard(koinViewModel()) else null
        HomeScreen(
            feed = feed,
            genreShelves = genreShelves,
            layout = layout,
            pins = viewModel.pins.collectAsLazyPagingItems(),
            onPinnedClick = onPinnedClick,
            onPinnedLongClick = onPinnedLongClick,
            coverArt = viewModel::coverArt,
            artistArt = { viewModel.artistArt(it) },
            playingSongId = playingSongId,
            topInset = topInset,
            bottomInset = bottomInset,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlaylistClick = onPlaylistClick,
            onPlaylistsMore = onPlaylistsMore,
            onPlayStarred = viewModel::playStarred,
            artistPicks = artistPicks,
            queueBuilderContent = { QueueBuilderRoute() },
            songPathContent = songPath,
            onLongClick = onLongClick,
            onMore = onMore,
            onGenreClick = onGenreClick,
            onDecadeClick = onDecadeClick,
            onSync = viewModel::sync,
        )
    }
}

@Composable
fun HomeScreen(
    feed: HomeFeed,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    modifier: Modifier = Modifier,
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onPlaylistsMore: () -> Unit = {},
    onPlayStarred: (Song) -> Unit = {},
    artistPicks: List<Artist> = emptyList(),
    onLongClick: (MenuTarget) -> Unit = {},
    onMore: (HomeSection) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onDecadeClick: (Long) -> Unit = {},
    onSync: () -> Unit = {},
    layout: HomeLayout = HomeLayout(),
    pins: LazyPagingItems<PinnedItem>? = null,
    onPinnedClick: (PinnedItem) -> Unit = {},
    onPinnedLongClick: (PinnedItem) -> Unit = {},
    queueBuilderContent: (@Composable () -> Unit)? = null,
    songPathContent: (@Composable () -> Unit)? = null,
    genreShelves: List<HomeGenreShelf> = emptyList(),
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        val empty = feed.isEmpty() && (genreShelves.isEmpty() || HomeShelf.Genres in layout.hidden)
        if (feed.loading || (empty && pins?.itemCount == 0 && pins.loadState.refresh is androidx.paging.LoadState.Loading)) {
            LoadingState(modifier)
            return@CompositionLocalProvider
        }
        if (empty && (pins?.itemCount ?: 0) == 0) {
            EmptyState(
                text = stringResource(R.string.home_empty),
                modifier = modifier,
                actionLabel = stringResource(R.string.sync),
                onAction = onSync,
            )
            return@CompositionLocalProvider
        }
        BoxWithConstraints(modifier.fillMaxSize()) {
            val gridColumns = homeGridColumns(maxWidth, LocalDensity.current.fontScale)
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("homeList"),
                contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomInset + 24.dp),
            ) {
                homeTitle()
                for (shelf in layout.visible) {
                    if (shelf == HomeShelf.Pinned) {
                        pinnedSection(pins, shelf in layout.grids, gridColumns, coverArt, onPinnedClick, onPinnedLongClick)
                        continue
                    }
                    if (shelf == HomeShelf.YourPlaylists) {
                        yourPlaylistsRow(
                            feed.yourPlaylists,
                            coverArt,
                            onPlaylistClick,
                            onLongClick,
                            onPlaylistsMore,
                            if (shelf in layout.grids) gridColumns else null,
                        )
                        continue
                    }
                    if (shelf == HomeShelf.QueueBuilder) {
                        queueBuilderContent?.let { queueBuilderItem(it) }
                        continue
                    }
                    if (shelf == HomeShelf.SongPath) {
                        songPathContent?.let { songPathItem(it) }
                        continue
                    }
                    if (shelf == HomeShelf.ArtistPicks) {
                        artistRow(
                            HomeSection.MostPlayedArtists,
                            artistPicks,
                            coverArt,
                            artistArt,
                            onArtistClick,
                            onLongClick,
                            onMore,
                            title = R.string.home_section_artist_picks,
                            key = "artist-picks",
                            showMore = false,
                        )
                        continue
                    }
                    val section = HomeSection.valueOf(shelf.name)
                    when (shelf) {
                        HomeShelf.YourPlaylists -> {
                            Unit
                        }

                        HomeShelf.RecentlyPlayedArtists, HomeShelf.MostPlayedArtists -> {
                            artistRow(
                                section,
                                if (shelf ==
                                    HomeShelf.RecentlyPlayedArtists
                                ) {
                                    feed.recentlyPlayedArtists
                                } else {
                                    feed.mostPlayedArtists
                                },
                                coverArt,
                                artistArt,
                                onArtistClick,
                                onLongClick,
                                onMore,
                            )
                        }

                        HomeShelf.RecentlyStarredSongs -> {
                            favoriteSongColumns(
                                section,
                                feed.recentlyStarredSongs,
                                coverArt,
                                playingSongId,
                                onPlayStarred,
                                onLongClick,
                                onMore,
                            )
                        }

                        HomeShelf.Genres -> {
                            genreGrids(genreShelves, gridColumns, coverArt, onAlbumClick, onLongClick, onGenreClick)
                        }

                        HomeShelf.Decades -> {
                            decadeRow(section, feed.decades, onDecadeClick, onMore)
                        }

                        else -> {
                            val albums =
                                when (shelf) {
                                    HomeShelf.FavoriteAlbums -> feed.favoriteAlbums
                                    HomeShelf.RecentlyAddedAlbums -> feed.recentlyAddedAlbums
                                    HomeShelf.MostPlayedAlbums -> feed.mostPlayedAlbums
                                    HomeShelf.RecentlyPlayedAlbums -> feed.recentlyPlayedAlbums
                                    else -> feed.rediscoverAlbums
                                }
                            albumRow(
                                section,
                                albums,
                                coverArt,
                                onAlbumClick,
                                onLongClick,
                                onMore,
                                if (shelf in
                                    layout.grids
                                ) {
                                    gridColumns
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.homeTitle() {
    item(key = "home-title") {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_maekster_mark),
                contentDescription = null,
                modifier = Modifier.size(width = 52.dp, height = 24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun OfflineHomeScreen(
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    playlists: LazyPagingItems<Playlist>,
    songs: List<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    modifier: Modifier = Modifier,
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onSongPlay: (Song) -> Unit = {},
    onLongClick: (MenuTarget) -> Unit = {},
    onMore: (OfflineListKind) -> Unit = {},
    layout: HomeLayout = HomeLayout(),
    pins: LazyPagingItems<PinnedItem>? = null,
    onPinnedClick: (PinnedItem) -> Unit = {},
    onPinnedLongClick: (PinnedItem) -> Unit = {},
    queueBuilderContent: (@Composable () -> Unit)? = null,
    offlineSongCount: Int? = null,
    onTurnOffOffline: (() -> Unit)? = null,
    genreShelves: List<HomeGenreShelf> = emptyList(),
    onGenreClick: (String) -> Unit = {},
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val gridColumns = homeGridColumns(maxWidth, LocalDensity.current.fontScale)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topInset + 8.dp, bottom = bottomInset + 24.dp),
            ) {
                homeTitle()
                if (onTurnOffOffline != null) {
                    item(key = "offline-card") {
                        OfflineHomeCard(
                            songCount = offlineSongCount,
                            onBrowse = { onMore(OfflineListKind.Songs) },
                            onTurnOff = onTurnOffOffline,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                for (shelf in layout.visible) {
                    when (shelf) {
                        HomeShelf.Pinned -> {
                            pinnedSection(
                                pins,
                                shelf in layout.grids,
                                gridColumns,
                                coverArt,
                                onPinnedClick,
                                onPinnedLongClick,
                            )
                        }

                        HomeShelf.QueueBuilder -> {
                            queueBuilderContent?.let { queueBuilderItem(it) }
                        }

                        HomeShelf.Genres -> {
                            genreGrids(genreShelves, gridColumns, coverArt, onAlbumClick, onLongClick, onGenreClick)
                        }

                        else -> {
                            Unit
                        }
                    }
                }
                downloadedAlbumRow(albums, coverArt, onAlbumClick, onLongClick) { onMore(OfflineListKind.Albums) }
                downloadedArtistRow(artists, artistArt, onArtistClick, onLongClick) { onMore(OfflineListKind.Artists) }
                downloadedPlaylistRow(playlists, coverArt, onPlaylistClick, onLongClick) { onMore(OfflineListKind.Playlists) }
                songList(
                    key = "downloaded-songs",
                    title = R.string.home_downloaded_songs,
                    icon = Icons.Rounded.MusicNote,
                    songs = songs,
                    coverArt = coverArt,
                    playingSongId = playingSongId,
                    onSongPlay = onSongPlay,
                    onLongClick = onLongClick,
                    onMore = { onMore(OfflineListKind.Songs) },
                )
            }
        }
    }
}

private fun LazyListScope.downloadedAlbumRow(
    albums: LazyPagingItems<Album>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: () -> Unit,
) {
    if (albums.itemCount == 0) return
    item(key = "downloaded-albums-header") {
        HomeSectionHeader(R.string.home_downloaded_albums, Icons.Rounded.Album) { onMore() }
    }
    item(key = "downloaded-albums-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(count = albums.itemCount, key = albums.itemKey { it.id }) { index ->
                val album = albums[index]
                if (album != null) {
                    val ref = coverArt(album.coverArt, true)
                    AlbumTile(
                        album = album,
                        ref = ref,
                        onClick = { onAlbumClick(album) },
                        onLongClick = { onLongClick(MenuTarget.Album(album, ref)) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.downloadedArtistRow(
    artists: LazyPagingItems<Artist>,
    artistArt: (Artist) -> ArtistArt,
    onArtistClick: (Artist) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: () -> Unit,
) {
    if (artists.itemCount == 0) return
    item(key = "downloaded-artists-header") {
        HomeSectionHeader(R.string.home_downloaded_artists, Icons.Rounded.Person) { onMore() }
    }
    item(key = "downloaded-artists-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ARTIST_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(count = artists.itemCount, key = artists.itemKey { it.id }) { index ->
                val artist = artists[index]
                if (artist != null) {
                    val art = artistArt(artist)
                    ArtistTile(
                        artist = artist,
                        art = art,
                        onClick = { onArtistClick(artist) },
                        onLongClick = { onLongClick(MenuTarget.Artist(artist, art.primary)) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.downloadedPlaylistRow(
    playlists: LazyPagingItems<Playlist>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onPlaylistClick: (Playlist) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: () -> Unit,
) {
    if (playlists.itemCount == 0) return
    item(key = "downloaded-playlists-header") {
        HomeSectionHeader(R.string.home_downloaded_playlists, Icons.AutoMirrored.Rounded.PlaylistPlay) { onMore() }
    }
    item(key = "downloaded-playlists-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(count = playlists.itemCount, key = playlists.itemKey { it.id }) { index ->
                val playlist = playlists[index]
                if (playlist != null) {
                    val ref = coverArt(playlist.coverArt, true)
                    PlaylistTile(
                        playlist = playlist,
                        ref = ref,
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = { onLongClick(MenuTarget.Playlist(playlist, ref)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    playlist: Playlist,
    ref: CoverArtRef?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(TILE_SIZE).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CoverArt(
            ref = ref,
            name = playlist.name,
            modifier = Modifier.size(TILE_SIZE).clip(RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Your playlists, first on Home: large covers, newest change first, each with when it was updated. */
private fun LazyListScope.yourPlaylistsRow(
    playlists: List<Playlist>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onPlaylistClick: (Playlist) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: () -> Unit,
    gridColumns: Int? = null,
) {
    if (playlists.isEmpty()) return
    item(key = "your-playlists-header") {
        HomeSectionHeader(R.string.home_section_your_playlists, Icons.AutoMirrored.Rounded.PlaylistPlay, onMore)
    }
    if (gridColumns != null) {
        item(key = "your-playlists-grid") {
            HomeGrid(playlists, gridColumns, "YourPlaylists-grid") { playlist, modifier ->
                val ref = coverArt(playlist.coverArt, true)
                val count = playlist.songCount.toInt()
                HomeGridTile(
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.resources_song_count, count, count),
                    ref = ref,
                    onClick = { onPlaylistClick(playlist) },
                    onLongClick = { onLongClick(MenuTarget.Playlist(playlist, ref)) },
                    modifier = modifier,
                )
            }
        }
        return
    }
    item(key = "your-playlists-row") {
        val rowState = rememberLazyListState()
        RevealNewFrontItem(playlists, rowState) { it.id }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING * 2),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP).testTag(YOUR_PLAYLISTS_TAG),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                // The large card shows the full cover, not the thumbnail.
                val ref = coverArt(playlist.coverArt, false)
                val thumbnail = coverArt(playlist.coverArt, true)
                RecentPlaylistCard(
                    playlist = playlist,
                    ref = ref,
                    thumbnailRef = thumbnail,
                    onClick = { onPlaylistClick(playlist) },
                    onLongClick = { onLongClick(MenuTarget.Playlist(playlist, thumbnail)) },
                )
            }
        }
    }
}

@Composable
private fun RecentPlaylistCard(
    playlist: Playlist,
    ref: CoverArtRef?,
    thumbnailRef: CoverArtRef?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val resources = LocalResources.current
    val updated = remember(playlist.changed, resources) { playlistUpdatedText(resources, playlist.changed) }
    val count = playlist.songCount.toInt()
    Column(
        modifier = Modifier.width(PLAYLIST_CARD_SIZE).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CoverArt(
            ref = ref,
            name = playlist.name,
            thumbnailRef = thumbnailRef,
            modifier = Modifier.size(PLAYLIST_CARD_SIZE).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (updated != null) {
            Text(
                text = updated,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.resources_song_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal const val YOUR_PLAYLISTS_TAG = "yourPlaylists"
private val PLAYLIST_CARD_SIZE = 200.dp

private fun HomeFeed.isEmpty(): Boolean =
    yourPlaylists.isEmpty() &&
        favoriteAlbums.isEmpty() &&
        recentlyPlayedAlbums.isEmpty() &&
        recentlyPlayedArtists.isEmpty() &&
        mostPlayedAlbums.isEmpty() &&
        mostPlayedArtists.isEmpty() &&
        decades.isEmpty() &&
        recentlyStarredSongs.isEmpty() &&
        recentlyAddedAlbums.isEmpty() &&
        rediscoverAlbums.isEmpty()

private fun LazyListScope.queueBuilderItem(content: @Composable () -> Unit) {
    item(key = "queue-builder") { Column(Modifier.padding(top = SECTION_TOP)) { content() } }
}

private fun LazyListScope.songPathItem(content: @Composable () -> Unit) {
    item(key = "song-path") { Column(Modifier.padding(top = SECTION_TOP)) { content() } }
}

@Composable
private fun HomeSectionHeader(
    @StringRes title: Int,
    icon: ImageVector,
    onMore: (() -> Unit)?,
) {
    HomeSectionHeader(stringResource(title), icon, onMore)
}

@Composable
private fun HomeSectionHeader(
    text: String,
    icon: ImageVector,
    onMore: (() -> Unit)?,
) {
    val openLabel = stringResource(R.string.section_open, text)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = SECTION_TOP)
                .then(if (onMore != null) Modifier.sectionOpenAction(openLabel, onMore) else Modifier.heightIn(min = 48.dp))
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (onMore != null) {
            Spacer(Modifier.width(8.dp))
            SectionOpenArrow()
        }
    }
}

private val HomeSection.icon: ImageVector
    get() =
        when (this) {
            HomeSection.RecentlyPlayedAlbums -> Icons.Rounded.History
            HomeSection.RecentlyPlayedArtists -> Icons.Rounded.Groups
            HomeSection.MostPlayedAlbums -> Icons.AutoMirrored.Rounded.TrendingUp
            HomeSection.MostPlayedArtists -> Icons.Rounded.LocalFireDepartment
            HomeSection.Genres -> Icons.Rounded.Category
            HomeSection.Decades -> Icons.Rounded.CalendarMonth
            HomeSection.FavoriteAlbums -> Icons.Rounded.Favorite
            HomeSection.RecentlyStarredSongs -> Icons.Rounded.Favorite
            HomeSection.RecentlyAddedAlbums -> Icons.Rounded.LibraryAdd
            HomeSection.Rediscover -> Icons.Rounded.Replay
        }

private val TILE_SIZE = 132.dp
private val ARTIST_TILE_SIZE = 104.dp
private val TILE_SPACING = 8.dp
private val ARTIST_SPACING = 16.dp
private val SECTION_TOP = 24.dp
private val SECTION_CONTENT_TOP = 4.dp
private const val REVEAL_NEAR_FRONT = 1

internal fun shouldRevealNewFront(
    previousKey: Any?,
    firstKey: Any?,
    insertedCount: Int,
    firstVisibleItemIndex: Int,
): Boolean =
    firstKey != null &&
        previousKey != null &&
        previousKey != firstKey &&
        insertedCount >= 0 &&
        firstVisibleItemIndex - insertedCount <= REVEAL_NEAR_FRONT

/**
 * Scrolls a row back to the front when a new first item appears while it is already at/near the
 * start. The lazy layout anchors to the previously visible key, so a row at the start advances by
 * the number of prepended items; subtracting that recovers the position the user was actually at.
 */
@Composable
internal fun <T> RevealNewFrontItem(
    items: List<T>,
    state: LazyListState,
    key: (T) -> Any?,
) {
    val firstKey = items.firstOrNull()?.let(key)
    var previous by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(firstKey) {
        val oldFront = previous
        val inserted = if (oldFront != null) items.indexOfFirst { key(it) == oldFront } else -1
        if (shouldRevealNewFront(oldFront, firstKey, inserted, state.firstVisibleItemIndex)) {
            state.animateScrollToItem(0)
        }
        if (firstKey != null) previous = firstKey
    }
}

private fun LazyListScope.albumRow(
    section: HomeSection,
    albums: List<Album>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: (HomeSection) -> Unit,
    gridColumns: Int? = null,
) {
    if (albums.isEmpty()) return
    item(key = "${section.name}-header") {
        HomeSectionHeader(section.title, section.icon) { onMore(section) }
    }
    if (gridColumns != null) {
        homeAlbumGrid("${section.name}-grid", albums, gridColumns, coverArt, onAlbumClick, onLongClick)
        return
    }
    item(key = "${section.name}-row") {
        val rowState = rememberLazyListState()
        RevealNewFrontItem(albums, rowState) { it.id }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(albums, key = { it.id }) { album ->
                val ref = coverArt(album.coverArt, true)
                AlbumTile(
                    album = album,
                    ref = ref,
                    onClick = { onAlbumClick(album) },
                    onLongClick = { onLongClick(MenuTarget.Album(album, ref)) },
                )
            }
        }
    }
}

private fun LazyListScope.artistRow(
    section: HomeSection,
    artists: List<Artist>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    artistArt: (Artist) -> ArtistArt,
    onArtistClick: (Artist) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: (HomeSection) -> Unit,
    title: Int = section.title,
    key: String = section.name,
    showMore: Boolean = true,
) {
    if (artists.isEmpty()) return
    item(key = "$key-header") {
        HomeSectionHeader(title, section.icon, if (showMore) ({ onMore(section) }) else null)
    }
    item(key = "$key-row") {
        val rowState = rememberLazyListState()
        RevealNewFrontItem(artists, rowState) { it.id }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(ARTIST_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(artists, key = { it.id }) { artist ->
                val art = artistArt(artist)
                ArtistTile(
                    artist = artist,
                    art = art,
                    onClick = { onArtistClick(artist) },
                    onLongClick = { onLongClick(MenuTarget.Artist(artist, art.primary)) },
                )
            }
        }
    }
}

private fun LazyListScope.genreGrids(
    shelves: List<HomeGenreShelf>,
    columns: Int,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onGenreClick: (String) -> Unit,
) {
    for (shelf in shelves) {
        val key = "genre-${shelf.genre}"
        item(key = "$key-header") {
            HomeSectionHeader(shelf.genre, HomeSection.Genres.icon) { onGenreClick(shelf.genre) }
        }
        homeAlbumGrid("$key-grid", shelf.albums, columns, coverArt, onAlbumClick, onLongClick)
    }
}

private fun LazyListScope.homeAlbumGrid(
    key: String,
    albums: List<Album>,
    columns: Int,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
) {
    item(key = key) {
        HomeGrid(albums, columns, key) { album, modifier ->
            val ref = coverArt(album.coverArt, true)
            HomeGridTile(
                title = album.name,
                subtitle = album.albumArtist,
                ref = ref,
                onClick = { onAlbumClick(album) },
                onLongClick = { onLongClick(MenuTarget.Album(album, ref)) },
                modifier = modifier,
            )
        }
    }
}

private fun LazyListScope.decadeRow(
    section: HomeSection,
    decades: List<Long>,
    onDecadeClick: (Long) -> Unit,
    onMore: (HomeSection) -> Unit,
) {
    if (decades.isEmpty()) return
    item(key = "${section.name}-header") {
        HomeSectionHeader(section.title, section.icon) { onMore(section) }
    }
    item(key = "${section.name}-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
            modifier = Modifier.padding(top = SECTION_CONTENT_TOP),
        ) {
            items(decades, key = { it }) { decade ->
                AssistChip(onClick = { onDecadeClick(decade) }, label = { Text(stringResource(R.string.home_decade, decade)) })
            }
        }
    }
}

/**
 * Favorite songs in a [SongCarousel]; the row jumps back to its front when a newly starred song arrives.
 */
private fun LazyListScope.favoriteSongColumns(
    section: HomeSection,
    songs: List<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    onSongPlay: (Song) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: (HomeSection) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "${section.name}-header") {
        HomeSectionHeader(section.title, section.icon) { onMore(section) }
    }
    item(key = "${section.name}-columns") {
        val columns = remember(songs) { songs.chunked(SONG_CAROUSEL_ROWS) }
        val rowState = rememberLazyListState()
        RevealNewFrontItem(columns, rowState) { it.first().song.id }
        SongCarousel(
            songs = songs,
            coverArt = coverArt,
            playingSongId = playingSongId,
            onSongClick = { _, item -> onSongPlay(item.song) },
            onLongClick = onLongClick,
            state = rowState,
            modifier = Modifier.testTag(FAVORITE_SONGS_TAG),
        )
    }
}

internal const val FAVORITE_SONGS_TAG = "favoriteSongs"

private fun LazyListScope.songList(
    key: String,
    @StringRes title: Int,
    icon: ImageVector,
    songs: List<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    onSongPlay: (Song) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    onMore: (() -> Unit)?,
) {
    if (songs.isEmpty()) return
    item(key = "$key-header") {
        HomeSectionHeader(title, icon, onMore)
    }
    items(songs, key = { it.song.id }) { item ->
        SongRow(
            song = item.song,
            coverArtId = item.coverArt,
            coverArt = coverArt,
            isPlaying = item.song.id == playingSongId,
            durationSeconds = item.song.duration,
            modifier =
                Modifier.combinedClickable(
                    onClick = { onSongPlay(item.song) },
                    onLongClick = { onLongClick(MenuTarget.Song(item.song, coverArt(item.coverArt, true))) },
                ),
        )
    }
}

@Composable
private fun AlbumTile(
    album: Album,
    ref: CoverArtRef?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(TILE_SIZE).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CoverArt(
            ref = ref,
            name = album.name,
            modifier = Modifier.size(TILE_SIZE).clip(RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.albumArtist.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistTile(
    artist: Artist,
    art: ArtistArt,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(ARTIST_TILE_SIZE).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistIcon(
            artist = artist,
            art = art,
            modifier = Modifier.size(ARTIST_TILE_SIZE).clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
