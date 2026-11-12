package com.subtracks.ui.search

import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    @Test
    fun anExactSongTitleOutranksAnArtistPrefixAndAnAlbumSubstring() {
        val ranked =
            rankSearch(
                SearchResults(
                    artists = listOf(artist("a", "Lifehouse")),
                    albums = listOf(album("b", "A Day in the Life")),
                    songs = listOf(song("c", "Life")),
                ),
                "life",
                SearchScope.All,
            )

        assertEquals("song:c", ranked.top?.key)
        assertEquals(listOf(SearchScope.Artists, SearchScope.Albums), ranked.groups.map { it.scope })
        assertEquals(3, 1 + ranked.groups.sumOf { it.matches.size })
    }

    @Test
    fun titleAndArtistTokensMatchRegardlessOfOrderCaseAndAccents() {
        assertTrue(searchScore("Jóga", "Björk", "joga bjork") > 0)
        assertTrue(searchScore("Jóga", "Björk", "BJORK joga") > 0)
        assertEquals(1_000, searchScore("Jóga", "Björk", "JOGA"))
    }

    @Test
    fun scoresFollowTheIosTiers() {
        assertEquals(1_000, searchScore("Life", "", "life"))
        assertEquals(800, searchScore("Lifehouse", "", "life"))
        assertEquals(600, searchScore("A Day in the Life", "", "life"))
        assertEquals(500, searchScore("Dark Side of the Moon", "", "dark moon"))
        assertEquals(200, searchScore("Evening", "Soft jazz", "jazz"))
        assertEquals(0, searchScore("Metal", "", "jazz"))
        assertEquals(0, searchScore("Metal", "", " "))
        assertEquals(1_000, searchScore("AC/DC", "", "ac dc"))
    }

    @Test
    fun allPreviewsFivePerTypeWithoutRepeatingTheTopResult() {
        val songs = (1..7).map { song("s$it", "Road $it") }
        val ranked = rankSearch(SearchResults(songs = songs), "road", SearchScope.All)

        assertEquals("song:s1", ranked.top?.key)
        val group = ranked.groups.single()
        assertEquals((2..6).map { "song:s$it" }, group.matches.map { it.key })
        assertTrue(group.hasMore)
    }

    @Test
    fun aTypeScopeShowsAllOfThatTypeAndNoTopResult() {
        val songs = (1..7).map { song("s$it", "Road $it") }
        val ranked = rankSearch(SearchResults(songs = songs, albums = listOf(album("a", "Road"))), "road", SearchScope.Songs)

        assertNull(ranked.top)
        val group = ranked.groups.single()
        assertEquals(SearchScope.Songs, group.scope)
        assertEquals(7, group.matches.size)
        assertFalse(group.hasMore)
    }

    @Test
    fun withinATypeHigherScoresComeFirstAndTiesKeepTheDatabaseOrder() {
        val albums = listOf(album("z", "Road Z"), album("contains", "Long Road"), album("a", "Road A"), album("exact", "Road"))
        val ranked = rankSearch(SearchResults(albums = albums), "road", SearchScope.Albums)

        assertEquals(
            listOf("exact", "z", "a", "contains"),
            ranked.groups
                .single()
                .matches
                .map { (it as SearchMatch.AlbumMatch).album.id },
        )
    }

    @Test
    fun duplicateResultsDoNotCreateDuplicateRows() {
        val ranked = rankSearch(SearchResults(songs = listOf(song("c", "Life"), song("c", "Life"))), "life", SearchScope.All)

        assertEquals("song:c", ranked.top?.key)
        assertTrue(ranked.groups.isEmpty())
    }

    @Test
    fun noResultsRankAsEmpty() {
        assertTrue(rankSearch(SearchResults(), "life", SearchScope.All).isEmpty)
        assertTrue(rankSearch(SearchResults(albums = listOf(album("a", "Life"))), "life", SearchScope.Songs).isEmpty)
    }

    private fun album(
        id: String,
        name: String,
    ) = Album(1, id, null, name, null, 0, null, null, null, null, 1)

    private fun artist(
        id: String,
        name: String,
    ) = Artist(1, id, name, 1, null, null)

    private fun song(
        id: String,
        title: String,
    ) = AlbumSongItem(Song(1, id, null, null, title, null, null, null, null, null, null), coverArt = null)
}
