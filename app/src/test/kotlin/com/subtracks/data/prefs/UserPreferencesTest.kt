package com.subtracks.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.data.net.NetworkMode
import com.subtracks.data.source.DEFAULT_FETCH_CONCURRENCY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {
    @Test
    fun freshReleasesStayOffUntilTurnedOn() =
        runTest {
            val prefs = fakeUserPreferences()
            assertEquals(false, prefs.freshReleasesEnabled().first())
            prefs.setFreshReleasesEnabled(true)
            assertEquals(true, prefs.freshReleasesEnabled().first())
        }

    @Test
    fun recommendedArtistPhotosStayOffUntilTurnedOn() =
        runTest {
            val prefs = fakeUserPreferences()
            assertEquals(false, prefs.externalArtistImagesEnabled().first())
            prefs.setExternalArtistImagesEnabled(true)
            assertEquals(true, prefs.externalArtistImagesEnabled().first())
            assertEquals(false, prefs.freshReleasesEnabled().first())
        }

    @Test
    fun queueBuilderPicksAreKeptPerServerWithALengthOnTheSliderSteps() =
        runTest {
            val prefs = fakeUserPreferences()
            assertEquals(QueueBuilderPicks(), prefs.queueBuilderPicks(1).first())
            val jazz90s =
                QueueBuilderPicks(QueueBuilderSelection(mains = setOf("Jazz"), tags = setOf("techno"), decades = setOf(1990)), length = 63)
            prefs.setQueueBuilderPicks(1, jazz90s)
            assertEquals(jazz90s.copy(length = 65), prefs.queueBuilderPicks(1).first())
            assertEquals(QueueBuilderPicks(), prefs.queueBuilderPicks(2).first())
            prefs.setQueueBuilderPicks(2, QueueBuilderPicks(length = 500))
            assertEquals(120, prefs.queueBuilderPicks(2).first().length)
            assertEquals(65, prefs.queueBuilderPicks(1).first().length)
        }

    @Test
    fun autoplayDefaultsOffAndPersistsAcrossReaders() =
        runTest {
            val file = File.createTempFile("autoplay", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            assertEquals(false, UserPreferences(store).autoplaySimilar().first())
            UserPreferences(store).setAutoplaySimilar(true)
            assertEquals(true, UserPreferences(store).autoplaySimilar().first())
            UserPreferences(store).setAutoplaySimilar(false)
            assertEquals(false, UserPreferences(store).autoplaySimilar().first())
            file.delete()
        }

    @Test
    fun quickPlaylistsAlsoFavoriteDefaultsToOnAndIsRemembered() =
        runTest {
            val file = File.createTempFile("quick-playlists", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            assertEquals(true, UserPreferences(store).quickPlaylistsAlsoFavorite().first())
            UserPreferences(store).setQuickPlaylistsAlsoFavorite(false)
            assertEquals(false, UserPreferences(store).quickPlaylistsAlsoFavorite().first())
            file.delete()
        }

    @Test
    fun albumLibraryLayoutDefaultsToGridAndPersistsAcrossReaders() =
        runTest {
            val file = File.createTempFile("album-layout", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)
            val query = ListQuery(AlbumSort.Name.name, descending = true, downloaded = true)
            prefs.setListQuery(LibraryListTab.Albums, query)
            assertEquals(false, prefs.albumsAsList().first())

            prefs.setAlbumsAsList(true)
            val reopened = UserPreferences(store)
            assertEquals(true, reopened.albumsAsList().first())
            assertEquals(query, reopened.listQuery(LibraryListTab.Albums).first())

            reopened.setAlbumsAsList(false)
            assertEquals(false, UserPreferences(store).albumsAsList().first())
            file.delete()
        }

    @Test
    fun artistLibraryGridPersistsIndependentlyOfAlbums() =
        runTest {
            val file = File.createTempFile("artist-layout", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)
            assertEquals(false, prefs.artistsAsGrid().first())
            prefs.setAlbumsAsList(true)
            prefs.setArtistsAsGrid(true)

            val reopened = UserPreferences(store)
            assertEquals(true, reopened.artistsAsGrid().first())
            assertEquals(true, reopened.albumsAsList().first())
            reopened.setArtistsAsGrid(false)
            assertEquals(true, reopened.albumsAsList().first())
            file.delete()
        }

    @Test
    fun homeLayoutPersistsVisibilityAndOrderAcrossReaders() =
        runTest {
            val file = File.createTempFile("home-layout", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)
            assertEquals(HomeLayout(), prefs.homeLayout().first())
            val custom =
                HomeLayout()
                    .move(
                        HomeShelf.FavoriteAlbums,
                        -1,
                    ).show(HomeShelf.Genres, false)
                    .grid(HomeShelf.FavoriteAlbums, true)
                    .grid(HomeShelf.Pinned, true)
            prefs.setHomeLayout(custom)
            assertEquals(custom, UserPreferences(store).homeLayout().first())
            store.edit { it[stringPreferencesKey("home_layout")] = "FavoriteAlbums,FavoriteAlbums,Unknown|Genres,Unknown" }
            val recovered = prefs.homeLayout().first()
            assertEquals(HomeShelf.entries.size, recovered.order.distinct().size)
            assertEquals(setOf(HomeShelf.Genres), recovered.hidden)
            assertEquals(HomeShelf.Pinned, recovered.order.first())
            prefs.setHomeLayout(HomeLayout())
            assertEquals(HomeLayout(), prefs.homeLayout().first())
            file.delete()
        }

    @Test
    fun homeGridCodecSupportsLegacyAndIgnoresUnsupportedShelves() {
        val legacy = HomeLayout.decode("FavoriteAlbums,YourPlaylists|Genres")
        assertEquals(emptySet<HomeShelf>(), legacy.grids)
        val decoded = HomeLayout.decode("FavoriteAlbums|Genres|FavoriteAlbums,YourPlaylists,Unknown,Genres,RecentlyStarredSongs")
        assertEquals(setOf(HomeShelf.FavoriteAlbums, HomeShelf.YourPlaylists, HomeShelf.Genres), decoded.grids)
        assertEquals(decoded, HomeLayout.decode(decoded.encode()))
        assertEquals(decoded.copy(grids = setOf(HomeShelf.YourPlaylists, HomeShelf.Genres)), decoded.grid(HomeShelf.FavoriteAlbums, false))
    }

    @Test
    fun homeLayoutCodecRecoversMalformedAndMissingSections() {
        assertEquals(HomeLayout(), HomeLayout.decode("broken"))
        assertEquals(HomeLayout(), HomeLayout.decode(null))
        val custom = HomeLayout().move(HomeShelf.Rediscover, -HomeShelf.entries.lastIndex).show(HomeShelf.YourPlaylists, false)
        assertEquals(custom, HomeLayout.decode(custom.encode()))
        assertEquals(HomeShelf.Rediscover, custom.visible.first())
        assertEquals(custom, custom.move(HomeShelf.Rediscover, -1))
        assertEquals(emptyList<HomeShelf>(), HomeLayout(hidden = HomeShelf.entries.toSet()).visible)
    }

    @Test
    fun listQueryRoundTripsPerTab() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(ListQuery("Name"), prefs.listQuery(LibraryListTab.Albums).first())
            assertEquals(ListQuery("Name"), prefs.listQuery(LibraryListTab.Artists).first())

            prefs.setListQuery(
                LibraryListTab.Albums,
                ListQuery("Added", descending = true, starred = StarredFilter.Starred, downloaded = true),
            )
            prefs.setListQuery(LibraryListTab.Artists, ListQuery("AlbumCount", descending = true))
            prefs.setListQuery(LibraryListTab.Playlists, ListQuery("Updated", starred = StarredFilter.NotStarred))

            assertEquals(
                ListQuery("Added", descending = true, starred = StarredFilter.Starred, downloaded = true),
                prefs.listQuery(LibraryListTab.Albums).first(),
            )
            assertEquals(
                ListQuery("AlbumCount", descending = true),
                prefs.listQuery(LibraryListTab.Artists).first(),
            )
            assertEquals(
                ListQuery("Updated", starred = StarredFilter.NotStarred),
                prefs.listQuery(LibraryListTab.Playlists).first(),
            )

            file.delete()
        }

    @Test
    fun theSongsListIgnoresTheOldSubtracksSongsTabQuery() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            store.edit { it[stringPreferencesKey("list_query_songs")] = "Added|0|1|0" }
            val prefs = UserPreferences(store)

            assertEquals(ListQuery("Name"), prefs.listQuery(LibraryListTab.Songs).first())
            prefs.setListQuery(LibraryListTab.Songs, ListQuery("Artist", descending = true))
            assertEquals(ListQuery("Artist", descending = true), prefs.listQuery(LibraryListTab.Songs).first())

            file.delete()
        }

    @Test
    fun theSortNameModeRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(SortNameMode.Name, prefs.sortNameMode().first())

            prefs.setSortNameMode(SortNameMode.NonLatin)

            assertEquals(SortNameMode.NonLatin, prefs.sortNameMode().first())

            file.delete()
        }

    @Test
    fun theThemeFollowsTheSystemUntilChosenAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(ThemeMode.System, prefs.themeMode().first())

            prefs.setThemeMode(ThemeMode.Light)
            assertEquals(ThemeMode.Light, prefs.themeMode().first())

            prefs.setThemeMode(ThemeMode.Dark)
            assertEquals(ThemeMode.Dark, prefs.themeMode().first())

            file.delete()
        }

    @Test
    fun anUnknownStoredThemeFallsBackToTheSystem() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            store.edit { it[stringPreferencesKey("theme_mode")] = "Sepia" }

            assertEquals(ThemeMode.System, prefs.themeMode().first())

            file.delete()
        }

    @Test
    fun materialYouIsOffUntilChosenAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(false, prefs.materialYou().first())

            prefs.setMaterialYou(true)
            assertEquals(true, prefs.materialYou().first())

            prefs.setMaterialYou(false)
            assertEquals(false, prefs.materialYou().first())

            file.delete()
        }

    @Test
    fun aListQueryStoredBeforeTheDownloadedFilterStillDecodes() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            store.edit { it[stringPreferencesKey("list_query_albums")] = "Added|1|1" }

            assertEquals(
                ListQuery("Added", descending = true, starred = StarredFilter.Starred),
                prefs.listQuery(LibraryListTab.Albums).first(),
            )

            file.delete()
        }

    @Test
    fun streamQualityRoundTripsPerNetworkMode() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(StreamQuality(), prefs.streamQuality(NetworkMode.Wifi).first())
            assertEquals(StreamQuality(192, "mp3"), prefs.streamQuality(NetworkMode.Mobile).first())

            prefs.setStreamQuality(NetworkMode.Wifi, StreamQuality(320, null))
            prefs.setStreamQuality(NetworkMode.Mobile, StreamQuality(96, "opus"))

            assertEquals(StreamQuality(320, null), prefs.streamQuality(NetworkMode.Wifi).first())
            assertEquals(StreamQuality(96, "opus"), prefs.streamQuality(NetworkMode.Mobile).first())

            file.delete()
        }

    @Test
    fun downloadQualityAndMeteredRoundTrip() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(StreamQuality(), prefs.downloadQuality().first())
            assertEquals(false, prefs.downloadOverMetered().first())

            prefs.setDownloadQuality(StreamQuality(192, "opus"))
            prefs.setDownloadOverMetered(true)

            assertEquals(StreamQuality(192, "opus"), prefs.downloadQuality().first())
            assertEquals(true, prefs.downloadOverMetered().first())

            file.delete()
        }

    @Test
    fun syncConcurrencyDefaultsAndClampsToAtLeastOne() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(DEFAULT_FETCH_CONCURRENCY, prefs.syncConcurrency().first())

            prefs.setSyncConcurrency(8)
            assertEquals(8, prefs.syncConcurrency().first())

            prefs.setSyncConcurrency(0)
            assertEquals(1, prefs.syncConcurrency().first())

            file.delete()
        }

    @Test
    fun scrobblingDefaultsOnAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(true, prefs.scrobbling().first())

            prefs.setScrobbling(false)
            assertEquals(false, prefs.scrobbling().first())

            file.delete()
        }

    @Test
    fun keepScreenOnDefaultsOffAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(false, prefs.keepScreenOn().first())

            prefs.setKeepScreenOn(true)
            assertEquals(true, prefs.keepScreenOn().first())

            prefs.setKeepScreenOn(false)
            assertEquals(false, prefs.keepScreenOn().first())

            file.delete()
        }

    @Test
    fun lastSeedRoundTripsWithAndWithoutASecondary() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(null, prefs.lastSeed())

            prefs.setLastSeed(ArtworkSeedValue("art:1", 0xFF112233.toInt(), 0xFF445566.toInt()))
            assertEquals(
                ArtworkSeedValue("art:1", 0xFF112233.toInt(), 0xFF445566.toInt()),
                prefs.lastSeed(),
            )

            prefs.setLastSeed(ArtworkSeedValue("art:2", 0xFF778899.toInt(), null))
            assertEquals(ArtworkSeedValue("art:2", 0xFF778899.toInt(), null), prefs.lastSeed())

            file.delete()
        }

    @Test
    fun verboseLoggingDefaultsOffAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(false, prefs.verboseLogging().first())

            prefs.setVerboseLogging(true)
            assertEquals(true, prefs.verboseLogging().first())

            file.delete()
        }

    @Test
    fun replayGainDefaultsToAutoModeAndRoundTrips() =
        runTest {
            val file = File.createTempFile("user-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)

            assertEquals(ReplayGainMode.Auto, prefs.replayGainMode().first())
            assertEquals(0f, prefs.replayGainPreampDb().first(), 0f)

            prefs.setReplayGainMode(ReplayGainMode.Album)
            prefs.setReplayGainPreampDb(3.5f)

            assertEquals(ReplayGainMode.Album, prefs.replayGainMode().first())
            assertEquals(3.5f, prefs.replayGainPreampDb().first(), 0f)

            prefs.setReplayGainMode(ReplayGainMode.Auto)
            assertEquals(ReplayGainMode.Auto, prefs.replayGainMode().first())

            prefs.setReplayGainPreampDb(99f)
            assertEquals(MAX_REPLAY_GAIN_PREAMP_DB, prefs.replayGainPreampDb().first(), 0f)

            prefs.setReplayGainPreampDb(-99f)
            assertEquals(MIN_REPLAY_GAIN_PREAMP_DB, prefs.replayGainPreampDb().first(), 0f)

            prefs.setReplayGainPreampDb(Float.NaN)
            assertEquals(0f, prefs.replayGainPreampDb().first(), 0f)

            file.delete()
        }
}
