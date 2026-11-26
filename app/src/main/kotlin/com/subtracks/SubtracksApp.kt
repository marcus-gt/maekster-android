package com.subtracks

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.subtracks.data.LegacyDataCleanup
import com.subtracks.data.net.ServerHeaders
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.ArtworkSeedStore
import com.subtracks.di.appModule
import com.subtracks.log.Log
import com.subtracks.ui.theme.ArtworkSeedCache
import com.subtracks.widget.AlbumShelf
import com.subtracks.widget.AlbumShelfWidgetUpdater
import com.subtracks.widget.FavoriteSongsWidgetUpdater
import com.subtracks.widget.NowPlayingWidgetUpdater
import com.subtracks.widget.RadioStationsWidgetUpdater
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class SubtracksApp : Application() {
    // The active server's custom headers, added per network hop and only for that server's origin.
    private val serverHeaders = ServerHeaders()
    private var fontScale = 0f

    private val http =
        UserAgent
            .httpClient()
            .newBuilder()
            .addInterceptor(Log.interceptor())
            .addNetworkInterceptor(serverHeaders.interceptor())
            .build()

    @OptIn(DelicateCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        fontScale = resources.configuration.fontScale
        Log.init(this)
        LegacyDataCleanup.run(this)
        stopKoin()
        SingletonImageLoader.reset()
        val koin =
            startKoin {
                modules(appModule(this@SubtracksApp, http, serverHeaders))
            }.koin
        ArtworkSeedCache.install(koin.get<ArtworkSeedStore>(), koin.get<UserPreferences>())
        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlbumShelf.entries.forEach { koin.get<AlbumShelfWidgetUpdater>(it.updaterName).start() }
            koin.get<FavoriteSongsWidgetUpdater>().start()
            koin.get<RadioStationsWidgetUpdater>().start()
        }
    }

    /** The widgets' mood labels depend on the font scale, so a new scale redraws them. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.fontScale == fontScale) return
        fontScale = newConfig.fontScale
        GlobalContext.getOrNull()?.getOrNull<NowPlayingWidgetUpdater>()?.refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlobalContext.getOrNull()?.getOrNull<RadioStationsWidgetUpdater>()?.refresh()
        }
    }
}
