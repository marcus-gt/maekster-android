package com.subtracks.data.db

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Source
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDestinationsTest {
    private lateinit var db: SubtracksDatabase

    @Before
    fun setUp() =
        runTest {
            db = createTestDatabase()
            db.sourcesDao().upsertSource(Source(1, "one", "http://localhost", true, 1))
            db.sourcesDao().upsertSource(Source(2, "two", "http://localhost", true, 2))
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ownerSourceAndRecentExclusionsApplyBeforePaging() =
        runTest {
            db.libraryDao().upsertPlaylists(
                listOf(
                    playlist("mine", "Zulu"),
                    playlist("legacy", "Bravo").copy(owner = null),
                    playlist("recent", "Alpha"),
                    playlist("shared", "Another").copy(owner = "bob", shared = true),
                    playlist("private", "Before").copy(owner = "bob"),
                    playlist("foreign", "First").copy(sourceId = 2),
                ),
            )
            assertEquals(listOf("legacy", "mine"), first(owner = "alice", exclude = listOf("recent")).data.map { it.id })
            assertEquals(
                listOf("recent", "shared", "private", "legacy", "mine"),
                first(owner = null).data.map { it.id },
            )
            assertEquals(listOf("foreign"), first(sourceId = 2).data.map { it.id })
        }

    @Test
    fun searchMatchesSubstringWithoutTreatingEscapedWildcardsAsPatterns() =
        runTest {
            db.libraryDao().upsertPlaylists(
                listOf(
                    playlist("literal", "A 100%_mix\\live set"),
                    playlist("wildcard", "A 100XXmix live set"),
                    playlist("case", "A MIXED set"),
                    playlist("other", "Elsewhere"),
                ),
            )
            assertEquals(listOf("literal"), first(pattern = "%100\\%\\_mix\\\\live%").data.map { it.id })
            assertEquals(listOf("case", "literal", "wildcard").sorted(), first(pattern = "%mIx%").data.map { it.id }.sorted())
            assertEquals(emptyList<Playlist>(), first(pattern = "%missing%").data)
        }

    @Test
    fun pagesKeepCaseInsensitiveNameOrderAndIdTieBreaksWithoutGaps() =
        runTest {
            val expected = (0 until 125).map { "p" + it.toString().padStart(3, '0') }
            db.libraryDao().upsertPlaylists(
                expected.reversed().mapIndexed { index, id ->
                    val number = id.drop(1).toInt()
                    playlist(id, (if (index % 2 == 0) "mix " else "MIX ") + (number / 3).toString().padStart(3, '0'))
                },
            )
            val source = db.libraryDao().playlistDestinations(1, "alice", emptyList(), "%")
            try {
                var page = source.load(PagingSource.LoadParams.Refresh(null, 7, false)) as PagingSource.LoadResult.Page
                assertEquals(7, page.data.size)
                assertNull(page.prevKey)
                val ids = page.data.map { it.id }.toMutableList()
                while (page.nextKey != null) {
                    page = source.load(PagingSource.LoadParams.Append(page.nextKey!!, 7, false)) as PagingSource.LoadResult.Page
                    ids += page.data.map { it.id }
                }
                assertEquals(expected, ids)
                assertEquals(125, ids.distinct().size)
            } finally {
                source.invalidate()
            }
        }

    private suspend fun first(
        sourceId: Long = 1,
        owner: String? = "alice",
        exclude: List<String> = emptyList(),
        pattern: String = "%",
    ): PagingSource.LoadResult.Page<Int, Playlist> {
        val source = db.libraryDao().playlistDestinations(sourceId, owner, exclude, pattern)
        return try {
            source.load(PagingSource.LoadParams.Refresh(null, 20, false)) as PagingSource.LoadResult.Page
        } finally {
            source.invalidate()
        }
    }

    private fun playlist(
        id: String,
        name: String,
    ) = Playlist(1, id, name, null, null, 0, 1, owner = "alice")
}
