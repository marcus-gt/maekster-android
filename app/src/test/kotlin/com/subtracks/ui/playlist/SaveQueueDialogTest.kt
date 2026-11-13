package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class SaveQueueDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savesOnlyOnceANameIsTyped() {
        var ui by mutableStateOf(SaveQueueUi())
        var saved = 0
        composeRule.setContent {
            MaeksterTheme {
                SaveQueueDialog(ui = ui, onNameChange = { ui = ui.copy(name = it) }, onSave = { saved++ }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithTag(SAVE_QUEUE_FIELD_TAG).performTextReplacement("Road Trip")
        composeRule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(1, saved)
    }

    @Test
    fun aRetainedCaptureOffersRetryWithTheNameFixedAndShowsTheError() {
        var retried = 0
        composeRule.setContent {
            MaeksterTheme {
                SaveQueueDialog(
                    ui = SaveQueueUi(name = "Mix", retained = true, error = UiMessage(R.string.playlist_edit_offline)),
                    onNameChange = {},
                    onSave = { retried++ },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SAVE_QUEUE_FIELD_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Playlists can't be changed offline").assertExists()
        composeRule.onNodeWithText("Retry").assertIsEnabled().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun whileSavingNeitherButtonWorks() {
        composeRule.setContent {
            MaeksterTheme {
                SaveQueueDialog(ui = SaveQueueUi(name = "Mix", busy = true), onNameChange = {}, onSave = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
