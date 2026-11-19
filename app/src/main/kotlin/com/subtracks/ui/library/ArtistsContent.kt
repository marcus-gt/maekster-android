package com.subtracks.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.db.LetterBucket
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.ArtistIcon
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.FilteredEmptyState
import com.subtracks.ui.components.ListDownloadIndicator
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.ResetScrollOnChange
import com.subtracks.ui.components.ScrollToTopOnRequest
import com.subtracks.ui.components.rememberViewportFill
import com.subtracks.ui.theme.PrefetchArtworkSeeds
import kotlinx.coroutines.launch

@Composable
fun ArtistsContent(
    items: LazyPagingItems<Artist>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    bottomInset: Dp,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (MenuTarget) -> Unit = {},
    filtered: Boolean = false,
    onClearFilters: () -> Unit = {},
    resetKey: Any? = null,
    letterBuckets: List<LetterBucket> = emptyList(),
    topInset: Dp = 0.dp,
    onSync: () -> Unit = {},
    downloadStatuses: Map<String, ListDownloadStatus> = emptyMap(),
    scrollToTopKey: Any? = null,
    artistArt: (Artist) -> ArtistArt = { ArtistArt() },
    asGrid: Boolean = false,
) {
    when {
        items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> {
            LoadingState(modifier)
        }

        items.itemCount == 0 -> {
            if (filtered) {
                FilteredEmptyState(onClearFilters, modifier)
            } else {
                EmptyState(stringResource(R.string.artists_empty), modifier, actionLabel = stringResource(R.string.sync), onAction = onSync)
            }
        }

        else -> {
            val state = rememberLazyGridState()
            ResetScrollOnChange(resetKey, { items.loadState.refresh }) { state.scrollToItem(0) }
            ScrollToTopOnRequest(scrollToTopKey, state)
            val fill = rememberViewportFill(state)
            val scope = rememberCoroutineScope()
            LetterIndexedContent(letterBuckets, { position ->
                if (position < items.itemCount) scope.launch { state.scrollToItem(position) }
            }, modifier) { listModifier ->
                val inset = if (asGrid) 8.dp else 0.dp
                LazyVerticalGrid(
                    state = state,
                    columns = GridCells.Fixed(if (asGrid) 3 else 1),
                    modifier = listModifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = inset, end = inset, top = topInset + inset, bottom = bottomInset),
                    horizontalArrangement = Arrangement.spacedBy(inset),
                    verticalArrangement = Arrangement.spacedBy(inset),
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val artist = items[index]
                        if (artist == null) {
                            Spacer(if (asGrid) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.height(72.dp))
                        } else {
                            val art = artistArt(artist)
                            val download = downloadStatuses[artist.id]
                            val albumCount =
                                pluralStringResource(R.plurals.resources_album_count, artist.albumCount.toInt(), artist.albumCount)
                            val clickModifier =
                                Modifier.combinedClickable(
                                    onClick = { onArtistClick(artist) },
                                    onLongClick = { onLongClick(MenuTarget.Artist(artist, art.primary, download)) },
                                )
                            PrefetchArtworkSeeds(art.primary)
                            if (asGrid) {
                                ArtistGridTile(artist, art, albumCount, download, clickModifier)
                            } else {
                                ListItem(
                                    headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(albumCount, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        ArtistIcon(
                                            artist = artist,
                                            art = art,
                                            modifier = Modifier.size(48.dp).clip(CircleShape),
                                        )
                                    },
                                    trailingContent = { ListDownloadIndicator(download, Modifier.padding(end = 8.dp)) },
                                    modifier = clickModifier,
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(fill)) }
                }
            }
        }
    }
}

@Composable
private fun ArtistGridTile(
    artist: Artist,
    art: ArtistArt,
    albumCount: String,
    download: ListDownloadStatus?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ArtistIcon(artist, art, Modifier.fillMaxSize().clip(CircleShape))
            ListDownloadIndicator(download, Modifier.align(Alignment.BottomEnd), scrim = true)
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = albumCount,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
