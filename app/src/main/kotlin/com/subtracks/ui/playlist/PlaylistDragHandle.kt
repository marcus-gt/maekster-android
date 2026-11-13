package com.subtracks.ui.playlist

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.subtracks.R

internal class PlaylistDragState {
    private val bounds = mutableMapOf<Long, Rect>()
    var from by mutableStateOf<Long?>(null)
        private set
    var target by mutableStateOf<Long?>(null)
        private set
    var distance by mutableFloatStateOf(0f)
        private set
    private var origin = 0f

    fun record(
        position: Long,
        rect: Rect,
    ) {
        if (from != position) bounds[position] = rect
    }

    fun forget(position: Long) {
        bounds.remove(position)
        if (from == position) cancel()
    }

    fun start(position: Long) {
        val rect = bounds[position] ?: return
        origin = rect.center.y
        from = position
        target = position
        distance = 0f
    }

    fun drag(delta: Float) {
        if (from == null) return
        distance += delta
        val y = origin + distance
        target = bounds.entries.firstOrNull { y >= it.value.top && y < it.value.bottom }?.key
    }

    fun finish(): Long? {
        val destination = target?.takeIf { it != from }
        cancel()
        return destination
    }

    fun cancel() {
        from = null
        target = null
        distance = 0f
    }
}

@Composable
internal fun PlaylistDragHandle(
    state: PlaylistDragState,
    position: Long,
    songId: String,
    onDrop: (Long) -> Unit,
) {
    val drop by rememberUpdatedState(onDrop)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(48.dp).testTag("playlist-drag-$position").pointerInput(state, position, songId) {
                detectDragGestures(
                    onDragStart = { state.start(position) },
                    onDragCancel = state::cancel,
                    onDragEnd = { state.finish()?.let(drop) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount.y)
                    },
                )
            },
    ) {
        Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.playlist_drag_song))
    }
}
