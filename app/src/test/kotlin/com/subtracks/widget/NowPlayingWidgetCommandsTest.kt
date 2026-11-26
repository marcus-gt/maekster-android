package com.subtracks.widget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingWidgetCommandsTest {
    private val calls = mutableListOf<String>()

    private fun commands(
        hasItem: Boolean,
        resumes: Boolean = true,
    ) = NowPlayingWidgetCommands(
        hasItem = { hasItem },
        togglePlayPause = { calls += "toggle" },
        resume = {
            calls += "resume"
            resumes
        },
        previous = { calls += "previous" },
        next = { calls += "next" },
        refresh = { calls += "refresh" },
    )

    @Test
    fun withAPlayingItemTheButtonsGoStraightToThePlayer() =
        runBlocking {
            val commands = commands(hasItem = true)
            commands.run(NowPlayingWidgetAction.PlayPause)
            commands.run(NowPlayingWidgetAction.Previous)
            commands.run(NowPlayingWidgetAction.Next)

            assertEquals(listOf("toggle", "previous", "next"), calls)
        }

    @Test
    fun afterTheAppStoppedPlayResumesTheSavedQueue() =
        runBlocking {
            commands(hasItem = false).run(NowPlayingWidgetAction.PlayPause)

            assertEquals(listOf("resume"), calls)
        }

    @Test
    fun whenNothingCanResumeTheWidgetRedrawsInsteadOfStayingStale() =
        runBlocking {
            commands(hasItem = false, resumes = false).run(NowPlayingWidgetAction.PlayPause)

            assertEquals(listOf("resume", "refresh"), calls)
        }

    @Test
    fun skippingWithoutAPlayerOnlyRedrawsAndNeverStartsPlayback() =
        runBlocking {
            val commands = commands(hasItem = false)
            commands.run(NowPlayingWidgetAction.Previous)
            commands.run(NowPlayingWidgetAction.Next)

            assertEquals(listOf("refresh", "refresh"), calls)
        }
}
