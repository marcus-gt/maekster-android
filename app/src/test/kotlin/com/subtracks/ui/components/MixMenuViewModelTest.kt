package com.subtracks.ui.components

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.mix.MixSeed
import com.subtracks.data.model.Artist
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MixMenuViewModelTest {
    @Test
    fun duplicateRequestsAreIgnoredAndEmptyResultsExplainWhy() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            val result = CompletableDeferred<Boolean>()
            val calls = mutableListOf<Pair<Long, MixSeed>>()
            val messages = mutableListOf<UiMessage>()
            val model =
                MixMenuViewModel({ id, seed ->
                    calls += id to seed
                    result.await()
                }, messages::add)
            try {
                val artist = MenuTarget.Artist(Artist(4, "ar", "Artist", 1, null))
                model.start(artist)
                model.start(artist)
                assertEquals(listOf(4L to MixSeed.Artist("ar")), calls)
                result.complete(false)
                assertEquals(listOf(R.string.mix_preparing, R.string.mix_empty), messages.map { it.resId })
            } finally {
                model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Dispatchers.resetMain()
            }
        }
}
