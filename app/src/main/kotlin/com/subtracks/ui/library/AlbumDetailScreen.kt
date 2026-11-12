package com.subtracks.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberSmartRecord
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.AlbumFacets
import com.subtracks.data.model.BulkDownloadAction
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Disc
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.source.StarType
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistChooserSheet
import com.subtracks.ui.components.AudioFormats
import com.subtracks.ui.components.CloseWhenDownloadsGone
import com.subtracks.ui.components.ContextMenuHost
import com.subtracks.ui.components.DismissOnRequest
import com.subtracks.ui.components.FormatBadge
import com.subtracks.ui.components.HeroDetailScaffold
import com.subtracks.ui.components.HeroHeader
import com.subtracks.ui.components.ItemActions
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.MetadataChip
import com.subtracks.ui.components.PendingDownloadDeleteConfirmation
import com.subtracks.ui.components.rememberPendingDownloadDelete
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
fun AlbumDetailRoute(
    albumId: String,
    coverArtId: String?,
    onBack: () -> Unit,
    onViewArtist: (String) -> Unit,
    contextMenuHost: ContextMenuHost? = null,
    setStar: (StarType, String, Boolean) -> Unit,
    onCollectionClick: ((AlbumCollectionKind, String) -> Unit)? = null,
    viewModel: AlbumDetailViewModel = koinViewModel(key = albumId) { parametersOf(albumId) },
    playbackController: PlaybackController = koinInject(),
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val discs by viewModel.discs.collectAsStateWithLifecycle()
    val facets by viewModel.facets.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    CloseWhenDownloadsGone(downloadStatus.downloaded, offline, onBack)
    val dismissals = contextMenuHost?.dismissals ?: emptyFlow()
    val pendingDelete = rememberPendingDownloadDelete(dismissals) { _, _ -> viewModel.downloadedBytes() }
    var showArtistChooser by remember { mutableStateOf(false) }
    DismissOnRequest(dismissals) { showArtistChooser = false }
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val context = playback.context
    val shortcutArt = remember(coverArtId) { coverArtId?.let { viewModel.coverArt(it, true) } }
    val requestDownloadAction: (BulkDownloadAction) -> Unit = { action ->
        pendingDelete.onAction(album?.name.orEmpty(), DownloadList.Album, albumId, action) { _, _, requested ->
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
            viewArtist = onViewArtist,
        )
    AlbumDetailScreen(
        album = album,
        songs = songs,
        discs = discs,
        facets = facets,
        onCollectionClick = onCollectionClick,
        coverArt = viewModel::coverArt,
        artwork =
            rememberArtworkColors(
                shortcutArt ?: viewModel.coverArt(album?.coverArt, true),
                THEME_TRANSITION_MS,
                fallbackName = album?.name,
            ),
        downloads = downloads,
        downloadStatus = downloadStatus,
        onDownloadAction = requestDownloadAction,
        onBack = onBack,
        onSongClick = viewModel::play,
        onSongLongClick = { contextMenuHost?.show(it, actions) },
        onShuffle = viewModel::shuffle,
        onPlay = viewModel::playAll,
        onMore = {
            album?.let {
                contextMenuHost?.show(
                    MenuTarget.Album(it, viewModel.coverArt(it.coverArt, true), downloadStatus),
                    actions,
                )
            }
        },
        onArtistClick =
            album
                ?.takeIf { it.artistRefs.isNotEmpty() }
                ?.let {
                    {
                        val artists = it.artistRefs
                        if (artists.size == 1) onViewArtist(artists.first().id) else showArtistChooser = true
                    }
                },
        playingSongId = playback.item?.id.takeIf { context?.kind == QueueKind.Album && context.refId == albumId },
        onToggleFavorite = { song, favorite -> setStar(StarType.Song, song.id, favorite) },
    )

    PendingDownloadDeleteConfirmation(pendingDelete) {
        viewModel.onDownloadAction(BulkDownloadAction.Delete)
    }

    if (showArtistChooser) {
        ArtistChooserSheet(
            artists = album?.artistRefs.orEmpty(),
            onArtistClick = { artist ->
                showArtistChooser = false
                onViewArtist(artist.id)
            },
            onDismiss = { showArtistChooser = false },
        )
    }
}

@Composable
fun AlbumDetailScreen(
    album: Album?,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    discs: List<Disc> = emptyList(),
    facets: AlbumFacets = AlbumFacets(),
    onCollectionClick: ((AlbumCollectionKind, String) -> Unit)? = null,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    artwork: ArtworkColors?,
    downloads: Map<String, SongDownload> = emptyMap(),
    onBack: () -> Unit,
    onSongClick: (String) -> Unit,
    onSongLongClick: (MenuTarget) -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlay: () -> Unit = {},
    downloadStatus: ListDownloadStatus = ListDownloadStatus(),
    onDownloadAction: (BulkDownloadAction) -> Unit = {},
    onMore: () -> Unit = {},
    onArtistClick: (() -> Unit)? = null,
    playingSongId: String? = null,
    onToggleFavorite: ((Song, Boolean) -> Unit)? = null,
) {
    val multiDisc = songs.map { it.disc ?: 1L }.distinct().size > 1
    val resources = LocalResources.current
    val discLabels =
        remember(discs, resources) {
            val repeated =
                discs
                    .map { it.title }
                    .filter { it.isNotBlank() }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
            discs.associate { disc ->
                disc.disc to
                    disc.title.takeIf { it.isNotBlank() }?.let { title ->
                        if (title in repeated) resources.getString(R.string.disc_track, title, disc.disc) else title
                    }
            }
        }
    val discOrdinals =
        remember(songs) {
            val counters = mutableMapOf<Long, Long>()
            songs.associate { song ->
                val disc = song.disc ?: 1L
                val ordinal = (counters[disc] ?: 0L) + 1
                counters[disc] = ordinal
                song.id to ordinal
            }
        }
    HeroDetailScaffold(
        artwork = artwork,
        title = album?.name.orEmpty(),
        onBack = onBack,
        contentReady = songs.isNotEmpty(),
        header = { controlsModifier, topInset ->
            HeroHeader(
                art = coverArt(album?.coverArt, false),
                thumbnailRef = coverArt(album?.coverArt, true),
                name = album?.name.orEmpty(),
                subtitle = album?.albumArtist?.takeIf { it.isNotBlank() }.orEmpty(),
                onSubtitleClick = onArtistClick,
                details = if (album != null) ({ AlbumDetails(album.year, songs, facets, onCollectionClick) }) else null,
                hasSongs = songs.isNotEmpty(),
                onPlay = onPlay,
                onShuffle = onShuffle,
                downloadStatus = downloadStatus,
                onDownloadAction = onDownloadAction,
                onMore = onMore,
                topInset = topInset,
                controlsModifier = controlsModifier,
                nameMaxLines = Int.MAX_VALUE,
            )
        },
        content = { rowModifier ->
            var lastDisc: Long? = null
            songs.forEach { song ->
                val disc = song.disc ?: 1L
                val discLabel = discLabels[disc]
                if (disc != lastDisc && (multiDisc || discLabel != null)) {
                    val isFirst = lastDisc == null
                    item(
                        key = "disc:$disc",
                    ) { DiscHeader(discLabel ?: resources.getString(R.string.disc_header, disc), first = isFirst) }
                }
                lastDisc = disc
                item(key = song.id) {
                    SongRow(
                        song = song,
                        isPlaying = song.id == playingSongId,
                        trackNumber = song.track ?: discOrdinals[song.id],
                        durationSeconds = song.duration,
                        download = downloads[song.id],
                        onToggleFavorite = onToggleFavorite?.let { toggle -> { favorite -> toggle(song, favorite) } },
                        modifier =
                            rowModifier.combinedClickable(
                                onClick = { onSongClick(song.id) },
                                onLongClick = {
                                    onSongLongClick(MenuTarget.Song(song, coverArt(album?.coverArt, true), downloads[song.id]))
                                },
                            ),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

/** "2023 · 10 songs · 38 min" with format badges, then the genre and label chips that open their collections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumDetails(
    year: Long?,
    songs: List<Song>,
    facets: AlbumFacets,
    onCollectionClick: ((AlbumCollectionKind, String) -> Unit)?,
) {
    val resources = LocalResources.current
    val summary =
        remember(year, songs, resources) {
            listOfNotNull(
                year?.takeIf { it > 0 }?.toString(),
                songs.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.resources_song_count, it, it) },
                totalDurationText(resources, songs.sumOf { it.duration ?: 0L }),
            ).joinToString(" $DOT ")
        }
    val badges = remember(songs) { AudioFormats.badges(songs.map { it.suffix }) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaeksterSpacing.xs),
    ) {
        if (summary.isNotEmpty() || badges.isNotEmpty()) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("album-summary"),
                horizontalArrangement = Arrangement.spacedBy(MaeksterSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaeksterTheme.text.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                badges.forEach { FormatBadge(it) }
            }
        }
        if (onCollectionClick != null && (facets.genres.isNotEmpty() || facets.label != null)) {
            val openLabel = stringResource(R.string.open_collection_albums)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaeksterSpacing.s, Alignment.CenterHorizontally),
            ) {
                facets.genres.forEach { genre ->
                    MetadataChip(
                        text = genre.trim(),
                        onClick = { onCollectionClick(AlbumCollectionKind.Genre, genre) },
                        onClickLabel = openLabel,
                        modifier = Modifier.testTag("album-chip-genre"),
                    )
                }
                facets.label?.let { label ->
                    MetadataChip(
                        text = label.trim(),
                        onClick = { onCollectionClick(AlbumCollectionKind.Label, label) },
                        onClickLabel = openLabel,
                        icon = Icons.Rounded.Business,
                        iconDescription = stringResource(R.string.record_label),
                        modifier = Modifier.testTag("album-chip-label"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscHeader(
    text: String,
    first: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (first) 16.dp else 40.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FiberSmartRecord,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
