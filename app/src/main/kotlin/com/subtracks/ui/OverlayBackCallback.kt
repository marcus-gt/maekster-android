package com.subtracks.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback

/** How far down the Now Playing overlay slides, as a share of its travel, at the end of a Back gesture. */
internal const val NOW_PLAYING_BACK_PREVIEW = 0.2f

/** The Now Playing open fraction while a Back gesture is [backProgress] of the way through. */
internal fun nowPlayingBackPreview(backProgress: Float): Float = 1f - NOW_PLAYING_BACK_PREVIEW * backProgress.coerceIn(0f, 1f)

/**
 * Back for the overlays above the navigation host (queue, lyrics, Now Playing). A predictive gesture previews
 * closing the overlay that was on top when it started; cancelling it calls [onCancel], releasing it [onCommit].
 * A three-button Back goes straight to [onCommit]. With no overlay ([target] returns null) it does nothing, and it
 * should be disabled so the navigation host handles Back.
 */
internal class OverlayBackCallback(
    private val target: () -> OverlayBack?,
    private val onPreview: (OverlayBack, Float) -> Unit,
    private val onCancel: (OverlayBack) -> Unit,
    private val onCommit: (OverlayBack) -> Unit,
) : OnBackPressedCallback(false) {
    private var gesture: OverlayBack? = null

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        gesture = target()
        gesture?.let { onPreview(it, 0f) }
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        gesture?.let { onPreview(it, backEvent.progress) }
    }

    override fun handleOnBackCancelled() {
        gesture?.let(onCancel)
        gesture = null
    }

    override fun handleOnBackPressed() {
        val closing = gesture ?: target()
        gesture = null
        closing?.let(onCommit)
    }
}
