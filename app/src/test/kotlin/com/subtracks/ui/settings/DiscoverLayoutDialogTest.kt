package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.prefs.DiscoverLayout
import com.subtracks.data.prefs.DiscoverShelf
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class DiscoverLayoutDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun normalLight() = render(1f, com.subtracks.data.prefs.ThemeMode.Light)

    @Test fun largeDark() = render(2f, com.subtracks.data.prefs.ThemeMode.Dark)

    @Suppress("DEPRECATION")
    private fun render(
        scale: Float,
        mode: com.subtracks.data.prefs.ThemeMode,
    ) {
        val resources = rule.activity.resources
        val configuration =
            android.content.res
                .Configuration(resources.configuration)
                .apply { fontScale = scale }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        rule.setContent {
            MaeksterTheme(themeMode = mode) { DiscoverLayoutDialog(DiscoverLayout(), {}, {}) }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/discover_layout_${scale}_$mode.png")
    }

    @Test fun storedLayoutArrivingAfterOpeningReplacesTheUneditedDraft() {
        var layout by mutableStateOf(DiscoverLayout())
        var saved: DiscoverLayout? = null
        rule.setContent { MaeksterTheme { DiscoverLayoutDialog(layout, { saved = it }, {}) } }
        layout = DiscoverLayout(hidden = setOf(DiscoverShelf.Stations))
        rule.waitForIdle()
        rule.onNodeWithText("Save").performClick()
        assertEquals(setOf(DiscoverShelf.Stations), saved!!.hidden)
    }

    @Test fun draftVisibilityIsOnlyPersistedWhenSaved() {
        var saved: DiscoverLayout? = null
        rule.setContent { MaeksterTheme { DiscoverLayoutDialog(DiscoverLayout(), { saved = it }, {}) } }
        rule.onNodeWithContentDescription("Queue Builder").performClick()
        assertEquals(null, saved)
        rule.onNodeWithText("Save").performClick()
        assertFalse(DiscoverShelf.QueueBuilder in saved!!.visible)
        assertTrue(DiscoverShelf.SmartShuffle in saved.visible)
    }
}
