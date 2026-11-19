package com.subtracks.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.subtracks.R
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinnedItem
import com.subtracks.ui.components.CoverArt

@Composable
internal fun PinnedHomeShelf(
    pins: LazyPagingItems<PinnedItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: (PinnedItem) -> Unit,
    onLongClick: (PinnedItem) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.home_pinned),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pins.itemCount, key = pins.itemKey { it.key }) { index ->
                pins[index]?.let { item ->
                    PinnedTile(item, coverArt, onClick, onLongClick)
                }
            }
        }
    }
}

/** The Pinned shelf as a carousel or a paged grid; nothing when there are no pins. */
internal fun LazyListScope.pinnedSection(
    pins: LazyPagingItems<PinnedItem>?,
    grid: Boolean,
    columns: Int,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: (PinnedItem) -> Unit,
    onLongClick: (PinnedItem) -> Unit,
) {
    if (pins == null || pins.itemCount == 0) return
    if (grid) {
        pinnedGrid(pins, columns, coverArt, onClick, onLongClick)
    } else {
        item(key = "pins") { PinnedHomeShelf(pins, coverArt, onClick, onLongClick) }
    }
}

internal fun LazyListScope.pinnedGrid(
    pins: LazyPagingItems<PinnedItem>,
    columns: Int,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: (PinnedItem) -> Unit,
    onLongClick: (PinnedItem) -> Unit,
) {
    item(key = "pins-grid-header") {
        Text(
            stringResource(R.string.home_pinned),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
    items(count = (pins.itemCount + columns - 1) / columns, key = { "pins-grid-row-$it" }) { row ->
        val start = row * columns
        HomeGridRow(columns, minOf(columns, pins.itemCount - start), Modifier.padding(bottom = 8.dp)) { offset, modifier ->
            val item = pins[start + offset]
            if (item != null) {
                HomeGridTile(
                    title = item.name,
                    subtitle = item.subtitle,
                    ref = coverArt(item.coverArt, true),
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    modifier = modifier,
                    circular = item.kind == PinKind.Artist,
                )
            } else {
                Spacer(modifier.height(HOME_GRID_COVER))
            }
        }
    }
}

@Composable
private fun PinnedTile(
    item: PinnedItem,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    onClick: (PinnedItem) -> Unit,
    onLongClick: (PinnedItem) -> Unit,
) {
    Column(Modifier.width(132.dp).combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })) {
        CoverArt(
            coverArt(item.coverArt, true),
            item.name,
            modifier =
                Modifier.size(132.dp).clip(
                    if (item.kind ==
                        PinKind.Artist
                    ) {
                        CircleShape
                    } else {
                        RoundedCornerShape(4.dp)
                    },
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
