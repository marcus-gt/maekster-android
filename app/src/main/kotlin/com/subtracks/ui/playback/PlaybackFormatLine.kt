package com.subtracks.ui.playback

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.subtracks.R
import com.subtracks.playback.PlaybackOrigin
import com.subtracks.playback.PlaybackState
import java.math.BigDecimal

@Composable
internal fun PlaybackFormatLine(
    state: PlaybackState,
    modifier: Modifier = Modifier,
) {
    if (state.item == null) return
    val origin = state.playbackOrigin ?: return
    val label =
        stringResource(
            when (origin) {
                PlaybackOrigin.Downloaded -> R.string.playback_origin_downloaded
                PlaybackOrigin.Streaming -> R.string.playback_origin_streaming
                PlaybackOrigin.Transcoding -> R.string.playback_origin_transcoding
            },
        )
    val encoding = state.audioEncoding
    val parts =
        buildList {
            add(label)
            encoding?.format?.takeIf { it.isNotBlank() }?.let(::add)
            encoding?.bitrate?.takeIf { it >= 1000 }?.let { add(stringResource(R.string.bitrate_value, it / 1000)) }
            encoding?.sampleRate?.takeIf { it > 0 }?.let {
                val khz = BigDecimal.valueOf(it.toLong(), 3).stripTrailingZeros().toPlainString()
                add(stringResource(R.string.playback_sample_rate, khz))
            }
        }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
