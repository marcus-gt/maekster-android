package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.lyrics.LyricsSource
import com.subtracks.data.prefs.LyricsPreferences
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class LyricsSettingsDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Suppress("DEPRECATION")
    private fun check(
        fontScale: Float,
        suffix: String,
    ) {
        val settings = mutableStateOf(LyricsPreferences())
        val sources = mutableListOf<LyricsSource>()
        val permissions = mutableListOf<Boolean>()
        val offsets = mutableListOf<Int>()
        var dismissed = 0
        val resources = rule.activity.resources
        val configuration =
            android.content.res
                .Configuration(resources.configuration)
                .apply { this.fontScale = fontScale }
        resources.updateConfiguration(configuration, resources.displayMetrics)
        rule.setContent {
            MaeksterTheme {
                LyricsSettingsDialog(
                    settings.value,
                    onSource = {
                        sources += it
                        settings.value = settings.value.copy(source = it)
                    },
                    onAllowLrclib = {
                        permissions += it
                        settings.value = settings.value.copy(allowLrclib = it)
                    },
                    onShowCurrentLine = { settings.value = settings.value.copy(showCurrentLine = it) },
                    onTimingOffset = {
                        offsets += it
                        settings.value = settings.value.copy(timingOffsetMs = it)
                    },
                    onDismiss = { dismissed++ },
                )
            }
        }
        rule.onNodeWithText("Auto").assertIsSelected()
        rule.onNodeWithText("LRCLIB").assertIsNotEnabled()
        rule.onNodeWithText("Close").assertIsDisplayed()
        rule.onNode(isDialog()).captureRoboImage("src/test/screenshots/lyrics_settings$suffix.png")
        rule.onNodeWithText("Allow LRCLIB lookups").performScrollTo().performClick()
        assertEquals(listOf(true), permissions)
        rule
            .onNodeWithText("LRCLIB")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        rule.onNodeWithText("LRCLIB").assertIsSelected()
        rule.onNodeWithText("Server").performClick()
        assertEquals(listOf(LyricsSource.Lrclib, LyricsSource.Server), sources)
        rule.onNodeWithText("LRCLIB lookups send", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Show current line in player").performScrollTo().performClick()
        assertEquals(false, settings.value.showCurrentLine)
        rule
            .onNodeWithContentDescription("Timing correction")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        rule.waitForIdle()
        assertEquals(listOf(500), offsets)
        rule.onNodeWithText("Reset").performScrollTo().performClick()
        assertEquals(0, settings.value.timingOffsetMs)
        rule.waitForIdle()
        rule.onNode(isDialog()).captureRoboImage("src/test/screenshots/lyrics_settings_bottom$suffix.png")
        rule.onNodeWithText("Close").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun controlsAndPrivacyDisclosureAreReachable() = check(1f, "")

    @Test fun controlsRemainReachableAtLargeFontScale() = check(2f, "_large_font")
}
