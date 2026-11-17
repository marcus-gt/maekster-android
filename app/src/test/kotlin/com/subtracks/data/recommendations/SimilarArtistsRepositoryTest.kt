package com.subtracks.data.recommendations

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createTestDatabase
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimilarArtistsRepositoryTest {
    private lateinit var db: SubtracksDatabase
    private lateinit var cache: SimilarArtistsCache
    private lateinit var repository: SimilarArtistsRepository
    private var active: Long? = 1
    private var offline = false
    private var calls = 0
    private var candidates = emptyList<SimilarArtist>()
    private var onFetch: () -> Unit = {}

    @Before fun setUp() {
        db = createTestDatabase()
        cache =
            SimilarArtistsCache(db.similarArtistsCacheDao(), {
                SimilarArtists { _, _ ->
                    calls++
                    onFetch()
                    candidates
                }
            })
        repository = SimilarArtistsRepository(cache, db.libraryDao(), { active }, { offline })
    }

    @After fun tearDown() = db.close()

    private suspend fun seed(vararg artists: Artist) {
        for (id in 1L..2L) db.sourcesDao().upsertSource(Source(id, "server", "https://server", id == 1L, id))
        db.libraryDao().upsertArtists(artists.toList())
    }

    private fun artist(
        id: String,
        name: String,
        mbid: String? = null,
        source: Long = 1,
    ) = Artist(source, id, name, 1, null, musicBrainzId = mbid)

    @Test fun resolvesByIdThenUniqueMbidThenUniqueNameAndLeavesExternalCandidates() =
        runTest {
            seed(artist("id", "Renamed"), artist("mb", "Tagged", "ABC"), artist("name", "Name"))
            candidates =
                listOf(
                    SimilarArtist("id", "Old name"),
                    SimilarArtist(null, "Different spelling", musicBrainzId = "abc"),
                    SimilarArtist(null, "name"),
                    SimilarArtist(null, "External"),
                )
            val resolved = repository.refresh(1, "seed")
            assertEquals(listOf("id", "mb", "name", null), resolved.map { it.libraryArtist?.id })
            assertEquals(candidates, resolved.map { it.recommendation })
            offline = true
            assertEquals(resolved, repository.read(1, "seed"))
            assertEquals(1, calls)
        }

    @Test fun ambiguousNamesConflictingMbidsAndOtherServersNeverBecomeLocalLinks() =
        runTest {
            seed(
                artist("a", "Duplicate"),
                artist("b", "Duplicate"),
                artist("c", "Same name", "known"),
                artist("foreign", "Foreign", source = 2),
            )
            candidates =
                listOf(
                    SimilarArtist(null, "Duplicate"),
                    SimilarArtist(null, "Same name", musicBrainzId = "different"),
                    SimilarArtist("foreign", "Foreign"),
                )
            assertEquals(listOf(null, null, null), repository.refresh(1, "seed").map { it.libraryArtist })
        }

    @Test fun removesSelfAndMultipleRecommendationsResolvingToTheSameLocalArtist() =
        runTest {
            seed(artist("seed", "Seed"), artist("a", "Artist", "mb"))
            candidates =
                listOf(SimilarArtist("seed", "Seed"), SimilarArtist("a", "Artist"), SimilarArtist(null, "Artist", musicBrainzId = "mb"))
            assertEquals(listOf("a"), repository.refresh(1, "seed").map { it.libraryArtist?.id })
        }

    @Test fun readsResolveCurrentMirrorAndNeverRefreshWhileOfflineOrForAnotherSource() =
        runTest {
            seed()
            candidates = listOf(SimilarArtist(null, "Added later"))
            repository.refresh(1, "seed")
            db.libraryDao().upsertArtists(listOf(artist("new", "Added later")))
            assertEquals(
                "new",
                repository
                    .read(1, "seed")
                    .single()
                    .libraryArtist
                    ?.id,
            )
            offline = true
            repository.refresh(1, "uncached")
            offline = false
            active = 2
            assertEquals(emptyList<ResolvedSimilarArtist>(), repository.refresh(1, "uncached"))
            assertEquals(1, calls)
        }

    @Test fun serverSwitchDuringFetchDoesNotPublishOrReturnTheOldServersResult() =
        runTest {
            seed()
            candidates = listOf(SimilarArtist("a", "Artist"))
            onFetch = { active = 2 }
            assertEquals(emptyList<ResolvedSimilarArtist>(), repository.refresh(1, "seed"))
            assertEquals(emptyList<SimilarArtist>(), cache.read(1, "seed"))
        }
}
