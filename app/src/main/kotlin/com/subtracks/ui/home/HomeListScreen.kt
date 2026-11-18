package com.subtracks.ui.home

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.CoverArt
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.ListDownloadIndicator
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.ShuffleFab
import com.subtracks.ui.library.AlbumCollectionRow
import com.subtracks.ui.library.PlaylistsContent
import com.subtracks.ui.library.SongListPlaybackActions
import com.subtracks.ui.library.SongRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HomeListRoute(
    request: HomeListRequest,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    onGenreClick: (String) -> Unit,
    onDecadeClick: (Long) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    viewModel: HomeListViewModel = koinViewModel { parametersOf(request) },
    playbackController: PlaybackController = koinInject(),
    topBarExtra: (@Composable () -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    onGenreStyleClick: (String?) -> Unit = {},
) {
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val favoriteQuery =
        if (request.favorites == FavoriteKind.Songs) viewModel.favoriteSongsQuery.collectAsStateWithLifecycle().value else null
    val favoriteSongs =
        remember(viewModel, favoriteQuery) {
            favoriteQuery?.let(viewModel::favoriteSongs) ?: flowOf(PagingData.empty())
        }
    val genresState = if (request.section == HomeSection.Genres) viewModel.genres.collectAsStateWithLifecycle() else null
    val genres = genresState?.value.orEmpty()
    val genreStyles = if (request.showGenreStyles) viewModel.genreStyles.collectAsLazyPagingItems() else null
    val decadesState = if (request.section == HomeSection.Decades) viewModel.decades.collectAsStateWithLifecycle() else null
    val decades = decadesState?.value.orEmpty()
    val actions =
        ItemActions(
            playNext = { playbackController.playNext(it.sourceId, it.kind, it.refId) },
            addToQueue = { playbackController.addToQueue(it.sourceId, it.kind, it.refId) },
            setStar = setStar,
            viewAlbum = onViewAlbum,
            viewArtist = onViewArtist,
        )
    HomeListScreen(
        request = request,
        albums = viewModel.albums.collectAsLazyPagingItems(),
        artists = viewModel.artists.collectAsLazyPagingItems(),
        songs = (if (request.favorites == FavoriteKind.Songs) favoriteSongs else viewModel.songs).collectAsLazyPagingItems(),
        playlists = viewModel.playlists.collectAsLazyPagingItems(),
        genres = genres,
        decades = decades,
        coverArt = viewModel::coverArt,
        artistArt = { viewModel.artistArt(it) },
        playingSongId = playback.item?.id,
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPlaylistClick = onPlaylistClick,
        onSongClick = viewModel::play,
        genreStyles = genreStyles,
        onGenreStyleClick = onGenreStyleClick,
        onPlayGenreAlbums = if (request.groupedGenre) viewModel::playGenreAlbums else null,
        onFavoriteSongClick = favoriteQuery?.let { query -> { index -> viewModel.playFavorites(query, index) } },
        onPlayFavorites = favoriteQuery?.let { query -> { viewModel.playFavorites(query, inOrder = true) } },
        onShuffleFavorites = favoriteQuery?.let { query -> { viewModel.playFavorites(query, shuffled = true) } },
        onLongClick = { contextMenuHost?.show(it, actions) },
        onShuffle = if (viewModel.canShuffle) viewModel::shuffle else null,
        onGenreClick = onGenreClick,
        onDecadeClick = onDecadeClick,
        topBarExtra = topBarExtra,
        topBarActions = topBarActions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeListScreen(
    request: HomeListRequest,
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    songs: LazyPagingItems<AlbumSongItem>,
    playlists: LazyPagingItems<Playlist>,
    modifier: Modifier = Modifier,
    genres: List<String> = emptyList(),
    decades: List<Long> = emptyList(),
    coverArt: (String?, Boolean) -> CoverArtRef? = { _, _ -> null },
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    playingSongId: String? = null,
    onBack: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onLongClick: (MenuTarget) -> Unit = {},
    onShuffle: (() -> Unit)? = null,
    onFavoriteSongClick: ((Int) -> Unit)? = null,
    onPlayFavorites: (() -> Unit)? = null,
    onShuffleFavorites: (() -> Unit)? = null,
    onGenreClick: (String) -> Unit = {},
    onDecadeClick: (Long) -> Unit = {},
    // Shown under the top bar, such as the Favorites page's Songs, Albums and Artists switcher.
    topBarExtra: (@Composable () -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    genreStyles: LazyPagingItems<AlbumCollectionSummary>? = null,
    onGenreStyleClick: (String?) -> Unit = {},
    onPlayGenreAlbums: ((Boolean) -> Unit)? = null,
) {
    val now = remember { System.currentTimeMillis() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val songListState = rememberLazyListState()
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(request.title) },
                    actions = topBarActions,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigation_back))
                        }
                    },
                    modifier =
                        if (onShuffle != null) {
                            Modifier.clickable(onClickLabel = stringResource(R.string.scroll_to_top)) {
                                scope.launch { songListState.animateScrollToItem(0) }
                            }
                        } else {
                            Modifier
                        },
                )
                topBarExtra?.invoke()
                if (albums.itemCount > 0 && onPlayGenreAlbums != null) {
                    SongListPlaybackActions({ onPlayGenreAlbums(false) }, { onPlayGenreAlbums(true) })
                }
                if (request.favorites == FavoriteKind.Songs && songs.itemCount > 0 && onPlayFavorites != null &&
                    onShuffleFavorites != null
                ) {
                    SongListPlaybackActions(onPlayFavorites, onShuffleFavorites)
                }
            }
        },
        floatingActionButton = {
            if (onShuffle != null && songs.itemCount > 0) {
                ShuffleFab(onClick = onShuffle)
            }
        },
    ) { padding ->
        if (genreStyles != null) {
            GenreStylesContent(
                genre = request.title,
                styles = genreStyles,
                coverArt = coverArt,
                onClick = onGenreStyleClick,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val favorites = request.favorites
        if (favorites != null) {
            FavoritesContent(
                kind = favorites,
                albums = albums,
                artists = artists,
                songs = songs,
                coverArt = coverArt,
                artistArt = artistArt,
                playingSongId = playingSongId,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onSongClick = onSongClick,
                onLongClick = onLongClick,
                songListState = songListState,
                onSongIndexClick = onFavoriteSongClick,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val downloaded = request.downloaded
        if (downloaded != null) {
            when (downloaded) {
                OfflineListKind.Albums -> {
                    AlbumsList(
                        albums = albums,
                        coverArt = coverArt,
                        info = { albumListInfo(request, it, now, resources) },
                        onAlbumClick = onAlbumClick,
                        onLongClick = onLongClick,
                        modifier = Modifier.padding(padding),
                    )
                }

                OfflineListKind.Artists -> {
                    ArtistsList(
                        artists = artists,
                        artistArt = artistArt,
                        info = { artistListInfo(request, it, now, resources) },
                        onArtistClick = onArtistClick,
                        onLongClick = onLongClick,
                        modifier = Modifier.padding(padding),
                    )
                }

                OfflineListKind.Songs -> {
                    SongsList(
                        songs = songs,
                        coverArt = coverArt,
                        playingSongId = playingSongId,
                        onSongClick = onSongClick,
                        onLongClick = onLongClick,
                        listState = songListState,
                        modifier = Modifier.padding(padding),
                    )
                }

                OfflineListKind.Playlists -> {
                    PlaylistsContent(
                        items = playlists,
                        coverArt = coverArt,
                        bottomInset = 0.dp,
                        onPlaylistClick = onPlaylistClick,
                        onLongClick = onLongClick,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        } else {
            when (request.section) {
                HomeSection.Genres -> {
                    PlainList(
                        items = genres,
                        label = { it },
                        onClick = onGenreClick,
                        modifier = Modifier.padding(padding),
                    )
                }

                HomeSection.Decades -> {
                    PlainList(
                        items = decades,
                        label = { stringResource(R.string.home_decade, it) },
                        onClick = onDecadeClick,
                        modifier = Modifier.padding(padding),
                    )
                }

                HomeSection.RecentlyPlayedArtists, HomeSection.MostPlayedArtists -> {
                    ArtistsList(
                        artists = artists,
                        artistArt = artistArt,
                        info = { artistListInfo(request, it, now, resources) },
                        onArtistClick = onArtistClick,
                        onLongClick = onLongClick,
                        modifier = Modifier.padding(padding),
                    )
                }

                HomeSection.RecentlyStarredSongs -> {
                    SongsList(
                        songs = songs,
                        coverArt = coverArt,
                        playingSongId = playingSongId,
                        onSongClick = onSongClick,
                        onLongClick = onLongClick,
                        listState = songListState,
                        modifier = Modifier.padding(padding),
                    )
                }

                HomeSection.RecentlyPlayedAlbums,
                HomeSection.MostPlayedAlbums,
                HomeSection.FavoriteAlbums,
                HomeSection.RecentlyAddedAlbums,
                HomeSection.Rediscover,
                null,
                -> {
                    if (request.genre != null) {
                        SongsList(
                            songs = songs,
                            coverArt = coverArt,
                            playingSongId = playingSongId,
                            onSongClick = onSongClick,
                            onLongClick = onLongClick,
                            listState = songListState,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        AlbumsList(
                            albums = albums,
                            coverArt = coverArt,
                            info = { albumListInfo(request, it, now, resources) },
                            onAlbumClick = onAlbumClick,
                            onLongClick = onLongClick,
                            modifier = Modifier.padding(padding),
                            emptyText = if (request.collection != null) R.string.collection_albums_empty else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreStylesContent(
    genre: String,
    styles: LazyPagingItems<AlbumCollectionSummary>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        item(key = "all") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.genre_all, genre)) },
                modifier = Modifier.clickable { onClick(null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (styles.itemCount > 0) {
            item(key = "heading") {
                Text(
                    stringResource(R.string.genre_styles),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(styles.itemCount, key = styles.itemKey { "style:${it.name}" }) { index ->
                val style = styles[index]
                if (style != null) AlbumCollectionRow(style, coverArt) { onClick(style.name) }
            }
        }
    }
}

@Composable
private fun <T> PlainList(
    items: List<T>,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(label(item)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onClick(item) },
            )
        }
    }
}

/** One list of the Favorites page, with a hint when it is empty. */
@Composable
private fun FavoritesContent(
    kind: FavoriteKind,
    albums: LazyPagingItems<Album>,
    artists: LazyPagingItems<Artist>,
    songs: LazyPagingItems<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    artistArt: (Artist) -> ArtistArt,
    playingSongId: String?,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSongClick: (Song) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    songListState: LazyListState,
    modifier: Modifier = Modifier,
    onSongIndexClick: ((Int) -> Unit)? = null,
) {
    val shown: LazyPagingItems<*> =
        when (kind) {
            FavoriteKind.Songs -> songs
            FavoriteKind.Albums -> albums
            FavoriteKind.Artists -> artists
        }
    if (shown.itemCount == 0 && shown.loadState.refresh is LoadState.NotLoading) {
        EmptyState(text = stringResource(R.string.favorites_empty), modifier = modifier)
        return
    }
    when (kind) {
        FavoriteKind.Songs -> {
            SongsList(songs, coverArt, playingSongId, onSongClick, onLongClick, songListState, modifier, onSongIndexClick)
        }

        FavoriteKind.Albums -> {
            AlbumsList(albums, coverArt, { it.albumArtist }, onAlbumClick, onLongClick, modifier)
        }

        FavoriteKind.Artists -> {
            ArtistsList(artists, artistArt, { null }, onArtistClick, onLongClick, modifier)
        }
    }
}

@Composable
private fun SongsList(
    songs: LazyPagingItems<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    onSongClick: (Song) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onSongIndexClick: ((Int) -> Unit)? = null,
) {
    if (songs.itemCount == 0 && songs.loadState.refresh is LoadState.Loading) {
        LoadingState(modifier)
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(count = songs.itemCount, key = songs.itemKey { it.song.id }) { index ->
            val item = songs[index]
            if (item != null) {
                SongRow(
                    song = item.song,
                    coverArtId = item.coverArt,
                    coverArt = coverArt,
                    isPlaying = item.song.id == playingSongId,
                    durationSeconds = item.song.duration,
                    modifier =
                        Modifier.combinedClickable(
                            onClick = { if (onSongIndexClick != null) onSongIndexClick(index) else onSongClick(item.song) },
                            onLongClick = { onLongClick(MenuTarget.Song(item.song, coverArt(item.coverArt, true))) },
                        ),
                )
            }
        }
    }
}

private val ALBUM_ROW_COVER = 48.dp

@Composable
private fun AlbumsList(
    albums: LazyPagingItems<Album>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    info: (Album) -> String?,
    onAlbumClick: (Album) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    modifier: Modifier = Modifier,
    // Shown once the list has loaded with nothing in it; null keeps the list blank.
    @StringRes emptyText: Int? = null,
) {
    if (albums.itemCount == 0 && albums.loadState.refresh is LoadState.Loading) {
        LoadingState(modifier)
        return
    }
    if (emptyText != null && albums.itemCount == 0 && albums.loadState.refresh is LoadState.NotLoading) {
        EmptyState(text = stringResource(emptyText), modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(count = albums.itemCount, key = albums.itemKey { it.id }) { index ->
            val album = albums[index]
            if (album != null) {
                AlbumListRow(
                    album = album,
                    coverArt = coverArt,
                    info = info(album),
                    onClick = { onAlbumClick(album) },
                    onLongClick = { onLongClick(MenuTarget.Album(album, coverArt(album.coverArt, true))) },
                )
            }
        }
    }
}

@Composable
internal fun AlbumListRow(
    album: Album,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    info: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    downloadStatus: ListDownloadStatus? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            ref = coverArt(album.coverArt, true),
            name = album.name,
            modifier = Modifier.size(ALBUM_ROW_COVER).clip(RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = album.albumArtist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (info != null) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        ListDownloadIndicator(downloadStatus, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ArtistsList(
    artists: LazyPagingItems<Artist>,
    artistArt: (Artist) -> ArtistArt,
    info: (Artist) -> String?,
    onArtistClick: (Artist) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.itemCount == 0 && artists.loadState.refresh is LoadState.Loading) {
        LoadingState(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(count = artists.itemCount, key = artists.itemKey { it.id }) { index ->
            val artist = artists[index]
            if (artist != null) {
                val art = artistArt(artist)
                ArtistListRow(
                    artist = artist,
                    art = art,
                    info = info(artist),
                    onClick = { onArtistClick(artist) },
                    onLongClick = { onLongClick(MenuTarget.Artist(artist, art.primary)) },
                )
            }
        }
    }
}

@Composable
internal fun ArtistListRow(
    artist: Artist,
    art: ArtistArt,
    info: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistIcon(
            artist = artist,
            art = art,
            modifier = Modifier.size(ALBUM_ROW_COVER).clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.resources_album_count, artist.albumCount.toInt(), artist.albumCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (info != null) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun artistListInfo(
    request: HomeListRequest,
    artist: Artist,
    now: Long,
    resources: Resources,
): String? =
    when (request.section) {
        HomeSection.RecentlyPlayedArtists -> artist.played?.let { relativeTimeLabel(it, now, resources) }
        HomeSection.MostPlayedArtists -> playCountLabel(resources, artist.playCount)
        else -> null
    }

internal fun albumListInfo(
    request: HomeListRequest,
    album: Album,
    now: Long,
    resources: Resources,
): String? =
    when {
        request.section == HomeSection.RecentlyAddedAlbums -> {
            relativeTimeLabel(album.created, now, resources)
        }

        request.section == HomeSection.RecentlyPlayedAlbums || request.section == HomeSection.Rediscover -> {
            album.played?.let { relativeTimeLabel(it, now, resources) }
        }

        request.section == HomeSection.MostPlayedAlbums -> {
            playCountLabel(resources, album.playCount)
        }

        request.decade != null || request.collection != null -> {
            album.year?.takeIf { it > 0 }?.toString()
        }

        else -> {
            null
        }
    }

private fun playCountLabel(
    resources: Resources,
    count: Long,
): String? = if (count <= 0L) null else resources.getQuantityString(R.plurals.home_play_count, count.toInt(), count)

internal fun relativeTimeLabel(
    epochSeconds: Long,
    nowMillis: Long,
    resources: Resources,
): String? {
    if (epochSeconds <= 0L) return null
    val elapsedMillis = nowMillis - epochSeconds * 1000L
    if (elapsedMillis < 0L) return resources.getString(R.string.home_time_just_now)
    val days = elapsedMillis / 86_400_000L
    return when {
        elapsedMillis < 60_000L -> resources.getString(R.string.home_time_just_now)
        days == 0L -> resources.getString(R.string.home_time_today)
        days == 1L -> resources.getString(R.string.home_time_yesterday)
        days < 7L -> resources.getQuantityString(R.plurals.home_time_days_ago, days.toInt(), days)
        days < 14L -> resources.getString(R.string.home_time_last_week)
        days < 30L -> resources.getQuantityString(R.plurals.home_time_weeks_ago, (days / 7L).toInt(), days / 7L)
        days < 60L -> resources.getString(R.string.home_time_last_month)
        days < 365L -> resources.getQuantityString(R.plurals.home_time_months_ago, (days / 30L).toInt(), days / 30L)
        days < 730L -> resources.getString(R.string.home_time_last_year)
        else -> resources.getQuantityString(R.plurals.home_time_years_ago, (days / 365L).toInt(), days / 365L)
    }
}
