package com.subtracks.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.data.source.FakePlaylistWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickPlaylistRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var server: FakePlaylistWriter
    private lateinit var quick: QuickPlaylistRepository
    private val alsoFavorite = MutableStateFlow(true)
    private val favorited = mutableListOf<String>()
    private var online = true

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "home", "http://localhost", true, 1))
            db.sourcesDao().upsertSource(Source(2, "work", "http://localhost:2", true, 2))
            db.libraryDao().upsertSongs(
                listOf(
                    Song(1, "a", null, null, "A", null, null, 60, null, null, null),
                    Song(1, "liked", null, null, "Liked", null, null, 60, null, null, starred = 5),
                ),
            )
            server = FakePlaylistWriter(1)
            val playlists = PlaylistRepository(db, writerFor = { if (online) server else null })
            quick = QuickPlaylistRepository(db, playlists, alsoFavorite = { alsoFavorite }, favorite = { favorited += it })
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun mirror(
        id: String,
        name: String,
        vararg songIds: String,
        sourceId: Long = 1,
    ) {
        server.put(id, name, *songIds)
        db.libraryDao().replacePlaylist(
            Playlist(sourceId, id, name, null, null, songIds.size.toLong(), 1),
            songIds.mapIndexed { position, songId -> PlaylistSong(sourceId, id, songId, position.toLong()) },
        )
    }

    private suspend fun names(sourceId: Long = 1) = quick.quickPlaylists(sourceId).first().map { it.name }

    @Test
    fun toggleKeepsTheChosenOrderPerServerAndMoveReorders() =
        runTest {
            mirror("jazz", "Jazz tunes")
            mirror("techno", "Techno Chill")
            mirror("mix", "Mix")
            mirror("jazz", "Work jazz", sourceId = 2)
            quick.toggle(1, "techno")
            quick.toggle(1, "jazz")
            quick.toggle(1, "mix")
            assertEquals(listOf("Techno Chill", "Jazz tunes", "Mix"), names())
            assertEquals(emptyList<String>(), names(2))

            quick.move(1, from = 2, to = 0)
            assertEquals(listOf("Mix", "Techno Chill", "Jazz tunes"), names())
            quick.toggle(1, "techno")
            assertEquals(listOf("Mix", "Jazz tunes"), names())
            quick.toggle(1, "techno")
            assertEquals(listOf("Mix", "Jazz tunes", "Techno Chill"), names())
            quick.remove(1, "mix")
            assertEquals(listOf("Jazz tunes", "Techno Chill"), names())
        }

    @Test
    fun namesFollowTheMirrorAndDeletedPlaylistsDropOut() =
        runTest {
            mirror("jazz", "Jazz")
            mirror("gone", "Old")
            quick.toggle(1, "jazz")
            quick.toggle(1, "gone")
            mirror("jazz", "Jazz tunes")
            db.libraryDao().removePlaylist(1, "gone")
            assertEquals(listOf("Jazz tunes"), names())
            quick.move(1, from = 0, to = 0)
            assertEquals(listOf("Jazz tunes"), names())
        }

    @Test
    fun aQuickAddSendsOnlyAMissingSongAndFavoritesIt() =
        runTest {
            mirror("jazz", "Jazz tunes", "x")
            assertEquals(QuickAddOutcome.Added, quick.add(1, "jazz", "a"))
            assertEquals(listOf("x", "a"), server.songs("jazz"))
            assertEquals(listOf("x", "a"), db.libraryDao().playlistEntrySongIds(1, "jazz"))
            assertEquals(listOf("a"), favorited)
            assertEquals(
                listOf("jazz"),
                db
                    .playlistDestinationDao()
                    .recent(1)
                    .first()
                    .map { it.id },
            )

            assertEquals(QuickAddOutcome.AlreadyThere, quick.add(1, "jazz", "a"))
            assertEquals(1, server.appended.size)
            assertEquals(listOf("x", "a"), server.songs("jazz"))
        }

    @Test
    fun favoritingSkipsLikedSongsAndFollowsThePreference() =
        runTest {
            mirror("jazz", "Jazz tunes", "liked")
            assertEquals(QuickAddOutcome.AlreadyThere, quick.add(1, "jazz", "liked"))
            assertEquals(emptyList<String>(), favorited)
            alsoFavorite.value = false
            assertEquals(QuickAddOutcome.Added, quick.add(1, "jazz", "a"))
            assertEquals(emptyList<String>(), favorited)
        }

    @Test
    fun aFailedQuickAddFavoritesNothing() =
        runTest {
            mirror("jazz", "Jazz tunes")
            server.failNext = FakePlaylistWriter.Failure.BeforeWrite
            try {
                quick.add(1, "jazz", "a")
                fail("expected the server failure")
            } catch (expected: java.io.IOException) {
            }
            online = false
            try {
                quick.add(1, "jazz", "a")
                fail("expected offline")
            } catch (expected: PlaylistEditException) {
                assertEquals(PlaylistEditException.Reason.Offline, expected.reason)
            }
            assertTrue(favorited.isEmpty())
            assertEquals(emptyList<String>(), server.songs("jazz"))
        }
}
