package com.subtracks.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Artist
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ItemContextMenuAlbumLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val artist = Artist(sourceId = 1, id = "ar-1", name = "Bob Dylan", albumCount = 3, starred = null, coverArt = null)

    private fun render(actions: ItemActions) {
        composeRule.setContent {
            MaeksterTheme {
                ItemContextMenu(target = MenuTarget.Artist(artist), actions = actions, onDismiss = {})
            }
        }
    }

    @Test
    fun theArtistPageMenuOffersTheListWhileShowingTheGrid() {
        val chosen = mutableListOf<Boolean>()
        render(ItemActions(albumLayout = AlbumLayoutChoice(asList = false) { chosen += it }))

        composeRule.onNodeWithText("Show albums as a grid").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Show albums as a list").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(true), chosen)
    }

    @Test
    fun theArtistPageMenuOffersTheGridWhileShowingTheList() {
        val chosen = mutableListOf<Boolean>()
        render(ItemActions(albumLayout = AlbumLayoutChoice(asList = true) { chosen += it }))

        composeRule.onNodeWithText("Show albums as a list").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Show albums as a grid").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(false), chosen)
    }

    @Test
    fun otherArtistMenusHaveNoLayoutChoice() {
        render(ItemActions())

        composeRule.onNodeWithText("Show albums as a list").assertDoesNotExist()
        composeRule.onNodeWithText("Show albums as a grid").assertDoesNotExist()
    }
}
