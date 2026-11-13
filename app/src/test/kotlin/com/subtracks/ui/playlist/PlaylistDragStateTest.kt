package com.subtracks.ui.playlist

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDragStateTest {
    @Test
    fun onlyRegisteredRowsAreDropTargetsAndUnchangedDropsAreIgnored() {
        val state = PlaylistDragState()
        state.record(7, Rect(0f, 100f, 300f, 180f))
        state.record(10, Rect(0f, 180f, 300f, 260f))
        state.start(7)
        assertNull(state.finish())
        state.start(7)
        state.drag(80f)
        assertEquals(10L, state.finish())
        state.start(7)
        state.drag(-100f)
        assertNull(state.finish())
    }

    @Test
    fun removingTheSourceDuringADragCancelsIt() {
        val state = PlaylistDragState()
        state.record(7, Rect(0f, 100f, 300f, 180f))
        state.record(10, Rect(0f, 180f, 300f, 260f))
        state.start(7)
        state.drag(80f)
        state.forget(7)
        assertNull(state.from)
        assertNull(state.finish())
    }
}
