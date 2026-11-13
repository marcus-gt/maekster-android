package com.subtracks.ui.playlist

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSheetScopeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    class SessionModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun dismissalClearsTheSessionAndReopeningTheSameSongCreatesAFreshOne() {
        val request = AddToPlaylistRequest(1, listOf("song"))
        val shown = mutableStateOf<AddToPlaylistRequest?>(request)
        lateinit var current: SessionModel
        rule.setContent {
            shown.value?.let {
                PlaylistSheetScope(it) {
                    current = viewModel(factory = viewModelFactory { initializer { SessionModel() } })
                }
            }
        }
        rule.waitForIdle()
        val first = current
        rule.runOnIdle { shown.value = null }
        rule.waitForIdle()
        assertTrue(first.cleared)
        rule.runOnIdle { shown.value = request }
        rule.waitForIdle()
        assertNotSame(first, current)
        assertFalse(current.cleared)
    }

    @Test
    fun requestChangesClearTheOldSessionButEqualRequestsKeepIt() {
        val request = mutableStateOf(AddToPlaylistRequest(1, listOf("song")))
        val tick = mutableStateOf(0)
        lateinit var current: SessionModel
        rule.setContent {
            tick.value
            PlaylistSheetScope(request.value) {
                current = viewModel(factory = viewModelFactory { initializer { SessionModel() } })
            }
        }
        rule.waitForIdle()
        val first = current
        rule.runOnIdle { tick.value++ }
        rule.waitForIdle()
        assertSame(first, current)
        assertFalse(first.cleared)
        rule.runOnIdle { request.value = AddToPlaylistRequest(2, listOf("song")) }
        rule.waitForIdle()
        assertTrue(first.cleared)
        assertNotSame(first, current)
    }
}
