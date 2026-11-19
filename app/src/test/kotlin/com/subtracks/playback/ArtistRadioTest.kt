package com.subtracks.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.mix.InstantMix
import com.subtracks.data.mix.MixSeed
import com.subtracks.data.mix.SimilarSongs
import com.subtracks.data.mix.SmartShuffle
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import com.subtracks.data.model.Source
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.mixRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The artist menu and Discover's Artist radio tiles start the same mix, under the same name. */
@RunWith(AndroidJUnit4::class)
class ArtistRadioTest {
    private lateinit var db: SubtracksDatabase
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val seeds = mutableListOf<MixSeed>()
    private val labels = mutableListOf<String>()

    @Before
    fun setUp() {
        db = createTestDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun player(): Pair<InstantMixPlayer, Artist> {
        val sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
        db.libraryDao().upsertSongs(
            listOf("a", "b").map { Song(sourceId, it, "al", "ar-dylan", it, "Album", "Bob Dylan", 100, 1, 1, null) },
        )
        val similar =
            SimilarSongs { _, seed, _ ->
                seeds += seed
                emptyList()
            }
        val player =
            InstantMixPlayer(
                InstantMix(db.mixDao(), similar, SmartShuffle(db.mixDao())),
                { sourceId },
                { false },
                artistName = { id, artistId -> "Bob Dylan".takeIf { id == sourceId && artistId == "ar-dylan" } },
            ) { _, _, label -> labels += label }
        return player to Artist(sourceId, "ar-dylan", "Bob Dylan", 1, null)
    }

    @Test
    fun theMenuAndDiscoverStartTheSameArtistRadio() =
        runTest {
            val (player, artist) = player()

            val (menuSource, menuSeed) = MenuTarget.Artist(artist).mixRequest()!!
            assertTrue(player.play(menuSeed, expectedSourceId = menuSource))
            assertTrue(player.playArtistRadio(artist.sourceId, artist.id))

            assertEquals(listOf(MixSeed.Artist("ar-dylan"), MixSeed.Artist("ar-dylan")), seeds)
            assertEquals(2, labels.size)
            assertEquals(labels[0], labels[1])
            assertEquals("Bob Dylan radio", QueueCollection.displayName(labels[0], resources))
            assertEquals(QueueCollection.ArtistRadio, QueueCollection.fromStored(labels[0]))
            assertTrue("the radio keeps its name when it refills", QueueCollection.fromStored(labels[0])!!.endless)
        }

    @Test
    fun songMixesStayInstantMixAndAnUnnamedArtistIsStillArtistRadio() =
        runTest {
            val (player, artist) = player()

            assertTrue(player.play(MixSeed.Song("a"), expectedSourceId = artist.sourceId))
            assertEquals("Instant Mix", QueueCollection.displayName(labels.single(), resources))

            assertEquals("Artist radio", QueueCollection.displayName(QueueCollection.artistRadioLabel(null), resources))
            assertEquals("Jazz tunes", QueueCollection.displayName("Jazz tunes", resources))
        }
}
