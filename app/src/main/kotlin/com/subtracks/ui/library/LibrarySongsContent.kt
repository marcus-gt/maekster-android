package com.subtracks.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.FilteredEmptyState
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.ResetScrollOnChange
import com.subtracks.ui.components.rememberViewportFill

/**
 * The Library's Songs list. A tap plays the list as shown from that song, so [onSongClick] gets the row's position
 * as well as the song.
 */
@Composable
fun LibrarySongsContent(
    items: LazyPagingItems<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    bottomInset: Dp,
    onSongClick: (Song, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (MenuTarget) -> Unit = {},
    onToggleFavorite: ((Song, Boolean) -> Unit)? = null,
    playingSongId: String? = null,
    downloads: Map<String, SongDownload> = emptyMap(),
    filtered: Boolean = false,
    onClearFilters: () -> Unit = {},
    resetKey: Any? = null,
    onSync: () -> Unit = {},
) {
    when {
        items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> {
            LoadingState(modifier)
        }

        items.itemCount == 0 -> {
            if (filtered) {
                FilteredEmptyState(onClearFilters, modifier)
            } else {
                EmptyState(
                    stringResource(R.string.songs_empty),
                    modifier,
                    actionLabel = stringResource(R.string.sync),
                    onAction = onSync,
                )
            }
        }

        else -> {
            val listState = rememberLazyListState()
            ResetScrollOnChange(resetKey, { items.loadState.refresh }) { listState.scrollToItem(0) }
            val fill = rememberViewportFill(listState)
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + FAB_CLEARANCE),
            ) {
                items(count = items.itemCount, key = items.itemKey { it.song.id }) { index ->
                    val item = items[index]
                    if (item != null) {
                        val song = item.song
                        SongRow(
                            song = song,
                            coverArtId = item.coverArt,
                            coverArt = coverArt,
                            isPlaying = song.id == playingSongId,
                            durationSeconds = song.duration,
                            download = downloads[song.id],
                            onToggleFavorite = onToggleFavorite?.let { toggle -> { favorite -> toggle(song, favorite) } },
                            modifier =
                                Modifier.combinedClickable(
                                    onClick = { onSongClick(song, index) },
                                    onLongClick = {
                                        onLongClick(MenuTarget.Song(song, coverArt(item.coverArt, true), downloads[song.id]))
                                    },
                                ),
                        )
                    } else {
                        Spacer(Modifier.height(PLACEHOLDER_ROW_HEIGHT))
                    }
                }
                item { Spacer(Modifier.height(fill)) }
            }
        }
    }
}

// Keeps the last row clear of the shuffle button.
private val FAB_CLEARANCE = 72.dp

// A song row's height, so rows not loaded yet keep the scroll position steady.
private val PLACEHOLDER_ROW_HEIGHT = 72.dp
