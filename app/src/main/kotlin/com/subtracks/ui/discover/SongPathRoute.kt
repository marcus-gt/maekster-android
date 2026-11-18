package com.subtracks.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Song Path needs a connection and a readable AudioMuse for the active server. */
internal fun DiscoverState.songPathSourceId(): Long? = sourceId?.takeIf { !offline && moods }

/** Keeps the draft and picker above the lazy list so they survive scrolling the card out of view. */
@Composable
internal fun rememberSongPathCard(viewModel: DiscoverViewModel): (@Composable () -> Unit)? {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sourceId = state.songPathSourceId() ?: return null
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val startingPath by viewModel.startingPath.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val path = rememberSongPath(sourceId)
    val playing =
        playback.item?.takeIf { playback.context?.sourceId == sourceId }?.let {
            SongPathChoice.Song(it.id, it.title, it.artist, it.coverArtId)
        }
    SongPathPickerHost(path, sourceId, viewModel, playing)
    return {
        SongPathCard(path, busy, startingPath, viewModel::pathCover, playing) { from, to, length ->
            viewModel.songPath(sourceId, from, to, length)
        }
    }
}
