package com.subtracks.ui.playlist

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.source.subsonic.SubsonicClient
import com.subtracks.data.source.subsonic.SubsonicPlaylistWriter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaylistHttpCreationTest {
    @Test
    fun retryAfterHttpAppendFailureFinishesTheCreatedPlaylistAndMirrorsItBeforeClosing() =
        runBlocking {
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            Dispatchers.setMain(dispatcher)
            val server = MockWebServer()
            server.start()
            val db = createTestDatabase()
            var model: AddToPlaylistViewModel? = null
            try {
                db.sourcesDao().upsertSource(Source(1, "test", server.url("/").toString(), true, 1))
                db.libraryDao().upsertSongs(listOf(Song(1, "s1", null, null, "Song", null, null, 60, null, null, null)))

                fun ok(body: String = "") = MockResponse().setBody("<subsonic-response status=\"ok\">$body</subsonic-response>")

                fun playlist(entries: String = "") =
                    "<playlist id=\"p1\" name=\"Evening\" songCount=\"${if (entries.isEmpty()) 0 else 1}\" owner=\"tester\">$entries</playlist>"
                server.enqueue(ok(playlist()))
                server.enqueue(ok(playlist()))
                server.enqueue(MockResponse().setResponseCode(503))
                val client =
                    SubsonicClient(
                        server.url("/"),
                        "tester",
                        "fixture",
                        false,
                        OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
                    )
                val repository = PlaylistRepository(db, writerFor = { SubsonicPlaylistWriter(1, client) })
                val messages = Channel<UiMessage>(Channel.UNLIMITED)
                val vm =
                    AddToPlaylistViewModel(
                        AddToPlaylistRequest(1, listOf("s1")),
                        repository,
                        owner = { "tester" },
                        showMessage = { messages.trySend(it) },
                    )
                model = vm
                val closed = async(start = CoroutineStart.UNDISPATCHED) { vm.done.first() }
                try {
                    withContext(dispatcher) {
                        vm.setNewName("  Evening  ")
                        vm.create()
                    }
                    val failed = withTimeout(10_000) { vm.ui.first { it.error != null && it.busy == null } }
                    assertEquals("  Evening  ", failed.newName)
                    assertFalse(closed.isCompleted)
                    assertNull(messages.tryReceive().getOrNull())
                    assertEquals(emptyList<String>(), db.queueDao().playlistSongIds(1, "p1"))
                    assertEquals(emptyList<String>(), repository.recentDestinations(1).first().map { it.id })

                    server.enqueue(ok(playlist()))
                    server.enqueue(ok())
                    server.enqueue(ok(playlist("<entry id=\"s1\" title=\"Song\"/>")))
                    withContext(dispatcher) { vm.create() }
                    withTimeout(10_000) { closed.await() }
                    assertEquals(UiMessage(R.string.playlist_created, listOf("Evening")), messages.receive())
                    assertNull(vm.ui.value.error)
                    assertEquals(listOf("s1"), db.queueDao().playlistSongIds(1, "p1"))
                    assertEquals(listOf("p1"), repository.recentDestinations(1).first().map { it.id })
                    val requests = (0 until 6).map { server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!! }
                    assertEquals(
                        listOf(
                            "createPlaylist.view",
                            "getPlaylist.view",
                            "updatePlaylist.view",
                            "getPlaylist.view",
                            "updatePlaylist.view",
                            "getPlaylist.view",
                        ),
                        requests.map { it.encodedPath.substringAfterLast('/') },
                    )
                    assertEquals("Evening", requests.first().queryParameter("name"))
                    assertEquals(listOf("s1"), requests[2].queryParameterValues("songIdToAdd"))
                    assertEquals(listOf("s1"), requests[4].queryParameterValues("songIdToAdd"))
                    assertEquals(6, server.requestCount)
                } finally {
                    closed.cancel()
                    withContext(dispatcher) { vm.viewModelScope.cancel() }
                }
            } finally {
                model?.viewModelScope?.cancel()
                db.close()
                server.shutdown()
                Dispatchers.resetMain()
                dispatcher.close()
            }
        }
}
