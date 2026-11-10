package com.subtracks.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks which always-dark screens are showing, so the system bar icons stay readable in the light theme.
 *
 * The activity picks dark icons for the light theme. A screen that is always dark (album, artist, Now Playing,
 * queue) calls [WhiteStatusBarIcons] while it is on screen, and the activity switches the status bar icons to
 * white for as long as at least one such screen is composed.
 */
object StatusBarIcons {
    private var claims by mutableIntStateOf(0)

    /** True while any always-dark screen is composed. Read it in composition: it is snapshot state. */
    val white: Boolean get() = claims > 0

    internal fun claim() {
        claims++
    }

    internal fun release() {
        claims--
    }
}

/** Asks for white status bar icons while this call is in the composition. Use on screens that are always dark. */
@Composable
fun WhiteStatusBarIcons() {
    DisposableEffect(Unit) {
        StatusBarIcons.claim()
        onDispose { StatusBarIcons.release() }
    }
}
