package com.subtracks.data.db

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Album
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import com.subtracks.data.repo.artistNamePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistLikedSongsTest {
    private lateinit var db: SubtracksDatabase
    private val dao get() = db.libraryDao()

    @Before fun setUp() {
        db = createTestDatabase()
    }

    @After fun tearDown() = db.close()

    private suspend fun sources() {
        db.sourcesDao().upsertSource(Source(1, "One", "https://one", true, 1))
        db.sourcesDao().upsertSource(Source(2, "Two", "https://two", false, 2))
    }

    private fun song(
        id: String,
        star: Long?,
        artistId: String? = "artist",
        name: String = "Other",
        albumId: String? = null,
        source: Long = 1,
    ) = Song(source, id, albumId, artistId, id, null, name, 60, null, null, star)

    private fun liked(
        offline: Boolean = false,
        limit: Int = 25,
    ) = dao.artistLikedSongs(1, "artist", artistNamePattern("Björk [Live]*?"), offline, limit)

    @Test fun matchesAllIdCreditsAndOnlyFallsBackToTheLiteralNameWithoutATrackId() =
        runTest {
            sources()
            dao.upsertAlbums(listOf(Album(1, "guest", "artist", "Guest", null, 1, "art", null, null, null, 1)))
            dao.upsertSongs(
                listOf(
                    song("primary", 1),
                    song("contributor", 2, artistId = "someone"),
                    song("albumCredit", 3, artistId = "someone", albumId = "credited"),
                    song("albumPrimary", 4, artistId = "someone", albumId = "guest"),
                    song("name", 5, artistId = null, name = "BJÖRK [LIVE]*?"),
                    song("differentId", 6, artistId = "someone", name = "Björk [Live]*?"),
                    song("wildcard", 7, artistId = null, name = "Björk Lfake!"),
                    song("unstarred", null),
                    song("foreign", 8, source = 2),
                ),
            )
            dao.upsertAlbums(listOf(Album(1, "credited", null, "Credit", null, 1, null, null, null, null, 1)))
            db.useWriterConnection { writer ->
                writer.usePrepared("INSERT INTO song_artists VALUES (1, 'contributor', 0, 'artist', 'Artist')") { it.step() }
                writer.usePrepared("INSERT INTO album_artists VALUES (1, 'credited', 0, 'artist', 'Artist')") { it.step() }
            }
            val songs = liked().first()
            assertEquals(listOf("name", "albumPrimary", "albumCredit", "contributor", "primary"), songs.map { it.song.id })
            assertEquals("art", songs[1].coverArt)
        }

    @Test fun ordersNewestFirstBeforeApplyingTheLimitAndFiltersCompletedDownloadsOffline() =
        runTest {
            sources()
            dao.upsertSongs(listOf(song("old", 1), song("latest", 3), song("middle", 2)))
            db.downloadDao().upsert(SongDownload(1, "middle", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "latest", DownloadStatus.Queued))
            assertEquals(listOf("latest", "middle"), liked(limit = 2).first().map { it.song.id })
            assertEquals(listOf("middle"), liked(offline = true, limit = 1).first().map { it.song.id })
        }

    @Test fun anOpenShelfReceivesStarAndUnstarUpdates() =
        runTest {
            sources()
            dao.upsertSongs(listOf(song("one", null)))
            val updates = Channel<List<String>>(Channel.UNLIMITED)
            backgroundScope.launch(Dispatchers.Default) {
                liked().collect { rows -> updates.send(rows.map { it.song.id }) }
            }

            suspend fun next() = withContext(Dispatchers.Default) { withTimeout(5_000) { updates.receive() } }

            assertEquals(emptyList<String>(), next())
            dao.setSongStar(1, "one", 10)
            assertEquals(listOf("one"), next())
            dao.setSongStar(1, "one", null)
            assertEquals(emptyList<String>(), next())
        }
}
