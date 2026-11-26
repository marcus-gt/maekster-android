package com.subtracks.widget

import com.subtracks.data.model.Album
import com.subtracks.data.model.QueueKind
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyAddedAlbumsWidgetTest {
    @Test
    fun mirrorsBoundedShelfAndSwitchesSources() =
        runTest {
            val source = MutableStateFlow<Long?>(null)
            val firstShelf = MutableStateFlow((20 downTo 1).map { album(1, it) })
            val secondShelf = MutableStateFlow(emptyList<Album>())
            val observed = mutableListOf<RecentAlbumsContent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                recentAlbumsContent(source) { id, limit ->
                    (if (id == 1L) firstShelf else secondShelf).map { it.take(limit) }
                }.collect { observed += it }
            }
            runCurrent()
            assertEquals(RecentAlbumsContent(null), observed.last())

            source.value = 1
            runCurrent()
            assertEquals(1L, observed.last().sourceId)
            assertEquals(firstShelf.value.take(12).map { it.id }, observed.last().albums.map { it.id })

            firstShelf.value = listOf(album(1, 21)) + firstShelf.value
            runCurrent()
            assertEquals(1L, observed.last().sourceId)
            assertEquals(firstShelf.value.take(12).map { it.id }, observed.last().albums.map { it.id })

            val beforePlay = observed.size
            firstShelf.value = firstShelf.value.map { it.copy(playCount = 1) }
            runCurrent()
            assertEquals(beforePlay, observed.size)

            source.value = 2
            runCurrent()
            assertEquals(RecentAlbumsContent(2), observed.last())
            val emissions = observed.size
            firstShelf.value = emptyList()
            runCurrent()
            assertEquals(emissions, observed.size)

            secondShelf.value = listOf(album(2, 1))
            runCurrent()
            assertEquals(RecentAlbumsContent(2, listOf(WidgetAlbum("1", "Album 1", null))), observed.last())
        }

    @Test
    fun onlyTheCurrentAlbumOffersPauseOrResume() {
        val shelf = RecentAlbumsContent(1, listOf(WidgetAlbum("1", "First", null), WidgetAlbum("2", "Second", null)))
        val playing =
            PlaybackState(
                item = QueueItem("song", "Song", null, "Second", null, albumId = "2"),
                context = QueueContext(QueueKind.Playlist, 1, "playlist"),
                isPlaying = true,
            )

        assertEquals(listOf(null, true), shelf.withPlayback(playing).albums.map { it.isPlaying })
        assertEquals(listOf(null, false), shelf.withPlayback(playing.copy(isPlaying = false)).albums.map { it.isPlaying })
        assertEquals(shelf, shelf.withPlayback(playing.copy(context = QueueContext(QueueKind.Album, 2, "2"))))
        assertEquals(shelf, shelf.withPlayback(playing.copy(item = null)))
    }

    @Test
    fun backgroundFollowsPlayingArtworkOnTheActiveServer() {
        val shelf = RecentAlbumsContent(1, listOf(WidgetAlbum("2", "Album", "album-cover")))
        val playing =
            PlaybackState(
                item = QueueItem("song", "Song", null, "Album", "track-cover", albumId = "2"),
                context = QueueContext(QueueKind.Album, 1, "2"),
                isPlaying = true,
            )

        assertEquals("track-cover", shelf.withPlayback(playing).backgroundCoverArt)
        assertEquals("album-cover", shelf.withPlayback(playing.copy(item = playing.item?.copy(coverArtId = null))).backgroundCoverArt)
        assertEquals(null, shelf.withPlayback(playing.copy(isPlaying = false)).backgroundCoverArt)
        assertEquals(null, shelf.withPlayback(playing.copy(context = QueueContext(QueueKind.Album, 2, "2"))).backgroundCoverArt)
        assertEquals(null, shelf.withPlayback(playing.copy(item = null)).backgroundCoverArt)
    }

    private fun album(
        source: Long,
        ordinal: Int,
    ): Album =
        Album(
            sourceId = source,
            id = "$ordinal",
            artistId = null,
            name = "Album $ordinal",
            albumArtist = null,
            created = ordinal.toLong(),
            coverArt = null,
            genre = null,
            year = null,
            starred = null,
            songCount = 1,
        )
}
