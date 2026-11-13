package com.subtracks.ui.playlist

import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.net.connectionErrorMessage
import com.subtracks.data.repo.PlaylistEditException
import com.subtracks.data.source.subsonic.SubsonicException

/** What to tell the user when a playlist edit failed. */
fun playlistEditMessage(failure: Exception): UiMessage =
    when {
        failure is PlaylistEditException -> {
            UiMessage(
                when (failure.reason) {
                    PlaylistEditException.Reason.Offline -> R.string.playlist_edit_offline
                    PlaylistEditException.Reason.NotFound -> R.string.playlist_edit_not_found
                    PlaylistEditException.Reason.Changed -> R.string.playlist_edit_changed
                    PlaylistEditException.Reason.NotReported -> R.string.playlist_edit_not_reported
                },
            )
        }

        failure is SubsonicException && failure.uiMessage != null -> {
            failure.uiMessage
        }

        else -> {
            connectionErrorMessage(failure) ?: UiMessage(R.string.playlist_edit_failed)
        }
    }
