package com.subtracks.playback

import androidx.media3.session.CommandButton
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteButtonTest {
    @Test
    fun theNotificationShowsAHeartThatFillsForAFavorite() {
        val outline = favoriteButton(isFavorite = false, displayName = "Add to Favorites")
        val filled = favoriteButton(isFavorite = true, displayName = "Remove from Favorites")

        assertEquals(CommandButton.ICON_HEART_UNFILLED, outline.icon)
        assertEquals(CommandButton.ICON_HEART_FILLED, filled.icon)
        assertEquals("Remove from Favorites", filled.displayName)
        assertEquals(STAR_COMMAND, filled.sessionCommand)
    }
}
