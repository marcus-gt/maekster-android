package com.subtracks.ui.library

import com.subtracks.data.model.Playlist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanEditPlaylistTest {
    private fun playlist(owner: String?) = Playlist(1, "p", "Mix", null, null, 1, created = 0, owner = owner)

    @Test
    fun theUsersOwnPlaylistCanBeEditedOnline() {
        assertTrue(canEditPlaylist(playlist("alex"), offline = false, user = "alex"))
    }

    @Test
    fun someoneElsesPlaylistCannot() {
        assertFalse(canEditPlaylist(playlist("sam"), offline = false, user = "alex"))
    }

    @Test
    fun anUnknownOwnerOrUserIsAllowedAsInTheAddSheet() {
        assertTrue(canEditPlaylist(playlist(null), offline = false, user = "alex"))
        assertTrue(canEditPlaylist(playlist("sam"), offline = false, user = null))
    }

    @Test
    fun nothingIsEditableOfflineOrBeforeThePlaylistLoads() {
        assertFalse(canEditPlaylist(playlist("alex"), offline = true, user = "alex"))
        assertFalse(canEditPlaylist(null, offline = false, user = "alex"))
    }
}
