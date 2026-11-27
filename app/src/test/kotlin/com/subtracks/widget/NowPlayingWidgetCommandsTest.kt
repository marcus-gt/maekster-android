package com.subtracks.widget

import com.subtracks.R
import com.subtracks.data.audiomuse.MoodRadioResult
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.playback.QueueCollection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingWidgetCommandsTest {
    private val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    private fun commands(
        hasItem: Boolean,
        resumes: Boolean = true,
        queueLabel: String? = null,
        playMood: suspend (RadioMood) -> MoodRadioResult = { MoodRadioResult.Ready(listOf("s")) },
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
        playMood = { mood ->
            calls += "mood ${mood.key}"
            playMood(mood)
        },
        showStartingMood = { calls += "starting ${it?.key}" },
        showMessage = { calls += "message $it" },
        queueLabel = { queueLabel },
    )

    @Test
    fun aTapOnTheMoodRadioThatIsPlayingPausesOrResumesIt() =
        runBlocking {
            val chill = QueueCollection.forMood(RadioMood.CHILL).storedLabel
            commands(hasItem = true, queueLabel = chill).startMood(RadioMood.CHILL)
            assertEquals(listOf("toggle"), calls)

            calls.clear()
            commands(hasItem = true, queueLabel = chill).startMood(RadioMood.FOCUS)
            commands(hasItem = false, queueLabel = chill).startMood(RadioMood.CHILL)
            assertEquals(listOf("starting focus", "mood focus", "starting null", "starting chill", "mood chill", "starting null"), calls)
        }

    @Test
    fun aMoodTapStartsThatRadioAndShowsItStartingMeanwhile() =
        runBlocking {
            commands(hasItem = false).startMood(RadioMood.CHILL)

            assertEquals(listOf("starting chill", "mood chill", "starting null"), calls)
        }

    @Test
    fun aMoodRadioThatCannotStartSaysWhy() =
        runBlocking {
            commands(hasItem = true, playMood = { MoodRadioResult.Unavailable }).startMood(RadioMood.NIGHT)
            commands(hasItem = true, playMood = { MoodRadioResult.NoMatches }).startMood(RadioMood.NIGHT)
            commands(hasItem = true, playMood = { error("server down") }).startMood(RadioMood.NIGHT)

            assertEquals(
                listOf(R.string.mood_radio_unavailable, R.string.mood_radio_empty, R.string.mood_radio_failed).map { "message $it" },
                calls.filter { it.startsWith("message") },
            )
            assertEquals("starting null", calls.last())
        }

    @Test
    fun aSecondMoodTapWhileOneStartsIsIgnored() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val commands =
                commands(hasItem = true, playMood = {
                    release.await()
                    MoodRadioResult.Ready(listOf("s"))
                })
            val first = launch(Dispatchers.Default) { commands.startMood(RadioMood.FOCUS) }
            while ("mood focus" !in synchronized(calls) { calls.toList() }) yield()
            commands.startMood(RadioMood.NIGHT)
            release.complete(Unit)
            first.join()
            commands.startMood(RadioMood.NIGHT)

            assertEquals(
                listOf("starting focus", "mood focus", "starting null", "starting night", "mood night", "starting null"),
                calls,
            )
        }

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
