package com.subtracks.playback

import com.subtracks.data.mix.QueueBuilderBuild
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueBuilderPlayerTest {
    private val remembered = mutableListOf<Pair<Long, QueueBuilderPicks>>()

    private fun player(songs: List<String>) =
        QueueBuilderPlayer(
            build = { _, _, _, _ -> QueueBuilderBuild(songs) },
            activeSourceId = { 1 },
            downloadedOnly = { false },
            start = { _, _ -> },
            rememberBuilt = { sourceId, built -> remembered += sourceId to built },
        )

    @Test
    fun aStartedQueueRemembersWhatItWasBuiltFrom() =
        runTest {
            val jazz = QueueBuilderSelection(mains = setOf("Jazz"))

            player(listOf("a", "b")).play(jazz, 40)

            assertEquals(listOf(1L to QueueBuilderPicks(jazz, 40)), remembered)
        }

    @Test
    fun nothingIsRememberedWhenNothingStarted() =
        runTest {
            player(emptyList()).play(QueueBuilderSelection(mains = setOf("Jazz")), 40)

            assertEquals(emptyList<Pair<Long, QueueBuilderPicks>>(), remembered)
        }
}
