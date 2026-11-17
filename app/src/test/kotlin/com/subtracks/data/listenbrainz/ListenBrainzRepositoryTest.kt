package com.subtracks.data.listenbrainz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.PendingListen
import com.subtracks.data.prefs.fakeUserPreferences
import com.subtracks.data.security.FakeCredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ListenBrainzRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private val api = FakeListenBrainzApi()
    private val prefs = fakeUserPreferences()
    private val credentials = FakeCredentialStore()
    private lateinit var repository: ListenBrainzRepository
    private val track = ListenTrack("Song", "Artist", "Album", 200_000)

    @Before
    fun setUp() {
        db = createTestDatabase()
        repository =
            ListenBrainzRepository(api, prefs, credentials, db.pendingListenDao()) { songId ->
                if (songId == "unnamed") null else track.copy(trackName = songId)
            }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aValidTokenIsStoredSealedAndTurnsListensOn() =
        runTest {
            val result = repository.connect(" secret ", "https://lb.example.org/")

            assertEquals(ConnectResult.Connected("listener"), result)
            val settings = prefs.listenBrainz().first()
            assertNotEquals("secret", settings.tokenCiphertext)
            assertEquals("secret", credentials.decrypt(settings.tokenCiphertext!!))
            assertEquals("https://lb.example.org", settings.root)
            val state = repository.state.first()
            assertTrue(state.connected && state.enabled)
            assertEquals("listener", state.username)
            assertTrue(repository.active.first())
        }

    @Test
    fun anInvalidOrUncheckableTokenStoresNothing() =
        runTest {
            api.validation = TokenValidation(false, null)
            assertEquals(ConnectResult.InvalidToken, repository.connect("bad"))

            val offline = ListenBrainzException.Network(IOException("down"))
            api.validationFailure = offline
            assertEquals(ConnectResult.Failed(offline), repository.connect("secret"))

            assertFalse(repository.state.first().connected)
            assertFalse(repository.active.first())
        }

    @Test
    fun nothingIsSentWithoutAnEnabledAccount() =
        runTest {
            repository.nowPlaying("a")
            repository.listen("a", 1_000)
            repository.connect("secret")
            repository.setEnabled(false)
            repository.listen("b", 2_000)

            assertTrue(api.tokens.isEmpty())
            assertEquals(0, repository.state.first().pending)
        }

    @Test
    fun aTransientFailureQueuesTheListenAndTheNextSuccessImportsIt() =
        runTest {
            repository.connect("secret")
            api.failNext += ListenBrainzException.Network(IOException("offline"))

            repository.listen("first", 10_000)
            assertEquals(1, repository.state.first().pending)

            repository.listen("second", 20_000)

            assertEquals(listOf("second"), api.singles.map { it.track.trackName })
            assertEquals(listOf(listOf(Listen(10, track.copy(trackName = "first")))), api.imports)
            assertEquals(0, repository.state.first().pending)
            assertEquals(setOf("secret"), api.tokens.toSet())
        }

    @Test
    fun aPermanentFailureDropsTheListen() =
        runTest {
            repository.connect("secret")
            api.failNext += ListenBrainzException.Http(400)

            repository.listen("rejected", 10_000)

            assertEquals(0, repository.state.first().pending)
        }

    @Test
    fun aLongQueueIsImportedInBatchesOfAThousand() =
        runTest {
            repository.connect("secret")
            repository.setEnabled(false)
            repeat(1_001) {
                db.pendingListenDao().insert(
                    PendingListen(listenedAt = it.toLong(), trackName = "t$it", artistName = "a", releaseName = null, durationMs = null),
                )
            }

            repository.setEnabled(true)

            assertEquals(listOf(1_000, 1), api.imports.map { it.size })
            assertEquals(0L, api.imports[0].first().listenedAt)
            assertEquals(0, repository.state.first().pending)
        }

    @Test
    fun aFailedFlushKeepsTheQueueAndEnablingRetriesIt() =
        runTest {
            repository.connect("secret")
            repository.setEnabled(false)
            db.pendingListenDao().insert(
                PendingListen(listenedAt = 5, trackName = "t", artistName = "a", releaseName = null, durationMs = null),
            )
            api.failNext += ListenBrainzException.RateLimited(30)

            repository.setEnabled(true)
            assertEquals(1, repository.state.first().pending)

            repository.flush()
            assertEquals(0, repository.state.first().pending)
        }

    @Test
    fun aKeystoreResetMakesTheTokenUnreadableAndStopsListens() =
        runTest {
            repository.connect("secret")
            credentials.loseKey()

            val state = repository.state.first()
            assertTrue(state.tokenUnreadable)
            assertFalse(repository.active.first())
            repository.listen("a", 1_000)
            assertTrue(api.singles.isEmpty())
        }

    @Test
    fun aDifferentAccountOrDisconnectingClearsTheQueue() =
        runTest {
            repository.connect("secret")
            repository.setEnabled(false)
            db.pendingListenDao().insert(
                PendingListen(listenedAt = 5, trackName = "t", artistName = "a", releaseName = null, durationMs = null),
            )
            api.failNext += ListenBrainzException.Network(IOException("offline"))

            // Same account again: the queue is kept (the flush fails here, so it stays).
            repository.connect("secret")
            assertEquals(1, repository.state.first().pending)

            repository.setEnabled(false)
            repository.connect("other-token")
            assertEquals(0, repository.state.first().pending)
            assertTrue(api.imports.isEmpty())

            db.pendingListenDao().insert(
                PendingListen(listenedAt = 6, trackName = "t", artistName = "a", releaseName = null, durationMs = null),
            )
            repository.disconnect()
            val state = repository.state.first()
            assertFalse(state.connected)
            assertEquals(0, state.pending)
        }

    @Test
    fun aSongThatCannotBeNamedIsNotSent() =
        runTest {
            repository.connect("secret")

            repository.nowPlaying("unnamed")
            repository.listen("unnamed", 1_000)

            assertTrue(api.playingNow.isEmpty() && api.singles.isEmpty())
        }
}
