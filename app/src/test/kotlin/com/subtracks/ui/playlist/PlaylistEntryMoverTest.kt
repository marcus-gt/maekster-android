package com.subtracks.ui.playlist

import com.subtracks.R
import com.subtracks.UiMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistEntryMoverTest {
    @Test
    fun aMoveKeepsItsOccurrenceAndDestinationAndBlocksAnotherUntilReadbackFinishes() =
        runTest {
            val finish = CompletableDeferred<Unit>()
            val calls = mutableListOf<Triple<Long, String, Int>>()
            val messages = mutableListOf<UiMessage>()
            val mover =
                PlaylistEntryMover(backgroundScope, { position, song, destination ->
                    calls += Triple(position, song, destination)
                    finish.await()
                }, messages::add)
            mover.move(3, "duplicate", 2)
            assertTrue(mover.busy.value)
            mover.move(0, "other", 1)
            runCurrent()
            assertEquals(listOf(Triple(3L, "duplicate", 2)), calls)
            assertTrue(messages.isEmpty())
            finish.complete(Unit)
            runCurrent()
            assertFalse(mover.busy.value)
            assertEquals(listOf(UiMessage(R.string.playlist_song_moved)), messages)
        }

    @Test
    fun aFailureReportsAnErrorAndAllowsAnotherAttemptWithoutSuccess() =
        runTest {
            var fail = true
            val messages = mutableListOf<UiMessage>()
            val mover = PlaylistEntryMover(backgroundScope, { _, _, _ -> if (fail) throw IOException("offline") }, messages::add)
            mover.move(1, "song", 0)
            runCurrent()
            assertFalse(mover.busy.value)
            assertEquals(1, messages.size)
            assertFalse(messages.single() == UiMessage(R.string.playlist_song_moved))
            fail = false
            mover.move(1, "song", 0)
            runCurrent()
            assertEquals(UiMessage(R.string.playlist_song_moved), messages.last())
        }
}
