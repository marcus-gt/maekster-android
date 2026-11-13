package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
class AddToPlaylistViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recentDestinationsWaitForOwnerAndExcludeOtherUsersPlaylists() =
        runTest {
            val db = createTestDatabase()
            var model: AddToPlaylistViewModel? = null
            try {
                db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
                val own = Playlist(1, "own", "My mix", null, null, 0, 1, owner = "alice")
                val other = own.copy(id = "other", name = "Shared mix", owner = "bob", shared = true)
                val unknown = own.copy(id = "unknown", name = "Legacy mix", owner = null)
                listOf(own, other, unknown).forEachIndexed { index, playlist ->
                    db.libraryDao().replacePlaylist(playlist, emptyList())
                    db.playlistDestinationDao().record(1, playlist.id, index.toLong())
                }
                val owner = CompletableDeferred<String?>()
                val vm =
                    AddToPlaylistViewModel(
                        AddToPlaylistRequest(1, listOf("song")),
                        PlaylistRepository(db, writerFor = { null }),
                        owner = { owner.await() },
                        showMessage = {},
                    )
                model = vm
                assertEquals(emptyList<Playlist>(), vm.ui.value.recent)
                owner.complete("alice")
                assertEquals(
                    listOf("unknown", "own"),
                    vm.ui
                        .first { it.recent.isNotEmpty() }
                        .recent
                        .map { it.id },
                )
                vm.setQuery("MY")
                assertEquals(
                    listOf("own"),
                    vm.ui
                        .first { it.query == "MY" }
                        .recent
                        .map { it.id },
                )
                db.playlistDestinationDao().record(1, "own", 10)
                vm.setQuery("")
                assertEquals(
                    listOf("unknown", "own"),
                    vm.ui
                        .first { it.query.isEmpty() }
                        .recent
                        .map { it.id },
                )
            } finally {
                model?.viewModelScope?.cancel()
                db.close()
            }
        }
}
