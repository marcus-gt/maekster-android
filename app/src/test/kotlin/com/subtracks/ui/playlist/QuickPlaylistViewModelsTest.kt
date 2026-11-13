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
import com.subtracks.data.repo.QuickPlaylistRepository
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
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
class QuickPlaylistViewModelsTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var quick: QuickPlaylistRepository
    private lateinit var playlists: PlaylistRepository
    private val messages = Channel<UiMessage>(Channel.UNLIMITED)
    private val song = Song(1, "a", null, null, "A", null, null, 60, null, null, null)
    private var online = true

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
            db.libraryDao().upsertSongs(listOf(song))
            server = FakePlaylistWriter(1)
            server.put("jazz", "Jazz tunes")
            db.libraryDao().replacePlaylist(Playlist(1, "jazz", "Jazz tunes", null, null, 0, 1, owner = "me"), emptyList())
            db.libraryDao().replacePlaylist(Playlist(1, "theirs", "Theirs", null, null, 0, 1, owner = "else"), emptyList())
            playlists = PlaylistRepository(db, writerFor = { if (online) server else null })
            quick = QuickPlaylistRepository(db, playlists, alsoFavorite = { flowOf(false) }, favorite = {})
        }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun aQuickAddReportsAddedAlreadyThereAndFailures() =
        runTest {
            quick.toggle(1, "jazz")
            val model = QuickPlaylistMenuViewModel(song, quick, { messages.trySend(it) }, work = backgroundScope)
            val jazz = model.playlists.first { it != null }!!.single()
            assertEquals("Jazz tunes", jazz.name)

            model.add(jazz)
            assertEquals(UiMessage(R.string.playlist_songs_added, listOf("Jazz tunes")), messages.receive())
            assertEquals(listOf("a"), server.songs("jazz"))
            model.add(jazz)
            assertEquals(UiMessage(R.string.playlist_already_has_songs, listOf("Jazz tunes")), messages.receive())
            online = false
            model.add(jazz)
            assertEquals(UiMessage(R.string.playlist_edit_offline), messages.receive())
            assertEquals(listOf("a"), server.songs("jazz"))
            model.viewModelScope.cancel()
        }

    @Test
    fun thePickerTicksAndTogglesTheOwnersPlaylists() =
        runTest {
            val model = QuickPlaylistPickerViewModel(1, playlists, quick, owner = { "me" })
            assertEquals(emptySet<String>(), model.chosen.value)
            val jazz = db.libraryDao().playlist(1, "jazz").first()!!
            model.toggle(jazz)
            assertEquals(setOf("jazz"), model.chosen.first { it.isNotEmpty() })
            model.toggle(jazz)
            assertEquals(emptySet<String>(), model.chosen.first { it.isEmpty() })
            model.viewModelScope.cancel()
        }
}
