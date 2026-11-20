package com.subtracks.ui.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.subtracks.R
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.PlaybackWaiting

private val PlaybackWaiting.labelRes: Int
    get() =
        when (this) {
            PlaybackWaiting.Preparing -> R.string.playback_waiting_preparing
            PlaybackWaiting.Buffering -> R.string.playback_waiting_buffering
            PlaybackWaiting.Reconnecting -> R.string.playback_waiting_reconnecting
        }

@Composable
internal fun PlaybackWaitingLabel(
    waiting: PlaybackWaiting?,
    modifier: Modifier = Modifier,
) {
    if (waiting == null) return
    Text(
        stringResource(waiting.labelRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * One reserved slot above the controls: the wait status while the player waits, otherwise the format line.
 * The format line keeps its space (hidden) under the status, so brief buffering at a track change never
 * adds, removes or re-wraps a line and the controls stay put.
 */
@Composable
internal fun PlaybackStatusLine(
    state: PlaybackState,
    modifier: Modifier = Modifier,
) {
    val lineHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodySmall.lineHeight
                .toDp()
        }
    val waiting = state.waiting
    Box(modifier.heightIn(min = lineHeight), contentAlignment = Alignment.CenterStart) {
        PlaybackFormatLine(
            state,
            Modifier.graphicsLayer { alpha = if (waiting == null) 1f else 0f }.then(
                if (waiting == null) Modifier else Modifier.clearAndSetSemantics {},
            ),
        )
        PlaybackWaitingLabel(waiting)
    }
}

@Composable
internal fun playbackWaitingStatus(waiting: PlaybackWaiting?): String? = waiting?.let { stringResource(it.labelRes) }

/** The play control's spinner still pauses when tapped, so it keeps the Pause label and adds the wait as state. */
internal fun Modifier.waitingControlSemantics(
    pauseLabel: String,
    status: String?,
): Modifier =
    semantics {
        contentDescription = pauseLabel
        if (status != null) stateDescription = status
    }
