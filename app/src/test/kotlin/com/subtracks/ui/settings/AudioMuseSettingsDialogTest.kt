package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.audiomuse.AudioMuseConfigurationState
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioMuseSettingsDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: AudioMuseConfigurationState = AudioMuseConfigurationState(),
        form: AudioMuseForm = AudioMuseForm(),
        scale: Float = 1f,
        onSave: (String, String, Boolean) -> Unit = { _, _, _ -> },
        onRemove: () -> Unit = {},
    ) {
        RuntimeEnvironment.setFontScale(scale)
        rule.setContent {
            MaeksterTheme { AudioMuseSettingsDialog("My server", state, form, onSave, onRemove, {}) }
        }
    }

    @After fun resetFontScale() = RuntimeEnvironment.setFontScale(1f)

    @Test fun changingTheUrlNeverReusesASavedTokenAndShowsHttpWarning() {
        var saved: Triple<String, String, Boolean>? = null
        show(AudioMuseConfigurationState("https://old/", true), onSave = { url, token, keep -> saved = Triple(url, token, keep) })
        rule.onNodeWithTag("audiomuse-token").assertIsNotEnabled()
        rule.onNodeWithTag("audiomuse-url").performScrollTo().performTextClearance()
        rule.onNodeWithTag("audiomuse-url").performTextInput("http://new/")
        rule.onNodeWithText("HTTP sends your AudioMuse token and requests without encryption.").assertExists()
        rule.onNodeWithTag("audiomuse-token").performScrollTo().performTextInput("new-secret")
        rule.onNodeWithText("Save settings").performClick()
        assertEquals(Triple("http://new/", "new-secret", false), saved)
    }

    @Test fun aSavedTokenCanBeExplicitlyReplacedWithoutChangingTheUrl() {
        var keep: Boolean? = null
        show(AudioMuseConfigurationState("https://old/", true), onSave = { _, _, value -> keep = value })
        rule.onNodeWithTag("audiomuse-keep-token").performScrollTo().performClick()
        rule.onNodeWithTag("audiomuse-token").performScrollTo().performTextInput("new-secret")
        rule.onNodeWithText("Save settings").performClick()
        assertEquals(false, keep)
    }

    @Test fun removalRequiresConfirmationAndCancelPreservesIt() {
        var removed = false
        show(AudioMuseConfigurationState("https://old/"), onRemove = { removed = true })
        rule.onNodeWithText("Remove AudioMuse").performScrollTo().performClick()
        assertEquals(false, removed)
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(false, removed)
        rule.onNodeWithText("Remove AudioMuse").performScrollTo().performClick()
        rule.onAllNodesWithText("Remove AudioMuse").onLast().performClick()
        assertEquals(true, removed)
    }

    @Test fun successfulReplacementReturnsToKeepingTheSavedToken() {
        val form = mutableStateOf(AudioMuseForm())
        rule.setContent {
            MaeksterTheme {
                AudioMuseSettingsDialog(
                    "My server",
                    AudioMuseConfigurationState("https://old/", true),
                    form.value,
                    { _, _, _ -> form.value = AudioMuseForm(saved = true) },
                    {},
                    {},
                )
            }
        }
        rule.onNodeWithTag("audiomuse-keep-token").performScrollTo().performClick()
        rule.onNodeWithTag("audiomuse-token").performScrollTo().performTextInput("replacement")
        rule.onNodeWithText("Save settings").performClick()
        rule.onNodeWithTag("audiomuse-keep-token").performScrollTo().assertIsOn()
    }

    @Test fun busyFormCannotSaveOrEditSecrets() {
        show(AudioMuseConfigurationState("https://old/"), AudioMuseForm(busy = true))
        rule.onNodeWithText("Save settings").assertIsNotEnabled()
        rule.onNodeWithTag("audiomuse-url").assertIsNotEnabled()
        rule.onNodeWithTag("audiomuse-token").assertIsNotEnabled()
    }

    @Test fun normal() = render(1f)

    @Test fun largeFont() = render(2f)

    private fun render(scale: Float) {
        show(AudioMuseConfigurationState("http://local:8000/", true, true), scale = scale)
        rule.onNodeWithText("My server").assertExists()
        rule.onNodeWithText("The saved token can no longer be read. Enter it again or save without a token.").assertExists()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithText("Save settings").assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/audiomuse_settings_$scale.png")
        rule.onNodeWithTag("audiomuse-token").performScrollTo().assertIsDisplayed()
    }
}
