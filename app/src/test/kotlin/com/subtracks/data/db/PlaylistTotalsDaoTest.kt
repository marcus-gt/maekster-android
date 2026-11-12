package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistTotalsDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun totalsCountEveryEntryOfThePlaylistAndOfflineOnlyTheDownloadedOnes() =
        runTest {
            val source = db.sourcesDao().insertSource(Source(name = "test", address = "http://localhost", isActive = true, createdAt = 0))
            val dao = db.libraryDao()
            dao.upsertSongs(
                listOf(song(source, "a", 200), song(source, "b", 100), song(source, "c", null), song(source, "other", 999)),
            )
            dao.upsertPlaylists(listOf(playlist(source, "p1"), playlist(source, "p2")))
            dao.upsertPlaylistSongs(
                listOf(
                    PlaylistSong(source, "p1", "a", 0),
                    PlaylistSong(source, "p1", "b", 1),
                    // The same song twice is two rows in the list, so it counts twice.
                    PlaylistSong(source, "p1", "a", 2),
                    PlaylistSong(source, "p1", "c", 3),
                    // A song missing from the mirror has no row in the list either.
                    PlaylistSong(source, "p1", "gone", 4),
                    PlaylistSong(source, "p2", "other", 0),
                ),
            )

            assertEquals(ListTotals(songCount = 4, duration = 500), dao.playlistTotals(source, "p1", false).first())
            assertEquals(ListTotals(), dao.playlistTotals(source, "p1", true).first())

            db.downloadDao().upsert(SongDownload(source, "a", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(source, "b", DownloadStatus.Queued))
            assertEquals(ListTotals(songCount = 2, duration = 400), dao.playlistTotals(source, "p1", true).first())
            assertEquals(ListTotals(), dao.playlistTotals(source, "missing", false).first())
        }

    private fun song(
        source: Long,
        id: String,
        duration: Long?,
    ) = Song(
        sourceId = source,
        id = id,
        albumId = null,
        artistId = null,
        title = id,
        album = null,
        artist = null,
        duration = duration,
        track = null,
        disc = null,
        starred = null,
    )

    private fun playlist(
        source: Long,
        id: String,
    ) = Playlist(sourceId = source, id = id, name = id, comment = null, coverArt = null, songCount = 0, created = 0)
}
