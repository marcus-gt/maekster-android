package com.subtracks.ui.library

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.recommendations.ResolvedSimilarArtist
import com.subtracks.data.recommendations.SimilarArtist
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
