package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.LibraryCounts
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.sync.SyncStatus
import com.subtracks.ui.theme.MaeksterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StorageSectionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeArtworkCache(
        var bytes: Long,
    ) : ArtworkCache {
        var clears = 0

        override fun size() = bytes

        override fun clear() {
            clears++
            bytes = 0
        }
    }

    @Test
    fun countsArePerServerFromTheMirror() =
        runTest {
            val db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "home", "http://a", true, 1))
            db.sourcesDao().upsertSource(Source(2, "work", "http://b", true, 2))
            db.libraryDao().upsertArtists(listOf(Artist(1, "ar", "Artist", 1, null)))
            db.libraryDao().upsertAlbums(
                listOf(1L, 2L).map { Album(it, "al", "ar", "Album", null, 0, null, null, null, null, 1) },
            )
            db.libraryDao().upsertSongs(
                listOf("a", "b").map { Song(1, it, "al", "ar", it, null, null, 60, null, null, null) },
            )
            db.libraryDao().replacePlaylist(Playlist(1, "p", "Mix", null, null, 0, 1), emptyList())

            assertEquals(LibraryCounts(artists = 1, albums = 1, songs = 2, playlists = 1), db.libraryDao().libraryCounts(1).first())
            assertEquals(LibraryCounts(albums = 1), db.libraryDao().libraryCounts(2).first())
            db.close()
        }

    @Test
    fun theViewModelReportsSizesClearsArtworkAndRequestsASync() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            try {
                val database = File.createTempFile("storage", ".db").apply { writeBytes(ByteArray(2048)) }
                val cache = FakeArtworkCache(4096)
                val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
                var syncs = 0
                val model =
                    StorageViewModel(
                        counts = MutableStateFlow(LibraryCounts(songs = 3)),
                        syncStatus = status,
                        requestSync = { syncs++ },
                        artworkCache = cache,
                        databaseFiles = { listOf(database, File(database.path + "-wal")) },
                        io = UnconfinedTestDispatcher(testScheduler),
                    )
                backgroundScope.launch { model.ui.collect {} }

                val first = model.ui.first { it.counts != null }
                assertEquals(3L, first.counts?.songs)
                assertEquals(2048L, first.databaseBytes)
                assertEquals(4096L, first.artworkBytes)

                model.clearArtwork()
                assertEquals(0L, model.ui.first { it.artworkBytes == 0L }.artworkBytes)
                assertEquals(1, cache.clears)

                status.value = SyncStatus.Running(silent = false)
                assertEquals(true, model.ui.first { it.syncing }.syncing)
                model.syncNow()
                assertEquals(1, syncs)
                model.viewModelScope.cancel()
                database.delete()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun clearingArtworkAsksFirstAndSyncingDisablesTheAction() {
        var clears = 0
        var syncs = 0
        rule.setContent {
            MaeksterTheme {
                Column {
                    ArtworkStorageSection(StorageUi(artworkBytes = 1_000_000), onClear = { clears++ })
                    LibraryStorageRows(
                        StorageUi(counts = LibraryCounts(941, 1757, 18578, 9), databaseBytes = 1_000_000, syncing = true),
                        onSyncNow = { syncs++ },
                    )
                }
            }
        }
        rule.onNodeWithText("941 artists · 1,757 albums · 18,578 songs · 9 playlists").assertExists()
        rule.onNodeWithText("Syncing…").assertExists()
        rule.onNodeWithText("Clear artwork cache").performClick()
        assertEquals(0, clears)
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Clear artwork cache").performClick()
        rule.onNodeWithText("Clear").performClick()
        assertEquals(1, clears)
        assertEquals(0, syncs)
    }
}
