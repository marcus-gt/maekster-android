package com.subtracks.widget

import com.subtracks.R
import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.mix.MoodShortfall
import com.subtracks.data.mix.QueueBuilderColumn
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderPill
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.playback.QueueBuilderOutcome
import com.subtracks.widget.QueueBuilderWidgetTap.Kind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueBuilderWidgetCommandsTest {
    private val index =
        QueueBuilderIndex(
            columns =
                listOf(
                    QueueBuilderColumn("Electronic", 10, listOf(QueueBuilderPill("techno", "Techno", 5))),
                    QueueBuilderColumn("Rock", 5, emptyList()),
                ),
            decades = mapOf(1990 to 1L, 2000 to 1L, 2010 to 1L),
        )
    private var saved = QueueBuilderPicks(length = 35)
    private val played = mutableListOf<Pair<QueueBuilderSelection, Int>>()
    private val building = mutableListOf<Boolean>()
    private val messages = mutableListOf<Int>()

    private fun commands(
        activeSourceId: Long? = 1,
        moods: Boolean = true,
        saveDelay: Long = 0,
        play: suspend () -> QueueBuilderOutcome = { QueueBuilderOutcome(true) },
    ) = QueueBuilderWidgetCommands(
        activeSourceId = { activeSourceId },
        picks = { saved },
        savePicks = { _, picks ->
            delay(saveDelay)
            saved = picks
        },
        index = { index },
        moodsAvailable = { moods },
        play = { selection, length ->
            played += selection to length
            play()
        },
        showBuilding = { building += it },
        showMessage = { messages += it },
    )

    @Test
    fun tapsEditTheSharedPicksAndKeepTheLength() =
        runTest {
            val commands = commands()
            commands.handle(QueueBuilderWidgetTap(1, Kind.Genre, "Rock"))
            commands.handle(QueueBuilderWidgetTap(1, Kind.Decades, DecadeGroup.Recent.name))
            commands.handle(QueueBuilderWidgetTap(1, Kind.Mood, QueueBuilderMood.PARTY.wireName))

            assertEquals(
                QueueBuilderPicks(
                    QueueBuilderSelection(mains = setOf("Rock"), decades = setOf(2000, 2010), moods = setOf(QueueBuilderMood.PARTY)),
                    length = 35,
                ),
                saved,
            )

            commands.handle(QueueBuilderWidgetTap(1, Kind.Mood, QueueBuilderMood.PARTY.wireName))
            commands.handle(QueueBuilderWidgetTap(1, Kind.Reset))
            assertEquals(QueueBuilderPicks(length = 35), saved)
        }

    @Test
    fun aGenreTapClearsItsSubGenresPickedInTheApp() =
        runTest {
            saved = QueueBuilderPicks(QueueBuilderSelection(tags = setOf("techno")))

            commands().handle(QueueBuilderWidgetTap(1, Kind.Genre, "Electronic"))

            assertEquals(QueueBuilderSelection(), saved.selection)
        }

    @Test
    fun quickTapsAreAllKeptBecauseEditsApplyOneAtATime() =
        runTest {
            val commands = commands(saveDelay = 10)
            val first = launch { commands.handle(QueueBuilderWidgetTap(1, Kind.Genre, "Rock")) }
            val second = launch { commands.handle(QueueBuilderWidgetTap(1, Kind.Mood, QueueBuilderMood.SAD.wireName)) }
            first.join()
            second.join()

            assertEquals(QueueBuilderSelection(mains = setOf("Rock"), moods = setOf(QueueBuilderMood.SAD)), saved.selection)
        }

    @Test
    fun playBuildsFromThePicksStillAvailableAndTheSavedLength() =
        runTest {
            saved =
                QueueBuilderPicks(
                    QueueBuilderSelection(mains = setOf("Rock", "Gone"), decades = setOf(1970, 2010), moods = setOf(QueueBuilderMood.SAD)),
                    length = 60,
                )

            commands(moods = false).handle(QueueBuilderWidgetTap(1, Kind.Play))

            assertEquals(listOf(QueueBuilderSelection(mains = setOf("Rock"), decades = setOf(2010)) to 60), played)
            assertEquals(listOf(true, false), building)
            assertEquals(emptyList<Int>(), messages)
        }

    @Test
    fun nothingStartedOrAFailureSaysWhy() =
        runTest {
            commands(play = { QueueBuilderOutcome(false) }).handle(QueueBuilderWidgetTap(1, Kind.Play))
            commands(play = { QueueBuilderOutcome(true, MoodShortfall.PARTIAL) }).handle(QueueBuilderWidgetTap(1, Kind.Play))
            commands(play = { error("database gone") }).handle(QueueBuilderWidgetTap(1, Kind.Play))

            assertEquals(listOf(R.string.queue_builder_no_match, R.string.queue_builder_moods_partial, R.string.mix_failed), messages)
            assertEquals(listOf(true, false, true, false, true, false), building)
        }

    @Test
    fun resumeContinuesThePausedQueueAndBuildsOnlyWhenNothingCanContinue() =
        runTest {
            var canResume = true
            var resumes = 0
            val commands =
                QueueBuilderWidgetCommands(
                    activeSourceId = { 1 },
                    picks = { saved },
                    savePicks = { _, picks -> saved = picks },
                    index = { index },
                    moodsAvailable = { true },
                    play = { selection, length ->
                        played += selection to length
                        QueueBuilderOutcome(true)
                    },
                    showBuilding = { building += it },
                    showMessage = { messages += it },
                    resume = {
                        resumes++
                        canResume
                    },
                )

            commands.handle(QueueBuilderWidgetTap(1, Kind.Resume))
            assertEquals(1, resumes)
            assertEquals(emptyList<Pair<QueueBuilderSelection, Int>>(), played)

            canResume = false
            commands.handle(QueueBuilderWidgetTap(1, Kind.Resume))
            assertEquals(1, played.size)
        }

    @Test
    fun pausePausesThePlayerAndBuildsNothing() =
        runTest {
            var pauses = 0
            val commands =
                QueueBuilderWidgetCommands(
                    activeSourceId = { 1 },
                    picks = { saved },
                    savePicks = { _, picks -> saved = picks },
                    index = { index },
                    moodsAvailable = { true },
                    play = { selection, length ->
                        played += selection to length
                        QueueBuilderOutcome(true)
                    },
                    showBuilding = { building += it },
                    showMessage = { messages += it },
                    pause = { pauses++ },
                )

            commands.handle(QueueBuilderWidgetTap(1, Kind.Pause))
            commands.handle(QueueBuilderWidgetTap(2, Kind.Pause))

            assertEquals(1, pauses)
            assertEquals(emptyList<Pair<QueueBuilderSelection, Int>>(), played)
        }

    @Test
    fun aWidgetStillShowingAnotherServerChangesNothing() =
        runTest {
            commands(activeSourceId = 2).handle(QueueBuilderWidgetTap(1, Kind.Genre, "Rock"))
            commands(activeSourceId = null).handle(QueueBuilderWidgetTap(1, Kind.Play))

            assertEquals(QueueBuilderPicks(length = 35), saved)
            assertEquals(emptyList<Pair<QueueBuilderSelection, Int>>(), played)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSlowBuildReleasesTheBroadcastInTimeAndStillFinishes() =
        runTest {
            val gate = CompletableDeferred<QueueBuilderOutcome>()
            val commands = commands(play = { gate.await() })
            val work = launch { commands.handle(QueueBuilderWidgetTap(1, Kind.Play)) }
            var releasedAt = -1L
            launch { releaseWithin(work, QueueBuilderWidget.BROADCAST_BUDGET_MS) { releasedAt = testScheduler.currentTime } }

            advanceTimeBy(QueueBuilderWidget.BROADCAST_BUDGET_MS + 1)
            assertEquals(QueueBuilderWidget.BROADCAST_BUDGET_MS, releasedAt)
            assertEquals(true, work.isActive)

            gate.complete(QueueBuilderOutcome(true))
            work.join()
            assertEquals(1, played.size)
            assertEquals(listOf(true, false), building)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aQuickTapReleasesTheBroadcastAsSoonAsItIsDone() =
        runTest {
            val work = launch { commands().handle(QueueBuilderWidgetTap(1, Kind.Genre, "Rock")) }
            var releasedAt = -1L

            releaseWithin(work, QueueBuilderWidget.BROADCAST_BUDGET_MS) { releasedAt = testScheduler.currentTime }

            assertEquals(0L, releasedAt)
            assertEquals(setOf("Rock"), saved.selection.mains)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playTapsWhileAQueueBuildsAreIgnored() =
        runTest {
            val gate = CompletableDeferred<QueueBuilderOutcome>()
            val commands = commands(play = { gate.await() })
            val first = launch { commands.handle(QueueBuilderWidgetTap(1, Kind.Play)) }
            runCurrent()

            val second = launch { commands.handle(QueueBuilderWidgetTap(1, Kind.Play)) }
            runCurrent()
            gate.complete(QueueBuilderOutcome(true))
            first.join()
            second.join()
            commands.handle(QueueBuilderWidgetTap(1, Kind.Play))

            assertEquals(2, played.size)
        }
}
