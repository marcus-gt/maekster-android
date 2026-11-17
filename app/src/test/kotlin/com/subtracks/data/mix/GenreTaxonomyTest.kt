package com.subtracks.data.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreTaxonomyTest {
    private val taxonomy = GenreTaxonomy.DISCOGS

    @Test
    fun theDiscogsMapHasFifteenMainGenres() {
        assertEquals(15, taxonomy.mainGenres.size)
        assertTrue("Folk, World, & Country" in taxonomy.mainGenres)
    }

    @Test
    fun aDiscogsStyleLandsUnderItsMainGenreWithItsCanonicalName() {
        val techno = taxonomy.resolve("techno")
        assertEquals(listOf("Electronic"), techno.mains)
        assertEquals("Techno", techno.styleName)
    }

    @Test
    fun aStyleListedUnderTwoGenresBelongsToBoth() {
        assertEquals(setOf("Electronic", "Rock"), taxonomy.resolve("Experimental").mains.toSet())
        assertEquals(setOf("Electronic", "Hip Hop"), taxonomy.resolve("Trip Hop").mains.toSet())
    }

    @Test
    fun spellingVariantsShareAKey() {
        assertEquals(taxonomy.resolve("Post Bop").key, taxonomy.resolve("post-bop").key)
        assertEquals(taxonomy.resolve("Drum And Bass").key, taxonomy.resolve("Drum n Bass").key)
        assertEquals(taxonomy.resolve("R&B").key, taxonomy.resolve("Rhythm & Blues").key)
    }

    @Test
    fun aMainGenreNameBeatsAStyleOfTheSameName() {
        // Discogs also lists "Hip Hop" as an Electronic style.
        val hipHop = taxonomy.resolve("Hip-Hop")
        assertEquals(listOf("Hip Hop"), hipHop.mains)
        assertTrue(hipHop.isMainTag)
        assertEquals(hipHop.key, taxonomy.resolve("hip hop").key)
    }

    @Test
    fun aStyleWithTheMainGenreWordMergesIntoTheStyle() {
        assertEquals(taxonomy.resolve("Modal").key, taxonomy.resolve("Modal Jazz").key)
        assertEquals(taxonomy.resolve("Fusion").key, taxonomy.resolve("Jazz Fusion").key)
    }

    @Test
    fun keywordsAndKnownSubgenresGetTheirOwnPill() {
        val eastCoast = taxonomy.resolve("East Coast Hip Hop")
        assertEquals(listOf("Hip Hop"), eastCoast.mains)
        assertFalse(eastCoast.isMainTag)
        val thirdStream = taxonomy.resolve("Third Stream")
        assertEquals(listOf("Jazz"), thirdStream.mains)
        assertFalse(thirdStream.isMainTag)
    }

    @Test
    fun synonymsAndAccentsResolveToTheMainGenre() {
        assertTrue(taxonomy.resolve("Rap").isMainTag)
        assertEquals(listOf("Hip Hop"), taxonomy.resolve("Rap").mains)
        assertEquals(listOf("Electronic"), taxonomy.resolve("Électronique").mains)
    }

    @Test
    fun anUnknownTagGoesToOther() {
        assertEquals(listOf(GenreTaxonomy.OTHER), taxonomy.resolve("Norway").mains)
    }
}
