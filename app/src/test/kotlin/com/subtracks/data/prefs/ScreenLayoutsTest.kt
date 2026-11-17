package com.subtracks.data.prefs

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenLayoutsTest {
    @Test fun discoverCodecKeepsCustomOrderAndToleratesUnknownMissingAndDuplicateSections() {
        val layout = DiscoverLayout.decode("QueueBuilder,QueueBuilder,Unknown,SmartShuffle|Stations")
        assertTrue(layout.order.indexOf(DiscoverShelf.QueueBuilder) < layout.order.indexOf(DiscoverShelf.SmartShuffle))
        assertEquals(4, layout.order.distinct().size)
        assertFalse(DiscoverShelf.Stations in layout.visible)
        assertEquals(layout, DiscoverLayout.decode(layout.encode()))
        assertEquals(DiscoverLayout(), DiscoverLayout.decode("broken"))
    }

    @Test fun legacyHomeOrderVisibilityAndGridsSurviveNewSections() {
        val legacyOrder =
            listOf(
                HomeShelf.Rediscover,
                HomeShelf.Pinned,
                HomeShelf.YourPlaylists,
                HomeShelf.FavoriteAlbums,
                HomeShelf.RecentlyAddedAlbums,
                HomeShelf.RecentlyPlayedArtists,
                HomeShelf.RecentlyStarredSongs,
                HomeShelf.MostPlayedAlbums,
                HomeShelf.Genres,
                HomeShelf.RecentlyPlayedAlbums,
                HomeShelf.MostPlayedArtists,
                HomeShelf.Decades,
            )
        val decoded = HomeLayout.decode(legacyOrder.joinToString(",") { it.name } + "|Genres|FavoriteAlbums")
        assertEquals(legacyOrder, decoded.order.filter { it in legacyOrder })
        assertEquals(setOf(HomeShelf.Genres), decoded.hidden)
        assertEquals(setOf(HomeShelf.FavoriteAlbums), decoded.grids)
        assertTrue(HomeShelf.QueueBuilder in decoded.order)
        assertTrue(HomeShelf.ArtistPicks in decoded.order)
        assertEquals(HomeShelf.Pinned, HomeLayout().visible.first())
        assertFalse(HomeShelf.Decades in HomeLayout().visible)
        assertFalse(HomeShelf.MostPlayedArtists in HomeLayout().visible)
    }

    @Test fun layoutsAreIndependentAndResetNeverGrantsConsentOrChangesAutoplay() =
        runTest {
            val prefs = fakeUserPreferences()
            val home = HomeLayout().show(HomeShelf.QueueBuilder, false).grid(HomeShelf.Genres, true)
            prefs.setHomeLayout(home)
            prefs.setDiscoverLayout(DiscoverLayout(hidden = setOf(DiscoverShelf.QueueBuilder, DiscoverShelf.FreshReleases)))
            assertEquals(home, prefs.homeLayout().first())
            assertFalse(prefs.freshReleasesEnabled().first())
            assertFalse(prefs.autoplaySimilar().first())
            prefs.setDiscoverLayout(DiscoverLayout())
            assertFalse(prefs.freshReleasesEnabled().first())
            assertFalse(prefs.autoplaySimilar().first())
            assertEquals(home, prefs.homeLayout().first())
            prefs.setFreshReleasesEnabled(true)
            prefs.setAutoplaySimilar(true)
            prefs.setHomeLayout(HomeLayout())
            assertTrue(prefs.freshReleasesEnabled().first())
            assertTrue(prefs.autoplaySimilar().first())
        }
}
