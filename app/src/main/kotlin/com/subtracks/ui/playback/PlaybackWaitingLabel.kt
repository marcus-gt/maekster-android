package com.subtracks.ui.playback

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.subtracks.R
import com.subtracks.playback.PlaybackWaiting

private val PlaybackWaiting.labelRes: Int
    get() =
        when (this) {
            PlaybackWaiting.Preparing -> R.string.playback_waiting_preparing
            PlaybackWaiting.Buffering -> R.string.playback_waiting_buffering
            PlaybackWaiting.Reconnecting -> R.string.playback_waiting_reconnecting
        }

@Composable
internal fun PlaybackWaitingLabel(waiting: PlaybackWaiting?) {
    if (waiting == null) return
    Text(
        stringResource(waiting.labelRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
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
