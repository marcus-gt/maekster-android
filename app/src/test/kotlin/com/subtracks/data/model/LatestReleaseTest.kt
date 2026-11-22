package com.subtracks.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestReleaseTest {
    @Test
    fun theNewestYearWinsWhateverTheListOrder() {
        val albums = listOf(album("old", year = 1999), album("new", year = 2020), album("mid", year = 2010))

        assertEquals("new", latestRelease(albums)?.id)
    }

    @Test
    fun aTieGoesToTheMostRecentlyAddedAlbum() {
        val albums =
            listOf(
                album("first", year = 2020, created = 100),
                album("added-later", year = 2020, created = 300),
                album("older", year = 2019, created = 900),
            )

        assertEquals("added-later", latestRelease(albums)?.id)
    }

    @Test
    fun aFullTieKeepsTheFirstInListOrder() {
        val albums = listOf(album("a", year = 2020, created = 5), album("b", year = 2020, created = 5))

        assertEquals("a", latestRelease(albums)?.id)
    }

    @Test
    fun albumsWithoutAYearAreNeverPicked() {
        assertNull(latestRelease(listOf(album("none", year = null), album("zero", year = 0))))
        assertEquals("dated", latestRelease(listOf(album("none", year = null, created = 999), album("dated", year = 1970)))?.id)
    }

    private fun album(
        id: String,
        year: Long?,
        created: Long = 0,
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar",
        name = id,
        albumArtist = "Artist",
        created = created,
        coverArt = null,
        genre = null,
        year = year,
        starred = null,
        songCount = 10,
    )
}
