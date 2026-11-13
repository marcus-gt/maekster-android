package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RenamePlaylistViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var model: RenamePlaylistViewModel
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            server = FakePlaylistWriter(1)
            server.put("p", "Mix")
            db.libraryDao().replacePlaylist(Playlist(1, "p", "Mix", null, null, 0, 1), emptyList())
            model =
                RenamePlaylistViewModel(
                    RenamePlaylistRequest(1, "p", "Mix"),
                    PlaylistRepository(db, writerFor = { server }),
                    showMessage = { messages.trySend(it) },
                )
        }

    @After
    fun tearDown() {
        model.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun serverName() = server.playlists.getValue("p").first

    private suspend fun mirroredName() =
        db
            .libraryDao()
            .playlist(1, "p")
            .first()
            ?.name

    @Test
    fun theTrimmedNameReachesTheServerAndTheMirrorThenTheDialogCloses() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.setName("  Road Trip ")
            model.rename()
            closed.await()
            assertEquals("Road Trip", serverName())
            assertEquals("Road Trip", mirroredName())
            assertEquals(UiMessage(R.string.playlist_renamed, listOf("Road Trip")), messages.receive())
        }

    @Test
    fun aBlankOrUnchangedNameCannotBeSent() =
        runTest {
            for (name in listOf("", "   ", "Mix", " Mix ")) {
                model.setName(name)
                assertFalse(name, model.ui.value.canRename)
                model.rename()
            }
            assertEquals("Mix", serverName())
            assertTrue(messages.isEmpty)
        }

    @Test
    fun aFailureKeepsTheDialogAndTheNameForARetry() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            model.setName("Road Trip")
            model.rename()
            model.ui.first { !it.busy }
            assertNotNull(model.ui.value.error)
            assertEquals("Road Trip", model.ui.value.name)
            runCurrent()
            assertFalse(closed.isCompleted)
            assertEquals("Mix", mirroredName())

            model.rename()
            closed.await()
            assertEquals("Road Trip", serverName())
            assertEquals("Road Trip", mirroredName())
        }
}
