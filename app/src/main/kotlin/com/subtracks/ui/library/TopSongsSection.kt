package com.subtracks.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.R
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.SongDownload
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.SongCarousel
import kotlinx.coroutines.flow.StateFlow

internal const val TOP_SONGS_TAG = "topSongs"

@Composable
fun TopSongsSectionRoute(
    state: TopSongsState,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playbackController: PlaybackController,
    downloads: StateFlow<Map<String, SongDownload>>,
    onPlay: (Int, Boolean) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
) {
    val playback by playbackController.state.collectAsStateWithLifecycle()
    val songDownloads by downloads.collectAsStateWithLifecycle()
    TopSongsSection(
        state = state,
        coverArt = coverArt,
        playingSongId = playback.item?.id,
        onPlay = onPlay,
        downloads = songDownloads,
        onLongClick = onLongClick,
    )
}

/**
 * A first fetch does not insert a temporary shelf. Once songs are cached they stay visible during refresh.
 */
@Composable
fun TopSongsSection(
    state: TopSongsState,
    coverArt: (String?, Boolean) -> CoverArtRef?,
    playingSongId: String?,
    onPlay: (Int, Boolean) -> Unit,
    onLongClick: (MenuTarget) -> Unit,
    modifier: Modifier = Modifier,
    downloads: Map<String, SongDownload> = emptyMap(),
) {
    if (state.songs.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.artist_top_songs),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            FilledIconButton(onClick = { onPlay(0, false) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.artist_section_play, stringResource(R.string.artist_top_songs)),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { onPlay(0, true) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.artist_section_shuffle, stringResource(R.string.artist_top_songs)),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        SongCarousel(
            songs = state.songs,
            coverArt = coverArt,
            playingSongId = playingSongId,
            onSongClick = { index, _ -> onPlay(index, false) },
            onLongClick = onLongClick,
            modifier = Modifier.testTag(TOP_SONGS_TAG),
            downloads = downloads,
        )
    }
}
