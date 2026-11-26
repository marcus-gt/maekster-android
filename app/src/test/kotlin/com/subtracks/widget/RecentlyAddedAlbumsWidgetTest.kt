package com.subtracks.widget

import com.subtracks.data.model.Album
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
