package com.subtracks.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.ui.library.SongRow

/** Rows per column of a [SongCarousel]. */
const val SONG_CAROUSEL_ROWS = 3
private const val COLUMN_WIDTH = 0.88f

/**
 * Songs as side-scrolling columns of up to [SONG_CAROUSEL_ROWS] rows (Home favorites, the artist page's Top
 * songs). When there is more than one column, each is slightly narrower than the screen so the next one peeks
 * in as a swipe hint. [onSongClick] gets the song's index in [songs].
 */
@Composable
fun SongCarousel(
    songs: List<AlbumSongItem>,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    onSongClick: (Int, AlbumSongItem) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    downloads: Map<String, SongDownload> = emptyMap(),
    onToggleFavorite: ((Song, Boolean) -> Unit)? = null,
) {
    val columns = remember(songs) { songs.withIndex().chunked(SONG_CAROUSEL_ROWS) }
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(columns, key = { _, column ->
            column
                .first()
                .value.song.id
        }) { _, column ->
            Column(Modifier.fillParentMaxWidth(if (columns.size > 1) COLUMN_WIDTH else 1f)) {
                column.forEach { (index, item) ->
                    val download = downloads[item.song.id]?.takeIf { it.sourceId == item.song.sourceId }
                    SongRow(
                        song = item.song,
                        coverArtId = item.coverArt,
                        coverArt = coverArt,
                        isPlaying = item.song.id == playingSongId,
                        durationSeconds = item.song.duration,
                        download = download,
                        onToggleFavorite = onToggleFavorite?.let { toggle -> { favorite -> toggle(item.song, favorite) } },
                        modifier =
                            Modifier.combinedClickable(
                                onClick = { onSongClick(index, item) },
                                onLongClick = { onLongClick(MenuTarget.Song(item.song, coverArt(item.coverArt, true), download)) },
                            ),
                    )
                }
            }
        }
    }
}
