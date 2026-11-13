package com.subtracks.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistMoveMenuScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun normal() = render(1f, "playlist_move_menu")

    @Test
    fun largeFont() = render(2f, "playlist_move_menu_2x")

    private fun render(
        fontScale: Float,
        name: String,
    ) {
        val resources = composeRule.activity.resources
        val previous = android.content.res.Configuration(resources.configuration)
        val configuration =
            android.content.res
                .Configuration(resources.configuration)
                .apply { this.fontScale = fontScale }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    MaeksterTheme {
                        ItemContextMenu(
                            target =
                                MenuTarget.Song(
                                    Song(1, "a", null, null, "Selected song", null, "Artist", 60, null, null, null),
                                    playlistPosition = 2,
                                ),
                            actions = ItemActions(moveUpInPlaylist = {}, moveDownInPlaylist = {}, removeFromPlaylist = {}),
                            onDismiss = {},
                        )
                    }
                }
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.onNodeWithText("Move up").assertExists()
            composeRule.onNodeWithText("Move down").assertExists()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        } finally {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(previous, resources.displayMetrics)
        }
    }
}
