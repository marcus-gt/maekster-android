package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenamePlaylistDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensWithTheCurrentNameAndRenamesOnlyOnceItChanges() {
        var ui by mutableStateOf(RenamePlaylistUi(name = "Mix", currentName = "Mix"))
        var renamed = 0
        composeRule.setContent {
            MaeksterTheme {
                RenamePlaylistDialog(
                    ui = ui,
                    onNameChange = { ui = ui.copy(name = it) },
                    onRename = { renamed++ },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(RENAME_PLAYLIST_FIELD_TAG).assertTextContains("Mix")
        composeRule.onNodeWithText("Rename").assertIsNotEnabled()

        composeRule.onNodeWithTag(RENAME_PLAYLIST_FIELD_TAG).performTextReplacement("Road Trip")
        composeRule.onNodeWithText("Rename").assertIsEnabled().performClick()

        assertEquals(1, renamed)
    }

    @Test
    fun whileRenamingNeitherButtonWorks() {
        composeRule.setContent {
            MaeksterTheme {
                RenamePlaylistDialog(
                    ui = RenamePlaylistUi(name = "Road Trip", currentName = "Mix", busy = true),
                    onNameChange = {},
                    onRename = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Rename").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
