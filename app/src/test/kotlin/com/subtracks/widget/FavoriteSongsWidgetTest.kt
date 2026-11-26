package com.subtracks.widget

import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteSongsWidgetTest {
    @Test
    fun transportAndArtworkFollowPlaybackForTheActiveServer() {
        val state =
            PlaybackState(
                item = QueueItem("song", "Song", null, "Album", "cover"),
                context = QueueContext(QueueKind.Library, 1, "library"),
                isPlaying = true,
                hasNext = true,
            )
        assertEquals(FavoriteWidgetPlayback(true, true, true, "cover"), state.favoriteWidgetPlayback(1) { null })
        assertEquals(FavoriteWidgetPlayback(true, false, true), state.copy(isPlaying = false).favoriteWidgetPlayback(1) { null })
        assertEquals(FavoriteWidgetPlayback(), state.favoriteWidgetPlayback(2) { "other" })
        assertEquals(FavoriteWidgetPlayback(), state.copy(item = null).favoriteWidgetPlayback(1) { null })

        val missingCover = state.copy(item = state.item?.copy(coverArtId = null), hasNext = false)
        assertEquals(FavoriteWidgetPlayback(true, true, false, "fallback"), missingCover.favoriteWidgetPlayback(1) { "fallback" })
    }
}
