package com.subtracks.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentlyChangedPlaylistsDaoTest {
    private lateinit var db: SubtracksDatabase

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theNewestChangeComesFirstAndAMissingChangeDateFallsBackToCreation() =
        runTest {
            val source = db.sourcesDao().insertSource(Source(name = "a", address = "http://localhost", isActive = true, createdAt = 0))
            val other = db.sourcesDao().insertSource(Source(name = "b", address = "http://localhost", isActive = false, createdAt = 0))
            db.libraryDao().upsertPlaylists(
                listOf(
                    playlist(source, "old", created = 100, changed = 200),
                    playlist(source, "edited", created = 100, changed = 900),
                    // No change date: sorted by its creation, between the two above.
                    playlist(source, "created-only", created = 500, changed = 0),
                    // Same moment: by name, case-insensitively.
                    playlist(source, "b-tie", created = 0, changed = 300),
                    playlist(source, "A-tie", created = 0, changed = 300),
                    playlist(other, "elsewhere", created = 0, changed = 10_000),
                ),
            )

            assertEquals(
                listOf("edited", "created-only", "A-tie", "b-tie", "old"),
                db
                    .libraryDao()
                    .recentlyChangedPlaylists(source, 10)
                    .first()
                    .map { it.id },
            )
            assertEquals(
                listOf("edited", "created-only"),
                db
                    .libraryDao()
                    .recentlyChangedPlaylists(source, 2)
                    .first()
                    .map { it.id },
            )
        }

    private fun playlist(
        source: Long,
        id: String,
        created: Long,
        changed: Long,
    ) = Playlist(
        sourceId = source,
        id = id,
        name = id,
        comment = null,
        coverArt = null,
        songCount = 0,
        created = created,
        changed = changed,
    )
}
