package com.subtracks.ui.library

import com.subtracks.data.model.Playlist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanRemoveSongsTest {
    private fun playlist(owner: String?) = Playlist(1, "p", "Mix", null, null, 1, created = 0, owner = owner)

    @Test
    fun theUsersOwnPlaylistCanBeEditedOnline() {
        assertTrue(canRemoveSongs(playlist("alex"), offline = false, user = "alex"))
    }

    @Test
    fun someoneElsesPlaylistCannot() {
        assertFalse(canRemoveSongs(playlist("sam"), offline = false, user = "alex"))
    }

    @Test
    fun anUnknownOwnerOrUserIsAllowedAsInTheAddSheet() {
        assertTrue(canRemoveSongs(playlist(null), offline = false, user = "alex"))
        assertTrue(canRemoveSongs(playlist("sam"), offline = false, user = null))
    }

    @Test
    fun nothingIsEditableOfflineOrBeforeThePlaylistLoads() {
        assertFalse(canRemoveSongs(playlist("alex"), offline = true, user = "alex"))
        assertFalse(canRemoveSongs(null, offline = false, user = "alex"))
    }
}
