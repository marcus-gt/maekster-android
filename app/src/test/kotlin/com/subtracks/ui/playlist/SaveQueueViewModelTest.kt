package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.QueueRepository
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SaveQueueViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var queue: QueueRepository
    private lateinit var playlists: PlaylistRepository
    private val store = ViewModelStore()
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)
    private var online = true

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "source", "http://localhost", true, 1))
            db.sourcesDao().upsertSource(Source(2, "other", "http://localhost:2", true, 2))
            db.libraryDao().upsertSongs(
                listOf("a", "b", "c", "x").map { Song(1, it, null, null, it.uppercase(), null, null, 60, null, null, null) } +
                    Song(2, "elsewhere", null, null, "E", null, null, 60, null, null, null),
            )
            server = FakePlaylistWriter(1)
            queue = QueueRepository(db)
            playlists = PlaylistRepository(db, writerFor = { if (online) server else null })
        }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.model(): SaveQueueViewModel =
        ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = SaveQueueViewModel(queue, playlists, { messages.trySend(it) }, cleanup = backgroundScope) as T
            },
        )[SaveQueueViewModel::class]

    private suspend fun queueSongs(
        vararg ids: String,
        upNext: List<String> = emptyList(),
    ) = queue.replace(
        ids.map { queue.songEntry(1, it) },
        upNext = upNext.map { queue.songEntry(1, it) },
        upNextAnchor = 0,
    )

    @Test
    fun theWholeQueueIsSavedInPlaybackOrderThenTheDialogCloses() =
        runTest {
            queueSongs("a", "b", "a", upNext = listOf("c"))
            val model = model()
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            model.setName("  Road Trip ")
            model.save()
            closed.await()
            val id = server.playlists.keys.single()
            assertEquals("Road Trip", server.playlists.getValue(id).first)
            assertEquals(queue.playlistBatches(queue.snapshot(), 1).first(), server.songs(id))
            assertEquals(4, server.songs(id).size)
            assertEquals(server.songs(id), db.libraryDao().playlistEntrySongIds(1, id))
            assertEquals(UiMessage(R.string.queue_saved_as_playlist, listOf("Road Trip")), messages.receive())
        }

    @Test
    fun aFailureKeepsTheCaptureSoRetrySavesTheQueueAsItWas() =
        runTest {
            queueSongs("a", "b")
            val model = model()
            val closed = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { model.done.first() }
            online = false
            model.setName("Mix")
            model.save()
            model.ui.first { !it.busy }
            assertTrue(model.ui.value.retained)
            assertEquals(UiMessage(R.string.playlist_edit_offline), model.ui.value.error)
            model.setName("Renamed")
            assertEquals("Mix", model.ui.value.name)
            runCurrent()
            assertFalse(closed.isCompleted)

            queueSongs("x", "c", "b")
            online = true
            model.save()
            closed.await()
            assertEquals(listOf("a", "b"), server.songs(server.playlists.keys.single()))
            assertEquals(
                "Mix",
                server.playlists.values
                    .single()
                    .first,
            )
        }

    @Test
    fun closingTheDialogAfterAFailureDiscardsTheCapture() =
        runTest {
            queueSongs("a", "b")
            val model = model()
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            model.setName("Mix")
            model.save()
            model.ui.first { !it.busy }
            assertTrue(model.ui.value.retained)
            val draft = db.playlistSaveDraftDao().songIds(1, 0, 10)
            assertEquals(listOf("a", "b"), draft)

            store.clear()
            runCurrent()
            assertEquals(emptyList<String>(), db.playlistSaveDraftDao().songIds(1, 0, 10))
            assertNull(db.playlistSaveDraftDao().draft(1))
        }

    @Test
    fun aPlaylistChangedElsewhereDropsTheCaptureAndAllowsANewSave() =
        runTest {
            queueSongs("a", "b")
            val model = model()
            server.failNext = FakePlaylistWriter.Failure.AfterFirstSong
            model.setName("Mix")
            model.save()
            model.ui.first { !it.busy }
            assertTrue(model.ui.value.retained)
            val id = server.playlists.keys.single()
            assertEquals(listOf("a"), server.songs(id))
            server.playlists
                .getValue(id)
                .second
                .add("x")

            model.save()
            model.ui.first { !it.busy }
            assertEquals(UiMessage(R.string.save_queue_changed), model.ui.value.error)
            assertFalse(model.ui.value.retained)
            runCurrent()
            assertNull(db.playlistSaveDraftDao().draft(1))
            model.setName("Mix again")
            assertEquals("Mix again", model.ui.value.name)
        }

    @Test
    fun anEmptyOrMixedQueueIsRefusedWithoutReachingTheServer() =
        runTest {
            val model = model()
            model.setName("Mix")
            model.save()
            model.ui.first { !it.busy }
            assertEquals(UiMessage(R.string.save_queue_empty), model.ui.value.error)

            queue.replace(listOf(queue.songEntry(1, "a"), queue.songEntry(2, "elsewhere")))
            model.save()
            model.ui.first { !it.busy }
            assertEquals(UiMessage(R.string.save_queue_several_servers), model.ui.value.error)
            assertFalse(model.ui.value.retained)
            assertEquals(0, server.creates)

            model.setName(" ")
            assertFalse(model.ui.value.canSave)
        }
}
