package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeletePlaylistViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var model: DeletePlaylistViewModel
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)
    private val purged = mutableListOf<Pair<Long, String>>()
    private var mirrorRowsWhenPurged: Int? = null

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            server = FakePlaylistWriter(1)
            server.put("p", "Mix", "a", "b")
            db.libraryDao().replacePlaylist(
                Playlist(1, "p", "Mix", null, null, 2, 1),
                listOf(PlaylistSong(1, "p", "a", 0), PlaylistSong(1, "p", "b", 1)),
            )
            model =
                DeletePlaylistViewModel(
                    DeletePlaylistRequest(1, "p", "Mix", hasDownloads = true),
                    PlaylistRepository(db, writerFor = { server }),
                    purgeDownloads = { sourceId, playlistId ->
                        mirrorRowsWhenPurged = db.libraryDao().playlistEntrySongIds(sourceId, playlistId).size
                        purged += sourceId to playlistId
                    },
                    showMessage = { messages.trySend(it) },
                )
        }

    @After
    fun tearDown() {
        model.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun mirrored() =
        db
            .libraryDao()
            .playlist(1, "p")
            .first()

    @Test
    fun deletingOnlyThePlaylistKeepsTheDownloads() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.delete(withDownloads = false)
            closed.await()
            assertNull(server.playlists["p"])
            assertNull(mirrored())
            assertEquals(emptyList<Pair<Long, String>>(), purged)
            assertEquals(UiMessage(R.string.playlist_deleted, listOf("Mix")), messages.receive())
        }

    @Test
    fun deletingWithDownloadsPurgesThemAfterTheServerWhileTheMirrorStillHasTheSongs() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.delete(withDownloads = true)
            closed.await()
            assertEquals(listOf(1L to "p"), purged)
            assertEquals(2, mirrorRowsWhenPurged)
            assertNull(mirrored())
        }

    @Test
    fun aFailureKeepsTheDialogPurgesNothingAndCanBeRetried() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            model.delete(withDownloads = true)
            model.ui.first { !it.busy }
            assertNotNull(model.ui.value.error)
            runCurrent()
            assertFalse(closed.isCompleted)
            assertEquals(emptyList<Pair<Long, String>>(), purged)
            assertNotNull(mirrored())
            assertEquals("Mix", server.playlists.getValue("p").first)

            model.delete(withDownloads = true)
            closed.await()
            assertNull(server.playlists["p"])
            assertEquals(listOf(1L to "p"), purged)
        }
}
