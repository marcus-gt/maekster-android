package com.subtracks.ui.playlist

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistMoveRecoveryDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun retryAndCancelAreReachable() = check(1f, false)

    @Test
    fun largeFont() = check(2f, false)

    @Test
    fun busyDisablesBothActions() = check(1f, true)

    @Suppress("DEPRECATION")
    private fun check(
        fontScale: Float,
        busy: Boolean,
    ) {
        val resources = rule.activity.resources
        val previous = Configuration(resources.configuration)
        resources.updateConfiguration(Configuration(previous).apply { this.fontScale = fontScale }, resources.displayMetrics)
        var retries = 0
        var cancelled = 0
        try {
            rule.setContent {
                MaeksterTheme {
                    PlaylistMoveRecoveryDialog(
                        UiMessage(R.string.playlist_edit_failed),
                        busy,
                        canRetry = true,
                        onRetry = { retries++ },
                        onDismiss = { cancelled++ },
                    )
                }
            }
            rule.onNodeWithText("Try again").performClick()
            rule.onNodeWithText("Cancel").performClick()
            assertEquals(if (busy) 0 else 1, retries)
            assertEquals(if (busy) 0 else 1, cancelled)
            if (!busy) rule.onRoot().captureRoboImage("src/test/screenshots/playlist_move_recovery_$fontScale.png")
        } finally {
            resources.updateConfiguration(previous, resources.displayMetrics)
        }
    }
}
