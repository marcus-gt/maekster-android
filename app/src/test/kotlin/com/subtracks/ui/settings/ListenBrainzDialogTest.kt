package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.listenbrainz.ListenBrainzApi
import com.subtracks.data.listenbrainz.ListenBrainzState
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// No PixelXL qualifier: with it, a text field in a dialog never lets Compose go idle under Robolectric.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListenBrainzDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val connected = ListenBrainzState(connected = true, username = "listener", enabled = true, pending = 3)

    private fun show(
        state: ListenBrainzState,
        form: ListenBrainzForm = ListenBrainzForm(),
        onConnect: (String, String) -> Unit = { _, _ -> },
        onEnabledChange: (Boolean) -> Unit = {},
        onDisconnect: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaeksterTheme {
                ListenBrainzDialog(state, form, onConnect, onEnabledChange, onDisconnect, onDismiss = {})
            }
        }
    }

    @Test
    fun connectingSendsTheTokenAndTheDefaultServer() {
        var sent: Pair<String, String>? = null
        show(ListenBrainzState(), onConnect = { token, root -> sent = token to root })

        composeRule.onNodeWithText("Connect").assertIsNotEnabled()
        composeRule.onNodeWithTag(LISTENBRAINZ_TOKEN_TAG).performTextInput("secret")
        composeRule.onNodeWithText("Connect").assertIsEnabled().performClick()

        assertEquals("secret" to ListenBrainzApi.DEFAULT_ROOT, sent)
    }

    @Test
    fun aRejectedTokenAndAnUnreadableOneAreExplained() {
        show(ListenBrainzState(connected = true, tokenUnreadable = true), ListenBrainzForm(error = ListenBrainzConnectError.InvalidToken))

        composeRule.onNodeWithText("ListenBrainz did not accept this token.").assertExists()
        composeRule.onNodeWithText("The saved token can no longer be read. Paste it again to keep sending listens.").assertExists()
        composeRule.onNodeWithTag(LISTENBRAINZ_TOKEN_TAG).assertExists()
    }

    @Test
    fun aConnectedAccountShowsItsQueueAndCanBePaused() {
        var enabled: Boolean? = null
        show(connected, onEnabledChange = { enabled = it })

        composeRule.onNodeWithText("Connected as listener").assertExists()
        composeRule.onNodeWithText("3 listens are waiting to be sent.").assertExists()
        composeRule.onNodeWithTag(LISTENBRAINZ_TOKEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LISTENBRAINZ_ENABLED_TAG).performClick()

        assertEquals(false, enabled)
    }

    @Test
    fun disconnectingAsksFirst() {
        var disconnected = false
        show(connected, onDisconnect = { disconnected = true })

        composeRule.onNodeWithText("Disconnect").performClick()
        composeRule.onNodeWithText("Disconnect ListenBrainz?").assertExists()
        assertEquals(false, disconnected)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Disconnect ListenBrainz?").assertDoesNotExist()

        composeRule.onNodeWithText("Disconnect").performClick()
        // The confirmation's button is the last "Disconnect" on screen.
        composeRule.onAllNodesWithText("Disconnect").onLast().performClick()

        assertTrue(disconnected)
    }

    @Test
    fun theSettingsRowSummarisesTheAccount() {
        val resources = composeRule.activity.resources
        assertEquals("Off", listenBrainzSummary(resources, ListenBrainzState()))
        assertEquals("On · listener", listenBrainzSummary(resources, connected.copy(pending = 0)))
        assertEquals("Paused · listener · 3 waiting", listenBrainzSummary(resources, connected.copy(enabled = false)))
        assertEquals("Enter your token again", listenBrainzSummary(resources, connected.copy(tokenUnreadable = true, pending = 0)))
    }
}
