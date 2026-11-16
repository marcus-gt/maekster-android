package com.subtracks.data.mix

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StationArtistsTest {
    @Test
    fun stationsAreBoundedSourceScopedAndPreferFavoritesThenRecentPlays() =
        runTest {
            val db = createTestDatabase()
            try {
                val source = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
                val other = db.sourcesDao().insertSource(Source(name = "o", address = "http://o", isActive = false, createdAt = 0))
                db.libraryDao().upsertArtists(
                    (1..20).map { Artist(source, "$it", "Artist $it", 1, null) } +
                        Artist(source, "star", "Z Favorite", 1, 1) +
                        Artist(source, "recent", "Z Recent", 1, null, played = 10) +
                        Artist(other, "foreign", "A Foreign", 1, 1) +
                        Artist(source, "uncredited", "A Hidden", 1, 1, credited = false),
                )
                val stations = db.mixDao().stationArtists(source).first()
                assertEquals(12, stations.size)
                assertEquals(listOf("star", "recent"), stations.take(2).map { it.id })
                assertEquals(setOf(source), stations.map { it.sourceId }.toSet())
                assertEquals(false, stations.any { it.id == "uncredited" })
            } finally {
                db.close()
            }
        }
}
