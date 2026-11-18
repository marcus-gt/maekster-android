package com.subtracks.di

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.cancelAndJoinBlocking
import com.subtracks.data.lyrics.LyricsRepository
import com.subtracks.data.net.ServerHeaders
import com.subtracks.data.repo.DownloadRepository
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.playback.PlaybackController
import com.subtracks.playback.QueueBuilderPlayer
import com.subtracks.ui.discover.QueueBuilderViewModel
import com.subtracks.ui.playback.LyricsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppModuleTest {
    private lateinit var koin: Koin
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        stopKoin()
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        try {
            if (::koin.isInitialized) {
                cancelAndJoinBlocking(
                    koin.get<CoroutineScope>(),
                    koin.get<CoroutineScope>(named("io")),
                    koin.get<CoroutineScope>(named("playback")),
                )
            }
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theModuleResolvesSimilarArtistsAsSingletons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val headers = ServerHeaders()
        koin = startKoin { modules(appModule(context, OkHttpClient(), headers)) }.koin
        assertSame(
            koin.get<com.subtracks.data.recommendations.SimilarArtistsCache>(),
            koin.get<com.subtracks.data.recommendations.SimilarArtistsCache>(),
        )
        assertSame(
            koin.get<com.subtracks.data.recommendations.SimilarArtistsRepository>(),
            koin.get<com.subtracks.data.recommendations.SimilarArtistsRepository>(),
        )
        val model =
            koin.get<com.subtracks.ui.library.SimilarArtistsViewModel> {
                org.koin.core.parameter
                    .parametersOf("seed")
            }
        assertNotNull(model)
        cancelAndJoinBlocking(model.viewModelScope)
    }

    @Test
    fun theModuleResolvesThePlaybackGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverHeaders = ServerHeaders()
        val http = OkHttpClient.Builder().addNetworkInterceptor(serverHeaders.interceptor()).build()
        koin = startKoin { modules(appModule(context, http, serverHeaders)) }.koin

        val downloads = koin.get<DownloadRepository>()
        val controller = koin.get<PlaybackController>()

        assertSame(downloads, koin.get<DownloadRepository>())
        assertSame(controller, koin.get<PlaybackController>())
    }

    @Test
    fun theModuleResolvesTheLyricsGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverHeaders = ServerHeaders()
        val http = OkHttpClient.Builder().addNetworkInterceptor(serverHeaders.interceptor()).build()
        koin = startKoin { modules(appModule(context, http, serverHeaders)) }.koin

        val lyrics = koin.get<LyricsRepository>()

        assertSame(lyrics, koin.get<LyricsRepository>())
        assertSame(koin.get<PlaylistRepository>(), koin.get<PlaylistRepository>())
        val model = koin.get<LyricsViewModel>()
        assertNotNull(model)
        cancelAndJoinBlocking(model.viewModelScope)
    }

    @Test
    fun theModuleResolvesTheQueueBuilder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverHeaders = ServerHeaders()
        val http = OkHttpClient.Builder().addNetworkInterceptor(serverHeaders.interceptor()).build()
        koin = startKoin { modules(appModule(context, http, serverHeaders)) }.koin

        assertSame(koin.get<QueueBuilderPlayer>(), koin.get<QueueBuilderPlayer>())
        val model = koin.get<QueueBuilderViewModel>()
        assertNotNull(model)
        cancelAndJoinBlocking(model.viewModelScope)
    }

    @Test
    fun theModuleResolvesTheAudioMuseActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverHeaders = ServerHeaders()
        val http = OkHttpClient.Builder().addNetworkInterceptor(serverHeaders.interceptor()).build()
        koin = startKoin { modules(appModule(context, http, serverHeaders)) }.koin

        assertSame(koin.get<com.subtracks.playback.SongPathPlayer>(), koin.get<com.subtracks.playback.SongPathPlayer>())
        assertSame(koin.get<com.subtracks.playback.MoodRadioPlayer>(), koin.get<com.subtracks.playback.MoodRadioPlayer>())
    }

    @Test
    fun theModuleResolvesFreshReleasesWithOneSharedMusicBrainzLimiter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverHeaders = ServerHeaders()
        val http = OkHttpClient.Builder().addNetworkInterceptor(serverHeaders.interceptor()).build()
        koin = startKoin { modules(appModule(context, http, serverHeaders)) }.koin

        assertSame(koin.get<com.subtracks.data.musicbrainz.FreshReleases>(), koin.get<com.subtracks.data.musicbrainz.FreshReleases>())
        assertSame(
            koin.get<com.subtracks.data.musicbrainz.MusicBrainzRateLimiter>(),
            koin.get<com.subtracks.data.musicbrainz.MusicBrainzRateLimiter>(),
        )
    }
}
