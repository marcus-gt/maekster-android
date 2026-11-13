package com.subtracks.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.QuickPlaylistRepository
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class QuickPlaylistsSettingsViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var quick: QuickPlaylistRepository
    private val file = File.createTempFile("quick-settings", ".preferences_pb").apply { delete() }

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
            listOf("a" to "Alpha", "b" to "Beta", "c" to "Gamma").forEach { (id, name) ->
                db.libraryDao().replacePlaylist(Playlist(1, id, name, null, null, 0, 1), emptyList())
            }
            quick =
                QuickPlaylistRepository(db, PlaylistRepository(db, writerFor = { null }), alsoFavorite = { error("unused") }, favorite = {})
        }

    @After
    fun tearDown() {
        db.close()
        file.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun orderRemovalAndTheFavoriteSwitchArePersisted() =
        runTest {
            val preferences = UserPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
            listOf("a", "b", "c").forEach { quick.toggle(1, it) }
            val model = QuickPlaylistsSettingsViewModel(1, quick, preferences)
            assertEquals(listOf("a", "b", "c"), model.playlists.first { it.size == 3 }.map { it.id })

            model.setOrder(listOf("c", "a", "b"))
            assertEquals(listOf("c", "a", "b"), quick.quickPlaylists(1).first { it.first().id == "c" }.map { it.id })
            model.remove(model.playlists.first { it.first().id == "c" }.first())
            assertEquals(listOf("a", "b"), quick.quickPlaylists(1).first { it.size == 2 }.map { it.id })

            assertEquals(true, model.alsoFavorite.value)
            model.setAlsoFavorite(false)
            assertEquals(false, preferences.quickPlaylistsAlsoFavorite().first { !it })
            model.viewModelScope.cancel()
        }
}
