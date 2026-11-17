package com.subtracks.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemContextMenuMixTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun check(
        target: MenuTarget,
        label: String,
    ) {
        var selected: MenuTarget? = null
        var dismissed = false
        composeRule.setContent {
            MaeksterTheme {
                ItemContextMenu(target, ItemActions(), { dismissed = true }, onInstantMix = { selected = it })
            }
        }
        composeRule.onNodeWithText(label).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(target, selected)
        assertEquals(true, dismissed)
    }

    @Test
    fun songStartsInstantMix() {
        check(MenuTarget.Song(Song(1, "s", "al", "ar", "Song", "Album", "Artist", 100, 1, 1, null)), "Instant Mix")
    }

    @Test
    fun artistStartsStation() {
        check(MenuTarget.Artist(Artist(1, "ar", "Artist", 1, null)), "Start station")
    }
}
