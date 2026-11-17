package com.subtracks.ui.library

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Artist
import com.subtracks.data.recommendations.ResolvedSimilarArtist
import com.subtracks.data.recommendations.SimilarArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
class SimilarArtistsViewModelTest {
    private val sources = MutableStateFlow<Long?>(1)
    private val offline = MutableStateFlow(false)
    private val cached = listOf(ResolvedSimilarArtist(SimilarArtist(null, "Cached"), null))
    private var requests = 0
    private var model: SimilarArtistsViewModel? = null

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        model?.viewModelScope?.cancel()
        Dispatchers.resetMain()
    }

    private fun create(
        refresh: suspend (Long, String) -> List<ResolvedSimilarArtist> = { _, _ ->
            requests++
            emptyList()
        },
    ) = SimilarArtistsViewModel("seed", sources, offline, { _, _ -> cached }, refresh).also { model = it }

    private val external = SimilarArtist(null, "Outside")
    private val mixed =
        listOf(
            ResolvedSimilarArtist(SimilarArtist("r1", "Inside"), Artist(1, "local", "Inside", 1, null)),
            ResolvedSimilarArtist(external, null),
        )
    private val photosEnabled = MutableStateFlow(false)
    private val lookups = mutableListOf<SimilarArtist>()

    private fun createWithPhotos(photo: suspend (SimilarArtist) -> String? = { "https://commons.example/${it.name}.jpg" }) =
        SimilarArtistsViewModel("seed", sources, offline, { _, _ -> mixed }, { _, _ -> mixed }, photosEnabled) {
            lookups += it
            photo(it)
        }.also { model = it }

    @Test fun photosAreLookedUpOnlyWhenEnabledOnlineAndOnlyForArtistsOutsideTheLibrary() =
        runTest {
            val vm = createWithPhotos()
            assertEquals(emptyList<SimilarArtist>(), lookups)
            offline.value = true
            photosEnabled.value = true
            assertEquals(emptyList<SimilarArtist>(), lookups)
            offline.value = false
            assertEquals(listOf(external), lookups)
            assertEquals(mapOf(external to "https://commons.example/Outside.jpg"), vm.state.value.photos)
        }

    @Test fun turningPhotosOffOrGoingOfflineClearsThemWithoutNewLookups() =
        runTest {
            photosEnabled.value = true
            val vm = createWithPhotos()
            assertEquals(1, vm.state.value.photos.size)
            photosEnabled.value = false
            assertEquals(emptyMap<SimilarArtist, String>(), vm.state.value.photos)
            offline.value = true
            photosEnabled.value = true
            assertEquals(emptyMap<SimilarArtist, String>(), vm.state.value.photos)
            assertEquals(1, lookups.size)
            offline.value = false
            assertEquals(2, lookups.size)
            assertEquals(1, vm.state.value.photos.size)
        }

    @Test fun aSourceChangeCancelsAPendingPhotoLookup() =
        runTest {
            photosEnabled.value = true
            val gates = List(2) { CompletableDeferred<String?>() }
            var cancelled = false
            val vm =
                createWithPhotos {
                    val gate = gates[lookups.size - 1]
                    try {
                        gate.await()
                    } catch (failure: CancellationException) {
                        cancelled = true
                        throw failure
                    }
                }
            sources.value = 2
            assertEquals(true, cancelled)
            gates[0].complete("https://commons.example/first-server.jpg")
            assertEquals(2L, vm.state.value.sourceId)
            assertEquals(emptyMap<SimilarArtist, String>(), vm.state.value.photos)
            gates[1].complete("https://commons.example/second-server.jpg")
            assertEquals(mapOf(external to "https://commons.example/second-server.jpg"), vm.state.value.photos)
        }

    @Test fun disablingPhotosCancelsAnInFlightLookupAndIgnoresItsReply() =
        runTest {
            photosEnabled.value = true
            val pending = CompletableDeferred<String?>()
            var cancelled = false
            val vm =
                createWithPhotos {
                    try {
                        pending.await()
                    } catch (failure: CancellationException) {
                        cancelled = true
                        throw failure
                    }
                }
            photosEnabled.value = false
            assertEquals(true, cancelled)
            pending.complete("https://commons.example/late.jpg")
            assertEquals(emptyMap<SimilarArtist, String>(), vm.state.value.photos)
            assertEquals(1, lookups.size)
        }

    @Test fun navigationOnlyReadsCacheAndRefreshRequiresAnExplicitOnlineAction() =
        runTest {
            val vm = create()
            assertEquals(cached, vm.state.value.artists)
            assertEquals(0, requests)
            offline.value = true
            vm.refresh()
            assertEquals(0, requests)
            offline.value = false
            vm.refresh()
            assertEquals(1, requests)
            assertEquals(true, vm.state.value.checked)
            assertEquals(emptyList<ResolvedSimilarArtist>(), vm.state.value.artists)
        }

    @Test fun sourceChangeCancelsRefreshAndDoesNotShowThePreviousServersReply() =
        runTest {
            val pending = CompletableDeferred<List<ResolvedSimilarArtist>>()
            val vm =
                create { _, _ ->
                    requests++
                    pending.await()
                }
            vm.refresh()
            assertEquals(true, vm.state.value.busy)
            sources.value = 2
            pending.complete(emptyList())
            assertEquals(2L, vm.state.value.sourceId)
            assertEquals(cached, vm.state.value.artists)
            assertEquals(false, vm.state.value.busy)
            assertEquals(false, vm.state.value.checked)
        }

    @Test fun repeatedRefreshTapsAreCoalescedAndGoingOfflineCancelsTheWait() =
        runTest {
            val pending = CompletableDeferred<List<ResolvedSimilarArtist>>()
            val vm =
                create { _, _ ->
                    requests++
                    pending.await()
                }
            vm.refresh()
            vm.refresh()
            assertEquals(1, requests)
            offline.value = true
            pending.complete(emptyList())
            assertEquals(true, vm.state.value.offline)
            assertEquals(false, vm.state.value.busy)
            assertEquals(cached, vm.state.value.artists)
        }
}
