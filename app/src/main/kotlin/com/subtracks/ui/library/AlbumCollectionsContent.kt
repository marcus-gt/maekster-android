package com.subtracks.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.data.model.CoverArtRef
import com.subtracks.ui.components.CoverArt
import com.subtracks.ui.components.EmptyState
import com.subtracks.ui.components.FilteredEmptyState
import com.subtracks.ui.components.LoadingState
import com.subtracks.ui.components.ResetScrollOnChange
import com.subtracks.ui.components.rememberViewportFill

/** The Genres or Labels list: each row shows one of its albums' covers and its album count, and opens its albums. */
@Composable
fun AlbumCollectionsContent(
    items: LazyPagingItems<AlbumCollectionSummary>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    bottomInset: Dp,
    @StringRes emptyText: Int,
    onClick: (AlbumCollectionSummary) -> Unit,
    modifier: Modifier = Modifier,
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
                EmptyState(stringResource(emptyText), modifier, actionLabel = stringResource(R.string.sync), onAction = onSync)
            }
        }

        else -> {
            val listState = rememberLazyListState()
            ResetScrollOnChange(resetKey, { items.loadState.refresh }) { listState.scrollToItem(0) }
            val fill = rememberViewportFill(listState)
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                // Names group case-insensitively, so the lowercased name is the row's identity.
                items(count = items.itemCount, key = items.itemKey { it.name.lowercase() }) { index ->
                    val collection = items[index]
                    if (collection != null) AlbumCollectionRow(collection, coverArt, onClick = { onClick(collection) })
                }
                item { Spacer(Modifier.height(fill)) }
            }
        }
    }
}

@Composable
private fun AlbumCollectionRow(
    collection: AlbumCollectionSummary,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                pluralStringResource(R.plurals.resources_album_count, collection.albumCount.toInt(), collection.albumCount),
                maxLines = 1,
            )
        },
        leadingContent = {
            CoverArt(
                ref = coverArt(collection.coverArt, true),
                name = collection.name,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
