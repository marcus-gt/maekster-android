package com.subtracks.widget

import com.subtracks.R
import com.subtracks.playback.QueueCollection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioStationsWidgetCommandsTest {
    private val started = mutableListOf<Pair<Long, String>>()
    private val starting = mutableListOf<String?>()
    private val messages = mutableListOf<Int>()
    private var toggles = 0

    private fun commands(
        activeSourceId: Long? = 1,
        currentRadio: String? = null,
        play: suspend (Long, String) -> Boolean = { _, _ -> true },
    ) = RadioStationsWidgetCommands(
        activeSourceId = { activeSourceId },
        playArtistRadio = { sourceId, artistId ->
            started += sourceId to artistId
            play(sourceId, artistId)
        },
        showStartingArtist = { starting += it },
        showMessage = { messages += it },
        currentRadio = { currentRadio },
        togglePlayPause = { toggles++ },
    )

    @Test
    fun aTapStartsThatArtistsRadioAndMarksItWhileItStarts() =
        runTest {
            commands().startArtistRadio(1, "a", "A")

            assertEquals(listOf(1L to "a"), started)
            assertEquals(listOf("a", null), starting)
            assertEquals(emptyList<Int>(), messages)
            assertEquals(0, toggles)
        }

    @Test
    fun aTapOnTheRadioThatIsPlayingPausesOrResumesItInsteadOfRestarting() =
        runTest {
            commands(currentRadio = QueueCollection.artistRadioLabel("A")).startArtistRadio(1, "a", "A")

            assertEquals(1, toggles)
            assertEquals(emptyList<Pair<Long, String>>(), started)

            commands(currentRadio = QueueCollection.artistRadioLabel("B")).startArtistRadio(1, "a", "A")
            assertEquals(1, toggles)
            assertEquals(listOf(1L to "a"), started)
        }

    @Test
    fun nothingToPlaySaysWhyAndAFailureToo() =
        runTest {
            commands(play = { _, _ -> false }).startArtistRadio(1, "a", "A")
            commands(play = { _, _ -> error("server down") }).startArtistRadio(1, "b", "B")

            assertEquals(listOf(R.string.mix_empty, R.string.mix_failed), messages)
            assertEquals(listOf("a", null, "b", null), starting)
        }

    @Test
    fun aWidgetStillShowingAnotherServerStartsNothing() =
        runTest {
            commands(activeSourceId = 2).startArtistRadio(1, "a", "A")
            commands(activeSourceId = null, currentRadio = QueueCollection.artistRadioLabel("A")).startArtistRadio(1, "a", "A")

            assertEquals(emptyList<Pair<Long, String>>(), started)
            assertEquals(emptyList<String?>(), starting)
            assertEquals(0, toggles)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun tapsWhileAStationStartsAreIgnored() =
        runTest {
            val gate = CompletableDeferred<Boolean>()
            val commands = commands(play = { _, _ -> gate.await() })
            val first = launch { commands.startArtistRadio(1, "a", "A") }
            runCurrent()

            val second = launch { commands.startArtistRadio(1, "b", "B") }
            runCurrent()
            gate.complete(true)
            first.join()
            second.join()
            commands.startArtistRadio(1, "c", "C")

            assertEquals(listOf(1L to "a", 1L to "c"), started)
        }
}
