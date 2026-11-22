package com.subtracks.ui.library

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.cancelAndJoinBlocking
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TopSongsViewModelTest {
    private val offline = MutableStateFlow(false)
    private val songs =
        listOf("b", "a", "c").map { id ->
            AlbumSongItem(Song(1, id, null, "artist", id, null, "Artist", 60, null, null, null), null)
        }
    private lateinit var model: TopSongsViewModel

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        cancelAndJoinBlocking(model.viewModelScope)
        Dispatchers.resetMain()
    }

    private fun create(
        refresh: suspend (Long, String, String) -> Boolean,
        play: (Long, List<String>, Int, Boolean, String) -> Unit = { _, _, _, _, _ -> },
    ) {
        model =
            TopSongsViewModel(
                artistId = "artist",
                artistName = { flowOf("Artist & Band") },
                sources = flowOf(1L),
                offline = offline,
                songs = { _, _, off -> flowOf(if (off) songs.takeLast(1) else songs) },
                refresh = refresh,
                play = play,
            )
    }

    @Test fun offlineOpenUsesDownloadedSubsetAndReconnectsOnlyOnceAfterSuccess() =
        runTest {
            val requests = mutableListOf<Triple<Long, String, String>>()
            offline.value = true
            create(refresh = { source, artist, name -> requests.add(Triple(source, artist, name)) })
            assertEquals(emptyList<Triple<Long, String, String>>(), requests)
            assertEquals(
                listOf("c"),
                model.state.value.songs
                    .map { it.song.id },
            )
            offline.value = false
            assertEquals(listOf(Triple(1L, "artist", "Artist & Band")), requests)
            offline.value = true
            offline.value = false
            assertEquals(1, requests.size)
        }

    @Test fun reconnectRetriesAnInterruptedFirstFetch() =
        runTest {
            var requests = 0
            create(refresh = { _, _, _ ->
                requests++
                if (requests == 1) awaitCancellation()
                true
            })
            assertEquals(1, requests)
            offline.value = true
            offline.value = false
            assertEquals(2, requests)
        }

    @Test fun playAndShuffleUseVisibleOrderAndTheTappedIndex() =
        runTest {
            val starts = mutableListOf<Triple<List<String>, Int, Boolean>>()
            create(refresh = { _, _, _ -> true }) { source, ids, index, shuffled, artist ->
                assertEquals(1L, source)
                assertEquals("artist", artist)
                starts += Triple(ids, index, shuffled)
            }
            model.play(1)
            model.play(0, shuffled = true)
            offline.value = true
            model.play(0)
            assertEquals(
                listOf(Triple(listOf("b", "a", "c"), 1, false), Triple(listOf("b", "a", "c"), 0, true), Triple(listOf("c"), 0, false)),
                starts,
            )
        }
}
