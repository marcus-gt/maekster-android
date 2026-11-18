package com.subtracks.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subtracks.data.model.CoverArtRef
import com.subtracks.ui.components.CoverArt

internal val HOME_GRID_COVER = 64.dp
private val HOME_GRID_SPACING = 8.dp
private val HOME_GRID_TEXT_GAP = 12.dp
private val HOME_GRID_MIN_TEXT = 64.dp
private val HOME_GRID_SHAPE = RoundedCornerShape(12.dp)

/**
 * Columns of compact Home grid tiles that fit [width] with 16 dp side margins. A tile needs the
 * cover plus room for a short title, and that room grows with [fontScale] so large text drops to
 * a single column instead of squeezing titles into one or two characters.
 */
internal fun homeGridColumns(
    width: Dp,
    fontScale: Float = 1f,
): Int {
    val tile = HOME_GRID_COVER + HOME_GRID_TEXT_GAP + HOME_GRID_MIN_TEXT * fontScale.coerceAtLeast(1f)
    return ((width - 32.dp + HOME_GRID_SPACING) / (tile + HOME_GRID_SPACING)).toInt().coerceAtLeast(1)
}

/** A compact Home grid tile: the cover on the leading edge with the title and subtitle beside it. */
@Composable
internal fun HomeGridTile(
    title: String,
    subtitle: String?,
    ref: CoverArtRef?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .clip(HOME_GRID_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = HOME_GRID_COVER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            ref = ref,
            name = title,
            modifier = Modifier.size(HOME_GRID_COVER).clip(if (circular) CircleShape else HOME_GRID_SHAPE),
        )
        Column(Modifier.weight(1f).padding(start = HOME_GRID_TEXT_GAP, end = HOME_GRID_SPACING, top = 4.dp, bottom = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One grid row of [columns] equal cells; missing cells keep their space so columns stay aligned. */
@Composable
internal fun HomeGridRow(
    columns: Int,
    count: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(HOME_GRID_SPACING),
    ) {
        repeat(columns) { index ->
            val cellModifier = Modifier.weight(1f)
            if (index < count) cell(index, cellModifier) else Spacer(cellModifier)
        }
    }
}

/** A non-lazy grid for short shelves (a few dozen items at most). */
@Composable
internal fun <T> HomeGrid(
    items: List<T>,
    columns: Int,
    tag: String,
    tile: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(HOME_GRID_SPACING),
    ) {
        items.chunked(columns).forEach { row ->
            HomeGridRow(columns, row.size) { index, modifier -> tile(row[index], modifier) }
        }
    }
}
