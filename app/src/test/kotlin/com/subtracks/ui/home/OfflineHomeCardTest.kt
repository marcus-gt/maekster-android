package com.subtracks.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
class OfflineHomeCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theCardCountsOfflineSongsLeadsToThemAndTurnsOfflineModeOff() {
        var count by mutableStateOf<Int?>(null)
        var browsed = 0
        var turnedOff = 0
        rule.setContent {
            MaeksterTheme { OfflineHomeCard(songCount = count, onBrowse = { browsed++ }, onTurnOff = { turnedOff++ }) }
        }

        rule.onNodeWithText("Offline mode").assertExists()
        rule.onNodeWithText("Loading…").performClick()
        count = 0
        rule.onNodeWithText("No music saved on this device.").performClick()
        assertEquals(0, browsed)

        count = 1
        rule.onNodeWithText("1 song available offline").assertExists()
        count = 42
        rule.onNodeWithText("42 songs available offline").performClick()
        assertEquals(1, browsed)

        rule.onNodeWithText("Turn off offline mode").performClick()
        assertEquals(1, turnedOff)
    }
}
