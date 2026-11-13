package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddToPlaylistRetryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var model: AddToPlaylistViewModel
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)
    private val playlist = Playlist(1, "p", "Mix", null, null, 1, 1)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            db.libraryDao().upsertSongs(
                listOf("a", "b", "c").map { Song(1, it, null, null, it, null, null, 60, null, null, null) },
            )
            server = FakePlaylistWriter(1)
            server.put("p", "Mix", "a")
            model =
                AddToPlaylistViewModel(
                    AddToPlaylistRequest(1, listOf("a", "b", "c")),
                    PlaylistRepository(db, writerFor = { server }),
                    owner = { "alice" },
                    showMessage = { messages.trySend(it) },
                )
        }

    @After
    fun tearDown() {
        model.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun addAnywayWaitsForDecisionThenAddsEveryRequestedOccurrence() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.addTo(playlist)
            model.ui.first { it.duplicatesIn != null && it.busy == null }
            assertEquals(emptyList<List<String>>(), server.appended)
            assertFalse(closed.isCompleted)
            assertNoMessage()

            model.resolveDuplicates(skip = false)
            closed.await()
            assertEquals(listOf("a", "a", "b", "c"), server.songs("p"))
            assertEquals(UiMessage(R.string.playlist_songs_added, listOf("Mix")), messages.receive())
            assertNull(model.ui.value.duplicatesIn)
        }

    @Test
    fun skipDuplicatesRetainsItsPlanAcrossAPartialWriteRetry() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.addTo(playlist)
            model.ui.first { it.duplicatesIn != null && it.busy == null }
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            model.resolveDuplicates(skip = true)
            model.ui.first { it.error != null && it.busy == null }
            assertEquals(listOf("a", "b"), server.songs("p"))
            assertFalse(closed.isCompleted)
            assertNoMessage()

            model.addTo(playlist)
            closed.await()
            assertEquals(listOf(listOf("b", "c"), listOf("c")), server.appended)
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
            assertEquals(listOf("a", "b", "c"), db.queueDao().playlistSongIds(1, "p"))
            assertNull(model.ui.value.error)
            assertEquals(UiMessage(R.string.playlist_songs_added, listOf("Mix")), messages.receive())
        }

    @Test
    fun skippingAnEntirelyPresentSelectionClosesWithoutWriting() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.addTo(playlist)
            model.ui.first { it.duplicatesIn != null && it.busy == null }
            model.resolveDuplicates(skip = true)
            closed.await()
            assertEquals(emptyList<List<String>>(), server.appended)
            assertEquals(UiMessage(R.string.playlist_already_has_songs, listOf("Mix")), messages.receive())
        }

    @Test
    fun creationRetryKeepsDraftAndFinishesTheSamePlaylist() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.setNewName("  Road trip  ")
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            model.create()
            val failed = model.ui.first { it.error != null && it.busy == null }
            assertEquals("  Road trip  ", failed.newName)
            assertEquals(1, server.creates)
            assertFalse(closed.isCompleted)
            assertNoMessage()

            model.create()
            closed.await()
            assertEquals(1, server.creates)
            assertEquals(listOf("a", "b", "c"), server.songs("new1"))
            assertEquals(listOf(listOf("a", "b", "c"), listOf("b", "c")), server.appended)
            assertEquals(UiMessage(R.string.playlist_created, listOf("Road trip")), messages.receive())
            assertNull(model.ui.value.error)
        }

    @Test
    fun dismissingDuplicatePromptDoesNotWriteOrCloseTheSheet() =
        runTest {
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.addTo(playlist)
            model.ui.first { it.duplicatesIn != null && it.busy == null }
            model.dismissDuplicates()
            assertNull(model.ui.value.duplicatesIn)
            assertFalse(closed.isCompleted)
            assertEquals(emptyList<List<String>>(), server.appended)
            assertNoMessage()
        }

    private fun assertNoMessage() {
        assertEquals(null, messages.tryReceive().getOrNull())
    }
}
