package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaylistSearchIntegrationTest {
    @Test
    fun queryChangesSearchTheMirrorLiterallyAndKeepRecentSeparate() =
        runBlocking {
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            Dispatchers.setMain(dispatcher)
            val db = createTestDatabase()
            var model: AddToPlaylistViewModel? = null
            try {
                db.sourcesDao().upsertSource(Source(1, "one", "http://localhost", true, 1))
                db.sourcesDao().upsertSource(Source(2, "two", "http://localhost", true, 2))

                fun playlist(
                    id: String,
                    name: String,
                ) = Playlist(1, id, name, null, null, 0, 1, owner = "alice")
                val recent = playlist("recent", "Evening mix")
                db.libraryDao().upsertPlaylists(
                    listOf(
                        recent,
                        playlist("percent", "100% live"),
                        playlist("underscore", "A_B"),
                        playlist("slash", "Folder\\Mix"),
                        playlist("plain", "Evening mix two"),
                        playlist("legacy", "Legacy").copy(owner = null),
                        playlist("other", "Evening mix shared").copy(owner = "bob", shared = true),
                        playlist("foreign", "Evening mix foreign").copy(sourceId = 2),
                    ),
                )
                db.playlistDestinationDao().record(1, "recent", 1)
                val vm =
                    AddToPlaylistViewModel(
                        AddToPlaylistRequest(1, listOf("song")),
                        PlaylistRepository(db, writerFor = { error("Search must only read the mirror") }),
                        owner = { "alice" },
                        showMessage = { error("Search must not send messages") },
                    )
                model = vm
                val refreshed = Channel<Unit>(Channel.UNLIMITED)
                val presenter =
                    object : PagingDataPresenter<Playlist>(dispatcher) {
                        override suspend fun presentPagingDataEvent(event: PagingDataEvent<Playlist>) {
                            if (event is PagingDataEvent.Refresh) refreshed.trySend(Unit)
                        }
                    }
                val collector = launch(dispatcher) { vm.destinations.collectLatest { presenter.collectFrom(it) } }

                suspend fun rowsAfterRefresh(): List<String> =
                    withTimeout(10_000) {
                        refreshed.receive()
                        withContext(dispatcher) { presenter.snapshot().items.map { it.id } }
                    }

                suspend fun search(query: String): List<String> {
                    withContext(dispatcher) { vm.setQuery(query) }
                    return rowsAfterRefresh()
                }
                try {
                    assertEquals(listOf("percent", "underscore", "plain", "slash", "legacy"), rowsAfterRefresh())
                    assertEquals(listOf("percent"), search("  %  "))
                    assertEquals(emptyList<Playlist>(), vm.ui.first { it.query == "  %  " }.recent)
                    assertEquals(listOf("underscore"), search("_"))
                    assertEquals(listOf("slash"), search("\\"))
                    assertEquals(listOf("plain"), search("  EVENING  "))
                    assertEquals(
                        listOf("recent"),
                        vm.ui
                            .first { it.query == "  EVENING  " }
                            .recent
                            .map { it.id },
                    )
                    assertEquals(emptyList<String>(), search("missing"))
                    assertEquals(listOf("percent", "underscore", "plain", "slash", "legacy"), search(""))

                    assertEquals(listOf("plain"), search("evening"))
                    db.libraryDao().upsertPlaylists(listOf(playlist("plain", "Morning mix")))
                    assertEquals(emptyList<String>(), rowsAfterRefresh())
                    assertEquals(listOf("plain"), search("morning"))
                } finally {
                    collector.cancelAndJoin()
                    withContext(dispatcher) { vm.viewModelScope.cancel() }
                }
            } finally {
                model?.viewModelScope?.cancel()
                db.close()
                Dispatchers.resetMain()
                dispatcher.close()
            }
        }
}
