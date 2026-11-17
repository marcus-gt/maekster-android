package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddSourceScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun serverActionsWrapAndRemainUsableAtDoubleFontScale() {
        var tested = false
        var saved = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaeksterTheme {
                    AddSourceScreen(
                        state = AddSourceState(),
                        onNameChange = {},
                        onAddressChange = {},
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onTokenAuthChange = {},
                        onTest = { tested = true },
                        onSave = { saved = true },
                        onDelete = {},
                        onBack = {},
                        modifier = Modifier.width(260.dp),
                    )
                }
            }
        }

        val testConnection = composeRule.onNodeWithText("Test connection").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        val save = composeRule.onNodeWithText("Save and sync").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        assertTrue(save.fetchSemanticsNode().boundsInRoot.top > testConnection.fetchSemanticsNode().boundsInRoot.bottom)
        testConnection.performClick()
        save.performClick()
        assertTrue(tested)
        assertTrue(saved)
    }
}
