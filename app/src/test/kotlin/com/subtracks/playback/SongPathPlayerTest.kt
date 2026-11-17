package com.subtracks.playback

import com.subtracks.data.audiomuse.AudioMuseMood
import com.subtracks.data.audiomuse.AudioMusePathEnd
import com.subtracks.data.audiomuse.SongPathResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPathPlayerTest {
    private val started = mutableListOf<Pair<Long, List<String>>>()
    private val from = AudioMusePathEnd.Song("a")
    private val to = AudioMusePathEnd.Mood(AudioMuseMood.HAPPY)

    @Test fun onlyAReadyPathOnTheStillActiveSourceStartsPlayback() =
        runBlocking {
            var result: SongPathResult = SongPathResult.Ready(listOf("a", "b"))
            val player = SongPathPlayer({ _, _, _, _ -> result }, { 4L }) { sourceId, ids -> started += sourceId to ids }
            assertEquals(result, player.play(from, to, 25))
            for (other in listOf(SongPathResult.NoPath, SongPathResult.InvalidEnds, SongPathResult.Unavailable)) {
                result = other
                assertEquals(other, player.play(from, to, 25))
            }
            assertEquals(listOf(4L to listOf("a", "b")), started)

            var active: Long? = 1L
            val switching =
                SongPathPlayer({ _, _, _, _ ->
                    active = 2L
                    SongPathResult.Ready(listOf("a"))
                }, { active }) { sourceId, ids -> started += sourceId to ids }
            assertTrue(runCatching { switching.play(from, to, 25) }.exceptionOrNull() is CancellationException)
            active = null
            assertEquals(SongPathResult.Unavailable, switching.play(from, to, 25))
            assertEquals(1, started.size)
        }

    @Test fun songPathIsTheOnlyFiniteCollection() {
        assertEquals(listOf(QueueCollection.SongPath), QueueCollection.entries.filter { it.finite })
        assertTrue(!QueueCollection.SongPath.endless)
    }
}
