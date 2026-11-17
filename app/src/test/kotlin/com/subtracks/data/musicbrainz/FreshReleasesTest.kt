package com.subtracks.data.musicbrainz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class FreshReleasesTest {
    private lateinit var db: SubtracksDatabase
    private var sourceId = 0L
    private val musicBrainz = FakeMusicBrainz()

    /** 27 October 2026, noon UTC. */
    private var now = 1_793_102_400_000L
    private val hour = 3_600_000L
    private lateinit var feed: FreshReleases

    private val burial = "9ddce51c-2b75-4b3e-ac8c-1db09e7c89c6"
    private val bonobo = "9a709693-b4f8-4da9-8cc1-038c911a61be"
    private val various = "89ad4ac3-39f7-470e-963a-56509c546377"

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            sourceId = db.sourcesDao().insertSource(Source(name = "s", address = "http://x", isActive = true, createdAt = 0))
            feed = FreshReleases(db.freshReleaseDao(), musicBrainz) { now }
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun library(vararg artists: Triple<String, String?, List<String>>) {
        db.libraryDao().upsertArtists(artists.map { (name, mbid, _) -> Artist(sourceId, "ar-$name", name, 1, null, musicBrainzId = mbid) })
        db.libraryDao().upsertAlbums(
            artists.flatMap { (name, _, albums) ->
                albums.mapIndexed { index, title ->
                    Album(sourceId, "al-$name-$index", "ar-$name", title, name, 0, null, null, null, null, 1)
                }
            },
        )
    }

    private fun group(
        id: String,
        title: String,
        date: String?,
        vararg artistIds: String,
    ) = MbReleaseGroup(id, title, "Album", emptyList(), date, artistIds.map { MbCredit(it, "Artist $it") })

    @Test
    fun followsTaggedArtistsAndListsOnlyReleasedRecordsTheLibraryLacksNewestFirst() =
        runTest {
            library(
                Triple("Burial", burial, listOf("Untrue", "Antidawn (Deluxe Edition)")),
                Triple("Bonobo", "12345", listOf("Migration")),
                Triple("Various Artists", various, listOf("Compilation")),
            )
            musicBrainz.groups[burial] =
                listOf(
                    group("g-old", "Streetlands", "2026-09-01", burial),
                    group("g-new", "Dreamfear", "2026-10-20", burial),
                    group("g-owned", "Antidawn", "2026-10-01", burial),
                    group("g-future", "Later", "2026-12-01", burial),
                )

            assertTrue(feed.refresh(sourceId) { true })

            val releases = feed.releases(sourceId).first()
            assertEquals(listOf("g-new", "g-old"), releases.map { it.id })
            assertEquals("Album", releases.first().typeLabel)
            // Only the real UUID is searched: a Discogs number is no MusicBrainz id, and Various Artists is never followed.
            assertEquals(listOf(listOf(burial)), musicBrainz.searches)
            assertEquals(listOf("2026-07-29" to "2026-10-27"), musicBrainz.windows.distinct())
            // Bonobo's tag is not a UUID, so its name was looked up.
            assertEquals(listOf("Bonobo"), musicBrainz.lookups)
        }

    @Test
    fun aReleaseOwnedUnderAnotherCreditedArtistIsLeftOut() =
        runTest {
            library(Triple("Burial", burial, listOf("Untrue")), Triple("Bonobo", bonobo, listOf("Collab")))
            val collab = group("g-collab", "Collab", "2026-10-01", burial, bonobo)
            musicBrainz.groups[burial] = listOf(collab)
            musicBrainz.groups[bonobo] = listOf(collab)

            feed.refresh(sourceId) { true }

            assertEquals(emptyList<String>(), feed.releases(sourceId).first().map { it.id })
        }

    @Test
    fun namesAreLookedUpOnceAndUnresolvedOnesRetriedAfterThirtyDays() =
        runTest {
            library(Triple("Bonobo", null, listOf("Migration")), Triple("Nobody", null, listOf("Nothing")))
            musicBrainz.names["Bonobo"] = bonobo
            musicBrainz.groups[bonobo] = listOf(group("g-b", "Fragments", "2026-10-10", bonobo))

            feed.refresh(sourceId) { true }
            assertEquals(listOf("g-b"), feed.releases(sourceId).first().map { it.id })
            assertEquals(listOf("Bonobo", "Nobody"), musicBrainz.lookups)

            now += 21 * hour
            musicBrainz.lookups.clear()
            feed.refresh(sourceId) { true }
            assertEquals(emptyList<String>(), musicBrainz.lookups)
            // The cached id is searched straight away in the first pass.
            assertEquals(listOf(bonobo), musicBrainz.searches.last())

            now += 31 * 24 * hour
            feed.refresh(sourceId) { true }
            assertEquals(listOf("Nobody"), musicBrainz.lookups)
        }

    @Test
    fun aFailedLookupIsNotRememberedAndAFailedSearchKeepsThePreviousResult() =
        runTest {
            library(Triple("Burial", burial, listOf("Untrue")), Triple("Bonobo", null, listOf("Migration")))
            musicBrainz.groups[burial] = listOf(group("g1", "Dreamfear", "2026-10-20", burial))
            musicBrainz.failLookups = true
            feed.refresh(sourceId) { true }
            assertEquals(listOf("g1"), feed.releases(sourceId).first().map { it.id })

            // The failed lookup was not stored, so the next refresh tries the name again.
            musicBrainz.failLookups = false
            now += 21 * hour
            feed.refresh(sourceId) { true }
            assertEquals(listOf("Bonobo", "Bonobo"), musicBrainz.lookups)

            musicBrainz.failSearches = true
            now += 21 * hour
            assertTrue(runCatching { feed.refresh(sourceId) { true } }.isFailure)
            assertEquals(listOf("g1"), feed.releases(sourceId).first().map { it.id })
        }

    @Test
    fun stalenessFollowsTheRefreshIntervalAndBacksOffAfterAnAttempt() =
        runTest {
            library(Triple("Burial", burial, listOf("Untrue")))
            assertTrue(feed.isStale(sourceId))
            feed.refresh(sourceId) { true }
            now += 19 * hour
            assertFalse(feed.isStale(sourceId))
            now += 2 * hour
            assertTrue(feed.isStale(sourceId))

            musicBrainz.failSearches = true
            runCatching { feed.refreshIfStale(sourceId) { true } }
            // A failed attempt is not retried for fifteen minutes.
            now += 10 * 60_000L
            assertFalse(feed.isStale(sourceId))
            now += 6 * 60_000L
            assertTrue(feed.isStale(sourceId))
        }

    @Test
    fun aServerSwitchDuringTheRefreshStoresNothing() =
        runTest {
            library(Triple("Burial", burial, listOf("Untrue")))
            musicBrainz.groups[burial] = listOf(group("g1", "Dreamfear", "2026-10-20", burial))
            feed.refresh(sourceId) { false }
            assertEquals(emptyList<String>(), feed.releases(sourceId).first().map { it.id })
        }

    @Test
    fun manyArtistsAreSearchedInPagesOfTwentyFive() =
        runTest {
            library(
                *Array(30) { index ->
                    Triple(
                        "Artist ${index.toString().padStart(2, '0')}",
                        "00000000-0000-0000-0000-0000000000${index.toString().padStart(2, '0')}",
                        listOf("A$index"),
                    )
                },
            )
            feed.refresh(sourceId) { true }
            assertEquals(listOf(25, 5), musicBrainz.searches.map { it.size })
        }

    private class FakeMusicBrainz : MusicBrainz {
        val groups = mutableMapOf<String, List<MbReleaseGroup>>()
        val names = mutableMapOf<String, String>()
        val searches = mutableListOf<List<String>>()
        val windows = mutableListOf<Pair<String, String>>()
        val lookups = mutableListOf<String>()
        var failSearches = false
        var failLookups = false

        override suspend fun releaseGroups(
            artistIds: List<String>,
            from: String,
            to: String,
        ): List<MbReleaseGroup> {
            if (artistIds.isEmpty()) return emptyList()
            if (failSearches) throw IOException("MusicBrainz down")
            searches += artistIds
            windows += from to to
            return artistIds.flatMap { groups[it].orEmpty() }
        }

        override suspend fun artistId(name: String): String? {
            lookups += name
            if (failLookups) throw IOException("MusicBrainz down")
            return names[name]
        }

        override suspend fun wikidataId(artistId: String): String? = null
    }
}
