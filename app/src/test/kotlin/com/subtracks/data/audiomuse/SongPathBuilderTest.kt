package com.subtracks.data.audiomuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SongPathBuilderTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var configuration: AudioMuseConfiguration
    private val credentials = FakeCredentialStore()
    private val access = MutableStateFlow(AudioMuseScoreAccess(1, false))
    private val requests = mutableListOf<Triple<AudioMusePathEnd, AudioMusePathEnd, Int>>()
    private var factories = 0
    private var path: suspend () -> List<AudioMuseTrack> = { emptyList() }

    private val api =
        object : AudioMuseClient {
            override suspend fun moodScores(itemId: String): AudioMuseMoodScores = error("Unexpected scores")

            override suspend fun warmup(): Boolean = error("Unexpected warmup")

            override suspend fun search(
                query: String,
                limit: Int,
            ): List<AudioMuseTrack> = error("Unexpected search")

            override suspend fun findPath(
                start: AudioMusePathEnd,
                end: AudioMusePathEnd,
                length: Int,
            ): List<AudioMuseTrack> {
                requests += Triple(start, end, length)
                return path()
            }
        }

    private fun builder() =
        SongPathBuilder(configuration, AudioMuseTrackResolver(db.audioMuseTrackDao(), access), access) {
            factories++
            api
        }

    @Before fun setUp() =
        runBlocking {
            db = createTestDatabase()
            configuration = AudioMuseConfiguration(db.audioMuseConfigDao(), credentials)
            for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://music", id == 1L, id))
            configuration.save(1, "https://muse", "secret")
            db.libraryDao().upsertSongs(
                listOf(song("a", "Alpha"), song("b", "Beta"), song("c", "Gamma"), song("other", "Other", sourceId = 2)),
            )
        }

    @After fun tearDown() = db.close()

    private fun song(
        id: String,
        title: String,
        sourceId: Long = 1,
    ) = Song(sourceId, id, null, null, title, "Album", "Artist", null, null, null, null)

    private val songA = AudioMusePathEnd.Song("a")
    private val relaxed = AudioMusePathEnd.Mood(AudioMuseMood.RELAXED)

    @Test fun thePathKeepsAudioMuseOrderOncePerSongAndOnlyLocalSongs() =
        runBlocking {
            path = {
                listOf(
                    AudioMuseTrack("a"),
                    AudioMuseTrack("fp_1", "gamma", "artist", "album"),
                    AudioMuseTrack("missing", "Nowhere"),
                    AudioMuseTrack("other"),
                    AudioMuseTrack("b"),
                    AudioMuseTrack("a"),
                )
            }
            assertEquals(SongPathResult.Ready(listOf("a", "c", "b")), builder().build(1, songA, relaxed, 25))
            assertEquals(listOf(Triple<AudioMusePathEnd, AudioMusePathEnd, Int>(songA, relaxed, 25)), requests)
            builder().build(1, relaxed, songA, 99)
            builder().build(1, songA, AudioMusePathEnd.Song("b"), 1)
            assertEquals(listOf(25, 50, 5), requests.map { it.third })
            assertEquals(relaxed, requests[1].first)
        }

    @Test fun selectedSongsAnchorThePathEvenWhenAudioMuseOmitsOrMisdescribesThem() =
        runBlocking {
            val songB = AudioMusePathEnd.Song("b")
            path = {
                listOf(
                    AudioMuseTrack("b", "Beta", "Different artist credit"),
                    AudioMuseTrack("c"),
                    AudioMuseTrack("a"),
                    AudioMuseTrack("c"),
                )
            }
            assertEquals(SongPathResult.Ready(listOf("a", "c", "b")), builder().build(1, songA, songB, 25))

            path = { listOf(AudioMuseTrack("c")) }
            assertEquals(SongPathResult.Ready(listOf("c", "b")), builder().build(1, relaxed, songB, 25))
            assertEquals(SongPathResult.Ready(listOf("a", "c")), builder().build(1, songA, relaxed, 25))
            assertEquals(
                SongPathResult.NoPath,
                builder().build(1, songA, AudioMusePathEnd.Song("other"), 25),
            )
        }

    @Test fun invalidEndsNeverContactAudioMuse() =
        runBlocking {
            val sad = AudioMusePathEnd.Mood(AudioMuseMood.SAD)
            assertEquals(SongPathResult.InvalidEnds, builder().build(1, relaxed, sad, 25))
            assertEquals(SongPathResult.InvalidEnds, builder().build(1, songA, AudioMusePathEnd.Song("a"), 25))
            assertEquals(SongPathResult.InvalidEnds, builder().build(1, AudioMusePathEnd.Song(" "), relaxed, 25))
            assertEquals(0, factories)
            assertFalse(SongPathBuilder.canBuild(songA, null))
            assertTrue(SongPathBuilder.canBuild(songA, AudioMusePathEnd.Song("b")))
            assertTrue(SongPathBuilder.canBuild(relaxed, songA))
        }

    @Test fun noPathIsReportedForA404AnEmptyPathAndAPathOutsideTheLibrary() =
        runBlocking {
            path = { throw AudioMuseHttpException(404, AudioMuseFailure.NOT_FOUND) }
            assertEquals(SongPathResult.NoPath, builder().build(1, songA, relaxed, 25))
            path = { emptyList() }
            assertEquals(SongPathResult.NoPath, builder().build(1, songA, relaxed, 25))
            path = { listOf(AudioMuseTrack("missing", "Nowhere"), AudioMuseTrack("fp_x")) }
            assertEquals(SongPathResult.NoPath, builder().build(1, songA, relaxed, 25))
            path = { throw AudioMuseHttpException(500, AudioMuseFailure.HTTP) }
            assertTrue(runCatching { builder().build(1, songA, relaxed, 25) }.exceptionOrNull() is IOException)
        }

    @Test fun unusableAccessOrConfigurationIsUnavailable() =
        runBlocking {
            access.value = AudioMuseScoreAccess(1, offline = true)
            assertEquals(SongPathResult.Unavailable, builder().build(1, songA, relaxed, 25))
            access.value = AudioMuseScoreAccess(2, false)
            assertEquals(SongPathResult.Unavailable, builder().build(2, songA, relaxed, 25))
            assertEquals(SongPathResult.Unavailable, builder().build(1, songA, relaxed, 25))
            access.value = AudioMuseScoreAccess(1, false)
            credentials.loseKey()
            assertEquals(SongPathResult.Unavailable, builder().build(1, songA, relaxed, 25))
            assertEquals(0, factories)
        }

    @Test fun goingOfflineCancelsAPathBeingFound() =
        runBlocking {
            val asked = CompletableDeferred<Unit>()
            path = {
                asked.complete(Unit)
                CompletableDeferred<List<AudioMuseTrack>>().await()
            }
            val outcome =
                coroutineScope {
                    val running = async { runCatching { builder().build(1, songA, relaxed, 25) } }
                    asked.await()
                    access.value = AudioMuseScoreAccess(1, offline = true)
                    withTimeout(5_000) { running.await() }
                }
            assertTrue(outcome.exceptionOrNull() is CancellationException)
        }
}
