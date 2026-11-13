package com.subtracks.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.BulkDownloadAction
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSongItem
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.CloseWhenDownloadsGone
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.DismissOnRequest
import com.subtracks.ui.components.HeroDetailScaffold
import com.subtracks.ui.components.HeroHeader
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.PendingDownloadDeleteConfirmation
import com.subtracks.ui.components.playlistUpdatedText
import com.subtracks.ui.components.rememberPendingDownloadDelete
import com.subtracks.ui.playlist.DeletePlaylistRequest
import com.subtracks.ui.playlist.DeletePlaylistRoute
import com.subtracks.ui.playlist.PlaylistMoveRecoveryDialog
import com.subtracks.ui.playlist.RenamePlaylistRequest
import com.subtracks.ui.playlist.RenamePlaylistRoute
import com.subtracks.ui.theme.ArtworkColors
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.rememberArtworkColors
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val DOT = "\u00B7"
private const val THEME_TRANSITION_MS = 100

@Composable
fun PlaylistDetailRoute(
    playlistId: String,
    onBack: () -> Unit,
    onViewAlbum: (String) -> Unit,
    onViewArtist: (String) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    viewModel: PlaylistDetailViewModel = koinViewModel(key = playlistId) { parametersOf(playlistId) },
    playbackController: PlaybackController = koinInject(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val canEdit by viewModel.canEdit.collectAsStateWithLifecycle()
    val moving by viewModel.moving.collectAsStateWithLifecycle()
    val moveError by viewModel.moveError.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<RenamePlaylistRequest?>(null) }
    var deleting by remember { mutableStateOf<DeletePlaylistRequest?>(null) }
    CloseWhenDownloadsGone(downloadStatus.downloaded, offline, onBack)
    val pendingDelete = rememberPendingDownloadDelete(contextMenuHost?.dismissals ?: emptyFlow()) { _, _ -> viewModel.downloadedBytes() }
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val context = playback.context
    val requestDownloadAction: (BulkDownloadAction) -> Unit = { action ->
        pendingDelete.onAction(playlist?.name.orEmpty(), DownloadList.Playlist, playlistId, action) { _, _, requested ->
            viewModel.onDownloadAction(requested)
        }
    }
    val actions =
        ItemActions(
            playNext = { playbackController.playNext(it.sourceId, it.kind, it.refId) },
            addToQueue = { playbackController.addToQueue(it.sourceId, it.kind, it.refId) },
            download = viewModel::download,
            cancelDownload = viewModel::cancelDownload,
            deleteDownload = viewModel::deleteDownload,
            bulkDownload = { _, action -> requestDownloadAction(action) },
            setStar = setStar,
            viewAlbum = onViewAlbum,
            viewArtist = onViewArtist,
            moveUpInPlaylist =
                if (canEdit && !moving) {
                    { viewModel.moveFromPlaylist(it, -1) }
                } else {
                    null
                },
            moveDownInPlaylist =
                if (canEdit && !moving) {
                    { viewModel.moveFromPlaylist(it, 1) }
                } else {
                    null
                },
            removeFromPlaylist =
                if (canEdit) {
                    { target -> target.playlistPosition?.let { viewModel.removeFromPlaylist(it, target.song.id) } }
                } else {
                    null
                },
            renamePlaylist =
                if (canEdit) {
                    { renaming = RenamePlaylistRequest(it.sourceId, it.id, it.name) }
                } else {
                    null
                },
            deletePlaylist =
                if (canEdit) {
                    { deleting = DeletePlaylistRequest(it.sourceId, it.id, it.name, hasDownloads = downloadStatus.downloaded > 0) }
                } else {
                    null
                },
        )
    PlaylistDetailScreen(
        playlist = playlist,
        songs = viewModel.songs.collectAsLazyPagingItems(),
        coverArt = viewModel::coverArt,
        artwork = rememberArtworkColors(viewModel.coverArt(playlist?.coverArt, true), THEME_TRANSITION_MS, fallbackName = playlist?.name),
        downloads = downloads,
        downloadStatus = downloadStatus,
        totals = totals,
        onDownloadAction = requestDownloadAction,
        onBack = onBack,
        onSongClick = viewModel::play,
        onSongLongClick = { target ->
            val position = (target as? MenuTarget.Song)?.playlistPosition
            val songActions =
                actions.copy(
                    moveUpInPlaylist = actions.moveUpInPlaylist.takeIf { position != null && position > 0 },
                    moveDownInPlaylist =
                        actions.moveDownInPlaylist.takeIf {
                            position != null && position < (playlist?.songCount ?: 0) - 1
                        },
                )
            contextMenuHost?.show(target, songActions)
        },
        onShuffle = viewModel::shuffle,
        onPlay = viewModel::playAll,
        onMore = {
            playlist?.let { contextMenuHost?.show(MenuTarget.Playlist(it, viewModel.coverArt(it.coverArt, true), downloadStatus), actions) }
        },
        playingSongId = playback.item?.id.takeIf { context?.kind == QueueKind.Playlist && context.refId == playlistId },
        onToggleFavorite = { song, favorite -> setStar(StarType.Song, song.id, favorite) },
    )

    moveError?.let { error ->
        PlaylistMoveRecoveryDialog(error, moving, canEdit && viewModel.canRetryMove, viewModel::retryMove, viewModel::dismissMoveError)
    }
    PendingDownloadDeleteConfirmation(pendingDelete) {
        viewModel.onDownloadAction(BulkDownloadAction.Delete)
    }
    renaming?.let { request -> RenamePlaylistRoute(request, onDismiss = { renaming = null }) }
    deleting?.let { request ->
        DeletePlaylistRoute(
            request,
            onDismiss = { deleting = null },
            onDeleted = {
                deleting = null
                onBack()
            },
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: LazyPagingItems<PlaylistSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    artwork: ArtworkColors?,
    modifier: Modifier = Modifier,
    downloads: Map<String, SongDownload> = emptyMap(),
    downloadStatus: ListDownloadStatus = ListDownloadStatus(),
    totals: ListTotals? = null,
    onDownloadAction: (BulkDownloadAction) -> Unit = {},
    onBack: () -> Unit,
    onSongClick: (Long) -> Unit,
    onSongLongClick: (MenuTarget) -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlay: () -> Unit = {},
    onMore: () -> Unit = {},
    playingSongId: String? = null,
    onToggleFavorite: ((Song, Boolean) -> Unit)? = null,
) {
    val resources = LocalResources.current
    // As on iOS: who made it and when it last changed up top, the count and length under the last track.
    val subtitle =
        remember(playlist?.owner, playlist?.changed, resources) {
            listOfNotNull(
                playlist?.owner?.takeIf { it.isNotBlank() },
                playlist?.changed?.let { playlistUpdatedText(resources, it) },
            ).joinToString(" $DOT ")
        }
    HeroDetailScaffold(
        artwork = artwork,
        title = playlist?.name.orEmpty(),
        onBack = onBack,
        contentReady = songs.itemCount > 0,
        header = { controlsModifier, topInset ->
            HeroHeader(
                art = coverArt(playlist?.coverArt, false),
                thumbnailRef = coverArt(playlist?.coverArt, true),
                name = playlist?.name.orEmpty(),
                subtitle = subtitle,
                comment = playlist?.comment,
                hasSongs = songs.itemCount > 0,
                onPlay = onPlay,
                onShuffle = onShuffle,
                downloadStatus = downloadStatus,
                onDownloadAction = onDownloadAction,
                onMore = onMore,
                topInset = topInset,
                controlsModifier = controlsModifier,
            )
        },
        content = { rowModifier ->
            items(count = songs.itemCount, key = songs.itemKey { it.position }) { index ->
                val item = songs[index]
                if (item != null) {
                    SongRow(
                        song = item.song,
                        coverArtId = item.coverArt,
                        coverArt = coverArt,
                        isPlaying = item.song.id == playingSongId,
                        durationSeconds = item.song.duration,
                        download = downloads[item.song.id],
                        onToggleFavorite = onToggleFavorite?.let { toggle -> { favorite -> toggle(item.song, favorite) } },
                        modifier =
                            rowModifier.combinedClickable(
                                onClick = { onSongClick(item.position) },
                                onLongClick = {
                                    onSongLongClick(
                                        MenuTarget.Song(item.song, coverArt(item.coverArt, true), downloads[item.song.id], item.position),
                                    )
                                },
                            ),
                    )
                }
            }
            if (totals != null && totals.songCount > 0 && songs.itemCount > 0) {
                item(key = "summary") { PlaylistSummary(totals) }
            }
        },
        modifier = modifier,
    )
}

/** "24 songs · 1 hr 32 min" under the last track, counting the songs shown (offline, the downloaded ones). */
@Composable
private fun PlaylistSummary(totals: ListTotals) {
    val resources = LocalResources.current
    val count = totals.songCount.toInt()
    Text(
        text =
            listOfNotNull(
                resources.getQuantityString(R.plurals.resources_song_count, count, count),
                totalDurationText(resources, totals.duration),
            ).joinToString(" $DOT "),
        style = MaeksterTheme.text.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaeksterSpacing.l, vertical = MaeksterSpacing.l)
                .testTag(PLAYLIST_SUMMARY_TAG),
    )
}

internal const val PLAYLIST_SUMMARY_TAG = "playlistSummary"
