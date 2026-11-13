package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeletePlaylistDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val choices = mutableListOf<Boolean>()

    private fun show(
        hasDownloads: Boolean,
        ui: DeletePlaylistUi = DeletePlaylistUi(),
    ) {
        composeRule.setContent {
            MaeksterTheme {
                DeletePlaylistDialog(
                    request = DeletePlaylistRequest(1, "p", "Mix", hasDownloads),
                    ui = ui,
                    onDelete = { choices += it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun withoutDownloadsThereIsOneDelete() {
        show(hasDownloads = false)

        composeRule.onNodeWithText("Delete “Mix”?").assertExists()
        composeRule.onNodeWithText("Delete playlist & downloads").assertDoesNotExist()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(listOf(false), choices)
    }

    @Test
    fun withDownloadsTheUserChoosesWhetherTheyGoToo() {
        show(hasDownloads = true)

        composeRule.onNodeWithText("Delete playlist only").performClick()
        composeRule.onNodeWithText("Delete playlist & downloads").performClick()

        assertEquals(listOf(false, true), choices)
    }

    @Test
    fun whileDeletingNothingCanBePressed() {
        show(hasDownloads = true, ui = DeletePlaylistUi(busy = true))

        composeRule.onNodeWithText("Delete playlist only").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete playlist & downloads").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }
}
