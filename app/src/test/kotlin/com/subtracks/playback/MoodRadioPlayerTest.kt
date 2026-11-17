package com.subtracks.playback

import com.subtracks.data.audiomuse.MoodRadioResult
import com.subtracks.data.audiomuse.RadioMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodRadioPlayerTest {
    private val started = mutableListOf<Triple<Long, List<String>, RadioMood>>()

    @Test fun onlyAReadyRadioOnTheStillActiveSourceStartsPlayback() =
        runBlocking {
            var result: MoodRadioResult = MoodRadioResult.Ready(listOf("b", "a"))
            val built = mutableListOf<Pair<Long, RadioMood>>()
            val player =
                MoodRadioPlayer({ sourceId, mood ->
                    built += sourceId to mood
                    result
                }, { 3L }) { sourceId, songIds, mood -> started += Triple(sourceId, songIds, mood) }
            assertEquals(result, player.play(RadioMood.CHILL))
            assertEquals(listOf(Triple(3L, listOf("b", "a"), RadioMood.CHILL)), started)
            for (other in listOf(MoodRadioResult.NoMatches, MoodRadioResult.NotInLibrary, MoodRadioResult.Unavailable)) {
                result = other
                assertEquals(other, player.play(RadioMood.FOCUS))
            }
            assertEquals(1, started.size)
            assertEquals(3L to RadioMood.FOCUS, built.last())
        }

    @Test fun noActiveSourceOrASourceSwitchWhileBuildingStartsNothing() =
        runBlocking {
            val noSource = MoodRadioPlayer({ _, _ -> error("Unexpected build") }, { null }) { s, i, m -> started += Triple(s, i, m) }
            assertEquals(MoodRadioResult.Unavailable, noSource.play(RadioMood.NIGHT))
            var active = 1L
            val switching =
                MoodRadioPlayer({ _, _ ->
                    active = 2L
                    MoodRadioResult.Ready(listOf("a"))
                }, { active }) { s, i, m -> started += Triple(s, i, m) }
            assertTrue(runCatching { switching.play(RadioMood.NIGHT) }.exceptionOrNull() is CancellationException)
            assertTrue(started.isEmpty())
        }

    @Test fun eachMoodQueueKeepsItsOwnEndlessLabel() {
        val collections = RadioMood.entries.map(QueueCollection::forMood)
        assertEquals(RadioMood.entries.size, collections.toSet().size)
        assertTrue(collections.all { it.endless && it.kind == null })
        assertEquals(collections, collections.map { QueueCollection.fromStored(it.storedLabel) })
    }
}
