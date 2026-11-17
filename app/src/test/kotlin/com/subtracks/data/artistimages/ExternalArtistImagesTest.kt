package com.subtracks.data.artistimages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.musicbrainz.MbReleaseGroup
import com.subtracks.data.musicbrainz.MusicBrainz
import com.subtracks.data.recommendations.SimilarArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ExternalArtistImagesTest {
    private val burial = "9ddce51c-2b75-4b3e-ac8c-1db09e7c89c6"
    private val bjork = "87c5dedd-371d-4a53-9f7f-80522fb7f3cb"
    private val other = "5b11f4ce-a62d-471e-81fc-a69a8278c7da"

    private val musicBrainz = FakeMusicBrainz()
    private val files = mutableMapOf<String, String>()
    private val fileLookups = mutableListOf<String>()
    private val wikidata =
        Wikidata { item ->
            fileLookups += item
            files[item]
        }

    private fun TestScope.images(maxEntries: Int = ExternalArtistImages.MAX_ENTRIES) =
        ExternalArtistImages(musicBrainz, wikidata, backgroundScope, maxEntries)

    @Test
    fun anIdLeadsThroughWikidataToACommonsRendition() =
        runTest {
            musicBrainz.items[bjork] = "Q129"
            files["Q129"] = "Björk Cannes 2000.jpg"

            val url = images().imageUrl(SimilarArtist(null, "Björk", musicBrainzId = " ${bjork.uppercase()} "))

            assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Bj%C3%B6rk_Cannes_2000.jpg?width=500", url)
            assertEquals(listOf(bjork), musicBrainz.itemLookups)
            assertTrue(musicBrainz.nameLookups.isEmpty())
        }

    @Test
    fun withoutAUsableIdOnlyACertainNameMatchIsFollowed() =
        runTest {
            val images = images()
            musicBrainz.names["Burial"] = burial
            musicBrainz.items[burial] = "Q1"
            files["Q1"] = "Burial.jpg"

            assertEquals(
                ExternalArtistImages.commonsFileUrl("Burial.jpg"),
                images.imageUrl(SimilarArtist(null, " Burial ", musicBrainzId = "not-an-id")),
            )
            assertEquals(listOf("Burial"), musicBrainz.nameLookups)

            assertNull(images.imageUrl(SimilarArtist(null, "Somebody Unknown")))
            assertEquals(listOf(burial), musicBrainz.itemLookups)
        }

    @Test
    fun answersAreCachedButFailuresAreTriedAgain() =
        runTest {
            val images = images()
            assertNull(images.imageUrlForId(other))
            assertNull(images.imageUrlForId(other.uppercase()))
            assertEquals(1, musicBrainz.itemLookups.size)

            musicBrainz.names["Burial"] = burial
            images.imageUrlForName("Burial")
            images.imageUrlForName("  burial ")
            assertEquals(1, musicBrainz.nameLookups.size)

            musicBrainz.fail = true
            musicBrainz.items[bjork] = "Q129"
            files["Q129"] = "Björk.jpg"
            assertNull(images.imageUrlForId(bjork))
            musicBrainz.fail = false
            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), images.imageUrlForId(bjork))
            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), images.imageUrlForId(bjork))
            assertEquals(listOf(other, burial, bjork, bjork), musicBrainz.itemLookups)
            assertEquals(listOf("Q129"), fileLookups)
        }

    @Test
    fun concurrentRequestsShareOneLookupThatOutlivesACancelledWaiter() =
        runTest {
            val images = images()
            musicBrainz.items[bjork] = "Q129"
            files["Q129"] = "Björk.jpg"
            val gate = CompletableDeferred<Unit>()
            musicBrainz.gate = gate

            val waiters = List(3) { async { images.imageUrlForId(bjork) } }
            runCurrent()
            assertEquals(1, musicBrainz.itemLookups.size)

            waiters[0].cancel()
            runCurrent()
            assertTrue(waiters[0].isCancelled)
            gate.complete(Unit)

            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), waiters[1].await())
            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), waiters[2].await())
            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), images.imageUrlForId(bjork))
            assertEquals(1, musicBrainz.itemLookups.size)
            assertTrue(!musicBrainz.cancelled.isCompleted)
        }

    @Test
    fun aLookupNobodyWaitsForIsCancelledAndNotCached() =
        runTest {
            val images = images()
            musicBrainz.items[bjork] = "Q129"
            files["Q129"] = "Björk.jpg"
            musicBrainz.gate = CompletableDeferred()

            val waiters = List(2) { async { images.imageUrlForId(bjork) } }
            runCurrent()
            waiters.forEach { it.cancel() }
            runCurrent()
            musicBrainz.cancelled.await()

            musicBrainz.gate = null
            assertEquals(ExternalArtistImages.commonsFileUrl("Björk.jpg"), images.imageUrlForId(bjork))
            assertEquals(2, musicBrainz.itemLookups.size)
        }

    @Test
    fun blankNamesAndBadIdsMakeNoRequests() =
        runTest {
            val images = images()
            assertNull(images.imageUrlForName("   "))
            assertNull(images.imageUrlForName("!!!"))
            assertNull(images.imageUrlForId("../artist"))
            assertNull(images.imageUrl(SimilarArtist(null, "", musicBrainzId = "")))
            assertTrue(musicBrainz.nameLookups.isEmpty() && musicBrainz.itemLookups.isEmpty() && fileLookups.isEmpty())
        }

    @Test
    fun theCacheKeepsOnlyTheMostRecentArtists() =
        runTest {
            val images = images(maxEntries = 2)
            images.imageUrlForId(bjork)
            images.imageUrlForId(burial)
            images.imageUrlForId(bjork)
            images.imageUrlForId(other)
            images.imageUrlForId(bjork)
            images.imageUrlForId(burial)
            assertEquals(listOf(bjork, burial, other, burial), musicBrainz.itemLookups)
        }

    @Test
    fun commonsFileNamesAreEncodedAsOnePathSegment() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/Special:FilePath/AC%2FDC_live_%3F%23.jpg?width=500",
            ExternalArtistImages.commonsFileUrl("AC/DC live ?#.jpg"),
        )
    }

    private class FakeMusicBrainz : MusicBrainz {
        val names = mutableMapOf<String, String>()
        val items = mutableMapOf<String, String>()
        val nameLookups = mutableListOf<String>()
        val itemLookups = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        val cancelled = CompletableDeferred<Unit>()
        var fail = false

        override suspend fun releaseGroups(
            artistIds: List<String>,
            from: String,
            to: String,
        ): List<MbReleaseGroup> = emptyList()

        override suspend fun artistId(name: String): String? {
            nameLookups += name
            return names[name]
        }

        override suspend fun wikidataId(artistId: String): String? {
            itemLookups += artistId
            try {
                gate?.await()
            } catch (failure: CancellationException) {
                cancelled.complete(Unit)
                throw failure
            }
            if (fail) throw IOException("MusicBrainz down")
            return items[artistId]
        }
    }
}
