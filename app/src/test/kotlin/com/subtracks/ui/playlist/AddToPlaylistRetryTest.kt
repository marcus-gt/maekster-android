package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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
                listOf("a", "b", "c", "x").map { Song(1, it, null, null, it, null, null, 60, null, null, null) },
            )
            server = FakePlaylistWriter(1)
            server.put("p", "Mix", "a")
            open(listOf("a", "b", "c"))
        }

    @After
    fun tearDown() =
        runBlocking {
            try {
                closeModel()
                if (this@AddToPlaylistRetryTest::db.isInitialized) db.close()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private suspend fun closeModel() {
        if (this::model.isInitialized) model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun open(songIds: List<String>): AddToPlaylistViewModel {
        closeModel()
        return AddToPlaylistViewModel(
            AddToPlaylistRequest(1, songIds),
            PlaylistRepository(db, writerFor = { server }),
            owner = { "alice" },
            showMessage = { messages.trySend(it) },
        ).also { model = it }
    }

    /** Puts the playlist in both the mirror and the server, as after a sync. */
    private suspend fun synced(vararg songIds: String) {
        server.put("p", "Mix", *songIds)
        mirror(*songIds)
    }

    private suspend fun mirror(vararg songIds: String) {
        db.libraryDao().replacePlaylist(
            playlist.copy(songCount = songIds.size.toLong()),
            songIds.mapIndexed { position, id -> PlaylistSong(1, "p", id, position.toLong()) },
        )
    }

    private fun TestScope.closing() = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }

    /** Waits for the mark to settle on [expected]: Room re-emits the mirror asynchronously after a write. */
    private suspend fun awaitMembership(expected: PlaylistMembership) =
        assertEquals(expected, model.ui.first { it.busy == null && it.membership(playlist) == expected }.membership(playlist))

    @Test
    fun marksFollowTheMirrorWithoutAskingTheServer() =
        runTest {
            synced("a", "b", "a")
            assertEquals(PlaylistMembership.Some, model.ui.first { it.membership.isNotEmpty() }.membership(playlist))
            synced("c", "a", "b")
            assertEquals(PlaylistMembership.All, model.ui.first { it.membership(playlist) == PlaylistMembership.All }.membership(playlist))
            assertEquals(PlaylistMembership.None, model.ui.value.membership(playlist.copy(id = "other")))
            assertEquals(3, model.ui.value.songCount)
            assertEquals(emptyList<List<String>>(), server.appended)
        }

    @Test
    fun fillingAPartlyHeldPlaylistAddsOnlyTheMissingSongsWithoutAsking() =
        runTest {
            synced("a")
            model.ui.first { it.membership(playlist) == PlaylistMembership.Some }
            val closed = closing()
            model.toggle(playlist)
            closed.await()
            assertEquals(listOf(listOf("b", "c")), server.appended)
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
            assertEquals(UiMessage(R.string.playlist_songs_added, listOf("Mix")), messages.receive())
        }

    @Test
    fun aTickedSongIsTakenOutAtEveryPositionTheServerHoldsIt() =
        runTest {
            open(listOf("a"))
            synced("a", "b", "a")
            model.ui.first { it.membership(playlist) == PlaylistMembership.All }
            val closed = closing()
            model.toggle(playlist)
            closed.await()
            assertEquals(listOf(listOf(0, 2)), server.removed)
            assertEquals(listOf("b"), server.songs("p"))
            assertEquals(listOf("b"), db.queueDao().playlistSongIds(1, "p"))
            assertEquals(UiMessage(R.string.playlist_song_removed, listOf("Mix")), messages.receive())
            awaitMembership(PlaylistMembership.None)
        }

    @Test
    fun aStaleTickRemovesNothingAndRefreshesTheMirror() =
        runTest {
            open(listOf("a"))
            mirror("a")
            server.put("p", "Mix", "b", "c")
            model.ui.first { it.membership(playlist) == PlaylistMembership.All }
            val closed = closing()
            model.toggle(playlist)
            closed.await()
            assertEquals(emptyList<List<Int>>(), server.removed)
            assertEquals(listOf("b", "c"), server.songs("p"))
            assertEquals(listOf("b", "c"), db.queueDao().playlistSongIds(1, "p"))
            awaitMembership(PlaylistMembership.None)
        }

    @Test
    fun removingSeveralSongsAsksFirstAndCancelWritesNothing() =
        runTest {
            synced("a", "x", "b", "c", "a")
            model.ui.first { it.membership(playlist) == PlaylistMembership.All }
            val closed = closing()
            model.toggle(playlist)
            assertEquals(playlist, model.ui.value.pendingRemoval)
            model.dismissRemoval()
            assertNull(model.ui.value.pendingRemoval)
            assertEquals(emptyList<List<Int>>(), server.removed)
            assertFalse(closed.isCompleted)
            assertNoMessage()

            model.toggle(playlist)
            model.confirmRemoval()
            closed.await()
            assertEquals(listOf("x"), server.songs("p"))
            assertEquals(
                UiMessage(pluralsId = R.plurals.playlist_songs_removed, quantity = 4, args = listOf(4, "Mix")),
                messages.receive(),
            )
        }

    @Test
    fun skippingDuplicatesRetainsItsPlanAcrossAPartialWriteRetry() =
        runTest {
            val closed = closing()
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            model.toggle(playlist)
            model.ui.first { it.error != null && it.busy == null }
            assertEquals(listOf("a", "b"), server.songs("p"))
            assertFalse(closed.isCompleted)
            assertNoMessage()

            model.toggle(playlist)
            closed.await()
            assertEquals(listOf(listOf("b", "c"), listOf("c")), server.appended)
            assertEquals(listOf("a", "b", "c"), server.songs("p"))
            assertEquals(listOf("a", "b", "c"), db.queueDao().playlistSongIds(1, "p"))
            assertNull(model.ui.value.error)
            assertEquals(UiMessage(R.string.playlist_songs_added, listOf("Mix")), messages.receive())
        }

    @Test
    fun aPlaylistTheMirrorDoesNotShowAsHoldingEverythingIsFilledWithoutWritingDuplicates() =
        runTest {
            server.put("p", "Mix", "a", "b", "c")
            val closed = closing()
            model.toggle(playlist)
            closed.await()
            assertEquals(emptyList<List<String>>(), server.appended)
            assertEquals(UiMessage(R.string.playlist_already_has_songs, listOf("Mix")), messages.receive())
        }

    @Test
    fun creationRetryKeepsDraftAndFinishesTheSamePlaylist() =
        runTest {
            val closed = closing()
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

    private fun assertNoMessage() {
        assertEquals(null, messages.tryReceive().getOrNull())
    }
}
