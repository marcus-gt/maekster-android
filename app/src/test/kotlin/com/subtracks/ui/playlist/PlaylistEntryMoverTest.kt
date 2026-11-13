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
    fun retryUsesTheRecoveryOperationAndCancelForgetsTheFailedMove() =
        runTest {
            var calls = 0
            var retries = 0
            val finish = CompletableDeferred<Unit>()
            val mover =
                PlaylistEntryMover(
                    backgroundScope,
                    { _, _, _ ->
                        calls++
                        throw IOException("offline")
                    },
                    {},
                    retryMove = {
                        retries++
                        finish.await()
                    },
                )
            mover.move(3, "song", 2)
            runCurrent()
            mover.move(0, "other", 1)
            runCurrent()
            assertEquals(1, calls)
            mover.retry()
            runCurrent()
            mover.retry()
            mover.dismissFailure()
            assertTrue(mover.error.value != null)
            assertTrue(mover.busy.value)
            finish.complete(Unit)
            runCurrent()
            assertEquals(1, retries)
            assertEquals(null, mover.error.value)
            mover.move(3, "song", 2)
            runCurrent()
            mover.dismissFailure()
            mover.retry()
            runCurrent()
            assertEquals(1, retries)
            assertEquals(null, mover.error.value)
        }

    @Test
    fun abandonmentWaitsForReadbackAndNeverReportsMoveSuccess() =
        runTest {
            val finish = CompletableDeferred<Unit>()
            var abandoned = 0
            var retries = 0
            val messages = mutableListOf<UiMessage>()
            val mover =
                PlaylistEntryMover(
                    backgroundScope,
                    { _, _, _ -> throw IOException("write failed") },
                    messages::add,
                    retryMove = { retries++ },
                    abandonMove = {
                        abandoned++
                        finish.await()
                    },
                )
            mover.move(1, "song", 0)
            runCurrent()
            messages.clear()
            mover.dismissFailure()
            assertTrue(mover.busy.value)
            mover.retry()
            mover.dismissFailure()
            runCurrent()
            assertEquals(1, abandoned)
            assertTrue(mover.error.value != null)
            finish.complete(Unit)
            runCurrent()
            assertFalse(mover.busy.value)
            assertEquals(null, mover.error.value)
            assertEquals(0, retries)
            assertTrue(messages.isEmpty())
        }

    @Test
    fun failedAbandonmentStillClosesWithAnErrorAndCannotReplay() =
        runTest {
            val messages = mutableListOf<UiMessage>()
            var retries = 0
            val mover =
                PlaylistEntryMover(
                    backgroundScope,
                    { _, _, _ -> throw IOException("write failed") },
                    messages::add,
                    retryMove = { retries++ },
                    abandonMove = { throw IOException("read failed") },
                )
            mover.move(1, "song", 0)
            runCurrent()
            messages.clear()
            mover.dismissFailure()
            runCurrent()
            assertFalse(mover.busy.value)
            assertEquals(null, mover.error.value)
            mover.retry()
            runCurrent()
            assertEquals(0, retries)
            assertEquals(1, messages.size)
            assertFalse(messages.single() == UiMessage(R.string.playlist_song_moved))
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
            assertTrue(mover.error.value != null)
            fail = false
            mover.retry()
            runCurrent()
            assertEquals(null, mover.error.value)
            assertEquals(UiMessage(R.string.playlist_song_moved), messages.last())
        }
}
