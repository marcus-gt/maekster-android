package com.subtracks.data.mix

import com.subtracks.data.model.Artist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPickerTest {
    private val day = 86_400L

    private fun artists(
        prefix: String,
        count: Int,
    ) = (1..count).map { Artist(1, "$prefix$it", "$prefix $it", 1, null) }

    @Test
    fun dormancyIsNinetyDaysOrHalfTheHistoryButAtLeastTwoWeeks() {
        val now = 1_000 * day
        assertEquals(90 * day, ArtistPicker.dormancy(null, now))
        assertEquals(90 * day, ArtistPicker.dormancy(now - 365 * day, now))
        assertEquals(50 * day, ArtistPicker.dormancy(now - 100 * day, now))
        assertEquals(14 * day, ArtistPicker.dormancy(now - 20 * day, now))
    }

    @Test
    fun picksSixFavouritesFourDormantAndFillsWithFreshArtists() {
        val played = artists("p", 40)
        val dormant = artists("d", 26)
        val fresh = artists("f", 12)

        val picks = ArtistPicker.pick(played, dormant, fresh, seed = 7)

        assertEquals(12, picks.map { it.id }.distinct().size)
        assertEquals(6, picks.count { it in played.take(18) })
        assertEquals(4, picks.count { it in dormant.take(20) })
        assertEquals(2, picks.count { it in fresh })
        assertEquals(picks, ArtistPicker.pick(played, dormant, fresh, seed = 7))
        assertNotEquals(picks, ArtistPicker.pick(played, dormant, fresh, seed = 8))
    }

    @Test
    fun dormantPicksSkipTheChosenFavourites() {
        val played = artists("p", 18)

        val picks = ArtistPicker.pick(played, dormant = played, fresh = artists("f", 5), seed = 3)

        assertEquals(12, picks.map { it.id }.distinct().size)
        assertEquals(10, picks.count { it in played })
        assertEquals(2, picks.count { it.id.startsWith("f") })
    }

    @Test
    fun smallLibrariesFillFromWhateverIsLeft() {
        val played = artists("p", 2)
        val fresh = artists("f", 3)

        val picks = ArtistPicker.pick(played, emptyList(), fresh, seed = 1)

        assertEquals((played + fresh).map { it.id }.toSet(), picks.map { it.id }.toSet())
        assertTrue(ArtistPicker.pick(emptyList(), emptyList(), emptyList(), seed = 1).isEmpty())
    }
}
