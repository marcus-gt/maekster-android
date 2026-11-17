package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.AudioMuseLocalTrack
import com.subtracks.data.db.AudioMuseTrackDao
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioMuseTrackResolverTest {
    private lateinit var db: SubtracksDatabase
    private val access = MutableStateFlow(AudioMuseScoreAccess(1, false))
    private var pages = 0
    private val afterIds = mutableListOf<String>()
    private var afterPage: suspend () -> Unit = {}
    private lateinit var resolver: AudioMuseTrackResolver

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
            val dao =
                object : AudioMuseTrackDao by db.audioMuseTrackDao() {
                    override suspend fun page(
                        sourceId: Long,
                        afterId: String,
                        downloadedOnly: Boolean,
                        limit: Int,
                    ): List<AudioMuseLocalTrack> {
                        assertTrue(limit <= 500)
                        pages++
                        afterIds += afterId
                        return db.audioMuseTrackDao().page(sourceId, afterId, downloadedOnly, limit).also { afterPage() }
                    }
                }
            resolver = AudioMuseTrackResolver(dao, access)
        }

    @After fun tearDown() = db.close()

    private fun song(
        id: String,
        title: String,
        artist: String? = "Artist",
        album: String? = "Album",
        sourceId: Long = 1,
    ) = Song(sourceId, id, null, null, title, album, artist, null, null, null, null)

    private suspend fun seed(vararg songs: Song) = db.libraryDao().upsertSongs(songs.toList())

    @Test fun verifiesDirectIdsLocallyAndPreservesOrderAndDuplicatesWithoutScanning() =
        runBlocking<Unit> {
            seed(song("a", "A"), song("b", "B"), song("other", "Other", sourceId = 2))
            val result = resolver.resolve(1, listOf(AudioMuseTrack("b"), AudioMuseTrack("a"), AudioMuseTrack("b"), AudioMuseTrack("other")))
            assertEquals(listOf("b", "a", "b"), result.songIds)
            assertEquals(listOf(3), result.unresolvedIndices)
            assertEquals(0, pages)
        }

    @Test fun recoversInternalAndUnmirroredIdsUsingNormalizedMetadataAndAlbum() =
        runBlocking<Unit> {
            seed(
                song("a", "Déjà-Vu!", "Björk", "First"),
                song("b", "Déjà-Vu!", "Björk", "Second"),
                song("fp_collision", "Wrong", "Wrong"),
            )
            val result =
                resolver.resolve(
                    1,
                    listOf(
                        AudioMuseTrack("fp_collision", "deja vu", "BJORK", "first"),
                        AudioMuseTrack("absent", "DEJA VU", "bjork", "second"),
                        AudioMuseTrack("fp_again", "deja vu", "BJORK", "first"),
                    ),
                )
            assertEquals(listOf("a", "b", "a"), result.songIds)
            assertTrue(result.unresolvedIndices.isEmpty())
            assertEquals(1, pages)
        }

    @Test fun collidingIdsWithContradictoryMetadataAreRecoveredRatherThanPlayedBlindly() =
        runBlocking<Unit> {
            seed(song("a", "Alpha"), song("b", "Beta"))
            val result =
                resolver.resolve(
                    1,
                    listOf(
                        AudioMuseTrack("a", "Beta", "Artist", "Album"),
                        AudioMuseTrack("a", "Absent", "Artist", "Album"),
                    ),
                )
            assertEquals(listOf("b"), result.songIds)
            assertEquals(listOf(1), result.unresolvedIndices)
        }

    @Test fun ambiguousIncompleteOrContainmentOnlyMetadataIsNotGuessed() =
        runBlocking<Unit> {
            seed(song("a", "Intro", "A", "One"), song("b", "Intro", "B", "Two"), song("live", "Song - Live", "A"))
            val result =
                resolver.resolve(
                    1,
                    listOf(
                        AudioMuseTrack("fp_1", "Intro"),
                        AudioMuseTrack("fp_2", "Intro", "A"),
                        AudioMuseTrack("fp_3", "Song", "A"),
                        AudioMuseTrack("fp_4", "Intro", "Missing"),
                        AudioMuseTrack("fp_5", "!!!"),
                    ),
                )
            assertEquals(listOf("a"), result.songIds)
            assertEquals(listOf(0, 2, 3, 4), result.unresolvedIndices)
        }

    @Test fun scansAllKeysetPagesBeforeDeclaringAMatchUnique() =
        runBlocking<Unit> {
            val songs =
                (0..500).map { index ->
                    song(
                        index.toString().padStart(4, '0'),
                        if (index == 0 ||
                            index == 500
                        ) {
                            "Target"
                        } else {
                            "Other $index"
                        },
                        album = if (index == 0) "First" else "Second",
                    )
                }
            db.libraryDao().upsertSongs(songs)
            val result =
                resolver.resolve(
                    1,
                    listOf(AudioMuseTrack("fp_1", "Target", "Artist"), AudioMuseTrack("fp_2", "Target", "Artist", "First")),
                )
            assertEquals(listOf("0000"), result.songIds)
            assertEquals(listOf(0), result.unresolvedIndices)
            assertEquals(listOf("", "0499"), afterIds)
        }

    @Test fun offlineResolutionUsesOnlyCompletedDownloadsOfThisSource() =
        runBlocking<Unit> {
            seed(song("a", "Same"), song("b", "Same"), song("c", "Queued"), song("foreign", "Foreign", sourceId = 2))
            db.downloadDao().upsert(SongDownload(1, "a", DownloadStatus.Completed))
            db.downloadDao().upsert(SongDownload(1, "c", DownloadStatus.Queued))
            db.downloadDao().upsert(SongDownload(2, "foreign", DownloadStatus.Completed))
            access.value = AudioMuseScoreAccess(1, true)
            val result =
                resolver.resolve(
                    1,
                    listOf(
                        AudioMuseTrack("a"),
                        AudioMuseTrack("b"),
                        AudioMuseTrack("c"),
                        AudioMuseTrack("fp_1", "Same", "Artist"),
                        AudioMuseTrack("fp_2", "Foreign", "Artist"),
                    ),
                )
            assertEquals(listOf("a", "a"), result.songIds)
            assertEquals(listOf(1, 2, 4), result.unresolvedIndices)
        }

    @Test fun sourceAndOfflineChangesCancelResultsEvenDuringALocalRead() =
        runBlocking<Unit> {
            seed(song("a", "A"))
            for (next in listOf(AudioMuseScoreAccess(2, false), AudioMuseScoreAccess(1, true))) {
                access.value = AudioMuseScoreAccess(1, false)
                afterPage = { access.value = next }
                assertThrows(CancellationException::class.java) {
                    runBlocking {
                        resolver.resolve(1, listOf(AudioMuseTrack("fp_1", "A", "Artist")))
                    }
                }
            }
            access.value = AudioMuseScoreAccess(2, false)
            assertThrows(CancellationException::class.java) { runBlocking { resolver.resolve(1, emptyList()) } }
        }

    @Test fun matchesAndMissesAreNotReusedAfterTheMirrorChanges() =
        runBlocking<Unit> {
            val input = listOf(AudioMuseTrack("fp_1", "New", "Artist"))
            assertTrue(resolver.resolve(1, input).allUnresolved)
            seed(song("new", "New"))
            assertEquals(listOf("new"), resolver.resolve(1, input).songIds)
            db.libraryDao().deleteSongs(1, listOf("new"))
            assertTrue(resolver.resolve(1, input).allUnresolved)
            assertFalse(resolver.resolve(1, emptyList()).allUnresolved)
        }

    @Test fun songsRemovedDuringTheScanAreNotReturned() =
        runBlocking<Unit> {
            seed(song("a", "A"))
            afterPage = { db.libraryDao().deleteSongs(1, listOf("a")) }
            assertTrue(resolver.resolve(1, listOf(AudioMuseTrack("fp_1", "A", "Artist"))).allUnresolved)
        }

    @Test fun oversizedBatchesAreRejectedBeforeReadingTheLibrary() =
        runBlocking<Unit> {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    resolver.resolve(1, List(501) { AudioMuseTrack("fp_$it", "A") })
                }
            }
            assertEquals(0, pages)
        }
}
