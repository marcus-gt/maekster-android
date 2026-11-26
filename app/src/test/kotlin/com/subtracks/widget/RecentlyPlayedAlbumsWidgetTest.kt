package com.subtracks.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.MainActivity
import com.subtracks.data.model.Album
import com.subtracks.data.model.Source
import com.subtracks.ui.ViewModelRepositoryFixture
import com.subtracks.ui.home.HomeSection
import com.subtracks.ui.home.buildHomeFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentlyPlayedAlbumsWidgetTest {
    private val fixture = ViewModelRepositoryFixture()
    private val db = fixture.db
    private val library = fixture.libraryRepository

    @After
    fun tearDown() = fixture.close()

    @Test
    fun mirrorsTheHomeRecentlyPlayedShelfAndMovesAlbumsAfterAPlay() =
        runTest {
            val sourceId = source()
            db.libraryDao().upsertAlbums(
                listOf(
                    album(sourceId, "a", played = 100),
                    album(sourceId, "b", played = 300),
                    album(sourceId, "c", played = 200),
                    album(sourceId, "new", played = null, created = 999),
                ) + (1..14).map { album(sourceId, "old$it", played = it.toLong()) },
            )
            val widget = content(sourceId)

            val shown = widget.first().albums.map { it.id }
            assertEquals(listOf("b", "c", "a") + (14 downTo 6).map { "old$it" }, shown)
            assertEquals(homeShelf(sourceId), shown.take(homeShelf(sourceId).size))

            db.libraryDao().upsertAlbums(listOf(album(sourceId, "a", played = 400)))
            assertEquals(
                listOf("a", "b", "c"),
                widget
                    .first { it.albums.first().id == "a" }
                    .albums
                    .take(3)
                    .map { it.id },
            )
        }

    @Test
    fun staysEmptyLikeHomeWithoutPlayData() =
        runTest {
            val sourceId = source()
            db.libraryDao().upsertAlbums(listOf(album(sourceId, "new", played = null, created = 999)))

            assertEquals(RecentAlbumsContent(sourceId), content(sourceId).first())
            assertEquals(emptyList<String>(), homeShelf(sourceId))
        }

    @Test
    fun headerOpensTheRecentlyPlayedList() {
        assertEquals(HomeSection.RecentlyPlayedAlbums, AlbumShelf.RecentlyPlayed.section)
        assertEquals(RecentlyPlayedAlbumsWidget::class.java, AlbumShelf.RecentlyPlayed.provider)
        assertEquals(AlbumShelf.RecentlyPlayed, AlbumShelf.forOpenAction(MainActivity.ACTION_OPEN_RECENTLY_PLAYED_ALBUMS))
        assertEquals(AlbumShelf.RecentlyAdded, AlbumShelf.forOpenAction(MainActivity.ACTION_OPEN_RECENT_ALBUMS))
    }

    private fun content(sourceId: Long): Flow<RecentAlbumsContent> =
        recentAlbumsContent(flowOf(sourceId)) { id, limit -> AlbumShelf.RecentlyPlayed.albums(library, id, limit) }

    private suspend fun homeShelf(sourceId: Long): List<String> {
        val recent = library.recentlyPlayedAlbums(sourceId, 10).first()
        val hasPlayData = library.hasAlbumPlayCount(sourceId).first() || library.hasAlbumPlayed(sourceId).first()
        return buildHomeFeed(
            recentAlbums = recent,
            recentArtists = emptyList(),
            frequentAlbums = emptyList(),
            frequentArtists = emptyList(),
            decades = emptyList(),
            starredSongs = emptyList(),
            addedAlbums = emptyList(),
            rediscover = emptyList(),
            hasPlayData = hasPlayData,
        ).recentlyPlayedAlbums.map { it.id }
    }

    private suspend fun source(): Long =
        db.sourcesDao().insertSource(Source(name = "Navidrome", address = "http://localhost", isActive = true, createdAt = 0))

    private fun album(
        sourceId: Long,
        id: String,
        played: Long?,
        created: Long = 0,
    ) = Album(
        sourceId = sourceId,
        id = id,
        artistId = null,
        name = id,
        albumArtist = null,
        created = created,
        coverArt = null,
        genre = null,
        year = null,
        starred = null,
        songCount = 1,
        played = played,
    )
}
