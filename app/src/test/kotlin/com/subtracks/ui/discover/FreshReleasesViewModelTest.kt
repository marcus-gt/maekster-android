package com.subtracks.ui.discover

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.model.FreshReleaseState
import com.subtracks.data.model.Source
import com.subtracks.data.musicbrainz.FreshReleases
import com.subtracks.data.musicbrainz.FreshReleasesRefresher
import com.subtracks.data.musicbrainz.MbCredit
import com.subtracks.data.musicbrainz.MbReleaseGroup
import com.subtracks.data.musicbrainz.MusicBrainz
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.prefs.fakeUserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FreshReleasesViewModelTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var prefs: UserPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeSource = MutableStateFlow<Long?>(1)
    private val offline = MutableStateFlow(false)
    private val requests = AtomicInteger()
    private var blockRequests = false
    private val requestStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val requestCancelled = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val burial = "9ddce51c-2b75-4b3e-ac8c-1db09e7c89c6"
    private var model: FreshReleasesViewModel? = null

    private val musicBrainz =
        object : MusicBrainz {
            override suspend fun releaseGroups(
                artistIds: List<String>,
                from: String,
                to: String,
            ): List<MbReleaseGroup> {
                if (artistIds.isEmpty()) return emptyList()
                requests.incrementAndGet()
                if (blockRequests) {
                    requestStarted.complete(Unit)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        requestCancelled.complete(Unit)
                    }
                }
                return listOf(MbReleaseGroup("g-new", "Dreamfear", "Album", emptyList(), "2026-10-20", listOf(MbCredit(burial, "Burial"))))
            }

            override suspend fun artistId(name: String): String? {
                requests.incrementAndGet()
                return null
            }

            override suspend fun wikidataId(artistId: String): String? {
                requests.incrementAndGet()
                return null
            }
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = createTestDatabase()
        prefs = fakeUserPreferences()
        runBlocking {
            db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
            db.libraryDao().upsertArtists(listOf(Artist(1, "ar", "Burial", 1, null, musicBrainzId = burial)))
            db.libraryDao().upsertAlbums(listOf(Album(1, "al", "ar", "Untrue", "Burial", 0, null, null, 2007, null, 1)))
        }
    }

    @After
    fun tearDown() {
        model?.viewModelScope?.cancel()
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun model(): FreshReleasesViewModel {
        val feed = FreshReleases(db.freshReleaseDao(), musicBrainz) { 1_793_102_400_000L }
        val refresher = FreshReleasesRefresher(feed, scope) { activeSource.value }
        return FreshReleasesViewModel(activeSource, offline, prefs, feed, refresher).also { model = it }
    }

    private fun FreshReleasesViewModel.awaitState(predicate: (FreshReleasesState) -> Boolean) =
        runBlocking { withTimeout(5_000) { state.first(predicate) } }

    private suspend fun storeOldResult() {
        db.freshReleaseDao().publish(
            1,
            listOf(FreshRelease(1, "g-stored", 0, "Antidawn", "Burial", "2026-09-01", "EP")),
            FreshReleaseState(1, refreshedAt = 1_793_102_400_000L, attemptedAt = 1_793_102_400_000L),
        )
    }

    @Test
    fun offItMakesNoRequestAndShowsNothingEvenWithAStoredResult() {
        runBlocking { storeOldResult() }
        val model = model()
        val state = model.awaitState { true }
        Thread.sleep(300)
        assertEquals(FreshReleasesState(enabled = false), model.state.value)
        assertEquals(FreshReleasesState(enabled = false), state)
        assertEquals(0, requests.get())
    }

    @Test
    fun turnedOnOnlineItRefreshesAndShowsTheResult() {
        val model = model()
        model.awaitState { !it.enabled }
        model.setEnabled(true)
        val state = model.awaitState { it.releases.isNotEmpty() }
        assertEquals(listOf("g-new"), state.releases.map { it.id })
        assertEquals(1, requests.get())
    }

    @Test
    fun goingOfflineCancelsAnInFlightRefresh() =
        runBlocking {
            blockRequests = true
            prefs.setFreshReleasesEnabled(true)
            model()
            withTimeout(5_000) { requestStarted.await() }
            offline.value = true
            withTimeout(5_000) { requestCancelled.await() }
            assertEquals(1, requests.get())
        }

    @Test
    fun offlineItShowsTheStoredResultWithoutRequests() {
        runBlocking {
            storeOldResult()
            prefs.setFreshReleasesEnabled(true)
        }
        offline.value = true
        // Stale, so it would refresh online; offline it must not.
        runBlocking {
            db.freshReleaseDao().setState(FreshReleaseState(1, refreshedAt = 0, attemptedAt = 0))
        }
        val model = model()
        val state = model.awaitState { it.releases.isNotEmpty() }
        assertEquals(listOf("g-stored"), state.releases.map { it.id })
        Thread.sleep(300)
        assertEquals(0, requests.get())
    }
}
