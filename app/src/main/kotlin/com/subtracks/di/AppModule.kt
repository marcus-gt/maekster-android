package com.subtracks.di

import android.content.Context
import android.os.Environment
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.UiMessages
import com.subtracks.data.db.SubtracksDatabase
import com.subtracks.data.db.createAndroidDatabase
import com.subtracks.data.download.ArtworkFetcher
import com.subtracks.data.download.ArtworkStore
import com.subtracks.data.download.DownloadEngine
import com.subtracks.data.download.DownloadNotifier
import com.subtracks.data.download.OkHttpArtworkFetcher
import com.subtracks.data.download.SystemDownloadEngine
import com.subtracks.data.listenbrainz.ListenBrainzRepository
import com.subtracks.data.listenbrainz.OkHttpListenBrainzApi
import com.subtracks.data.listenbrainz.toListenTrack
import com.subtracks.data.lyrics.LrclibProvider
import com.subtracks.data.lyrics.LyricsCache
import com.subtracks.data.lyrics.LyricsLoader
import com.subtracks.data.lyrics.LyricsProvider
import com.subtracks.data.lyrics.LyricsRepository
import com.subtracks.data.mix.SmartShuffle
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.Song
import com.subtracks.data.net.ServerHeaders
import com.subtracks.data.net.networkAvailable
import com.subtracks.data.net.networkMode
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.prefs.createUserPreferences
import com.subtracks.data.repo.ArtworkSeedRepository
import com.subtracks.data.repo.ArtworkSeedStore
import com.subtracks.data.repo.DownloadRepository
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.NetworkServerActionSink
import com.subtracks.data.repo.PendingServerActionSink
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.QueueRepository
import com.subtracks.data.repo.QuickPlaylistRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.data.security.CredentialStore
import com.subtracks.data.security.KeystoreCredentialStore
import com.subtracks.data.source.ServerActionSink
import com.subtracks.data.source.StarType
import com.subtracks.data.sync.AutoSync
import com.subtracks.data.sync.SyncManager
import com.subtracks.data.sync.SyncNotifier
import com.subtracks.log.Log
import com.subtracks.playback.MediaSessionConnection
import com.subtracks.playback.PlaybackController
import com.subtracks.playback.PlayerConnection
import com.subtracks.playback.Scrobbler
import com.subtracks.playback.SmartShufflePlayer
import com.subtracks.ui.NowPlayingLauncher
import com.subtracks.ui.RootViewModel
import com.subtracks.ui.components.PinMenuViewModel
import com.subtracks.ui.downloads.DownloadsViewModel
import com.subtracks.ui.home.HomeListViewModel
import com.subtracks.ui.home.HomeViewModel
import com.subtracks.ui.library.AlbumDetailViewModel
import com.subtracks.ui.library.ArtistDetailViewModel
import com.subtracks.ui.library.LibraryHubViewModel
import com.subtracks.ui.library.LibraryViewModel
import com.subtracks.ui.library.PlaylistDetailViewModel
import com.subtracks.ui.playback.LyricsViewModel
import com.subtracks.ui.playback.QueueViewModel
import com.subtracks.ui.playback.playingSong
import com.subtracks.ui.playlist.AddToPlaylistRequest
import com.subtracks.ui.playlist.AddToPlaylistViewModel
import com.subtracks.ui.playlist.DeletePlaylistRequest
import com.subtracks.ui.playlist.DeletePlaylistViewModel
import com.subtracks.ui.playlist.QuickPlaylistMenuViewModel
import com.subtracks.ui.playlist.QuickPlaylistPickerViewModel
import com.subtracks.ui.playlist.RenamePlaylistRequest
import com.subtracks.ui.playlist.RenamePlaylistViewModel
import com.subtracks.ui.playlist.SaveQueueViewModel
import com.subtracks.ui.search.SearchViewModel
import com.subtracks.ui.settings.AddSourceViewModel
import com.subtracks.ui.settings.CoilArtworkCache
import com.subtracks.ui.settings.ListenBrainzSettingsViewModel
import com.subtracks.ui.settings.QuickPlaylistsSettingsViewModel
import com.subtracks.ui.settings.SettingsViewModel
import com.subtracks.ui.settings.StorageViewModel
import com.subtracks.ui.settings.activeLibraryCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

fun appModule(
    context: Context,
    http: OkHttpClient,
    // The same holder [http] reads in its network interceptor, so SourceRepository's updates reach every request.
    serverHeaders: ServerHeaders,
) = module {
    // Repositories report to the user through this queue; the app shell shows each message as a snackbar.
    val uiMessages = UiMessages()
    val showMessage: (UiMessage) -> Unit = uiMessages::show
    single { uiMessages }
    single { createAndroidDatabase(context) }
    single { http }
    single { serverHeaders }
    single { createUserPreferences(context) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single(named("io")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single(named("playback")) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single(createdAtStart = true) { get<UserPreferences>().verboseLogging().onEach(Log::setVerbose).launchIn(get()) }
    single<CredentialStore> { KeystoreCredentialStore() }
    single {
        SourceRepository(
            get(),
            get(),
            get(),
            get(),
            credentials = get(),
            serverHeaders = get(),
            networkMode = networkMode(context.applicationContext),
            showMessage = showMessage,
            scope = get(),
        )
    }
    single<ServerActionSink> {
        PendingServerActionSink(
            delegate = NetworkServerActionSink(get()),
            db = get(),
            activeSourceId = get<SourceRepository>()::activeSourceIdOnce,
            offline = get<SourceRepository>().offline,
            offlinePreferences = get<UserPreferences>().offlineMode(),
            networkAvailable = networkAvailable(context.applicationContext),
            scope = get(),
        )
    }
    single { LibraryRepository(get(), get(), get(), get(), showMessage, scope = get(named("io"))) }
    single<ArtworkSeedStore> { ArtworkSeedRepository(get()) }
    single { SyncManager(get(), get(), get(), get(), preferences = get(), showMessage = showMessage, scope = get()) }
    single {
        AutoSync(
            preferences = get(),
            networkMode = networkMode(context.applicationContext),
            hasSource = { get<SourceRepository>().activeSourceIdOnce() != null },
            requestSync = { get<SyncManager>().requestSync(silent = true) },
            scope = get(),
        )
    }
    single { QueueRepository(get()) }
    single { ArtworkStore(downloadsRoot(context)) }
    single<ArtworkFetcher> { OkHttpArtworkFetcher(get()) }
    single<DownloadEngine> { SystemDownloadEngine(context.applicationContext) }
    single {
        DownloadRepository(
            db = get(),
            sourceRepository = get(),
            engine = get(),
            downloadsDir = downloadsRoot(context),
            artworkStore = get(),
            artworkFetcher = get(),
            showMessage = showMessage,
            dispatcher = Dispatchers.IO,
            scope = get(named("io")),
        ).also { it.start() }
    }
    single(createdAtStart = true) { DownloadNotifier(context.applicationContext, get(), scope = get()).also { it.start() } }
    single(createdAtStart = true) { SyncNotifier(context.applicationContext, get<SyncManager>().status, scope = get()).also { it.start() } }
    single<PlayerConnection> { MediaSessionConnection(context.applicationContext, get(), get()) }
    single {
        PlaybackController(
            get(),
            get(),
            get(),
            get(),
            showMessage = showMessage,
            scope = get(named("playback")),
            networkAvailable = networkAvailable(context.applicationContext),
        )
    }
    single {
        val sources = get<SourceRepository>()
        val library = get<LibraryRepository>()
        ListenBrainzRepository(
            api = OkHttpListenBrainzApi(),
            prefs = get(),
            credentials = get(),
            pending = get<SubtracksDatabase>().pendingListenDao(),
            track = { songId ->
                val sourceId = sources.activeSourceIdOnce()
                sourceId?.let { library.song(it, songId).first() }?.toListenTrack()
            },
        ).also { repository ->
            // Listens queued while offline or before a restart go out once the app is running again.
            get<CoroutineScope>().launch { repository.flush() }
        }
    }
    single(createdAtStart = true) {
        val playback = get<PlaybackController>()
        Scrobbler(
            sink = get(),
            enabled = get<UserPreferences>().scrobbling(),
            scope = get(),
            recordPlay = get<LibraryRepository>()::recordPlay,
            listens = get<ListenBrainzRepository>(),
        ).also {
            it.attach(playback.state, playback.positionMs)
        }
    }
    single { NowPlayingLauncher() }
    single {
        val sources = get<SourceRepository>()
        val connected =
            networkAvailable(context.applicationContext).stateIn(get<CoroutineScope>(), SharingStarted.Eagerly, true)
        SmartShufflePlayer.of(
            mix = SmartShuffle(get<SubtracksDatabase>().mixDao()),
            activeSourceId = sources::activeSourceIdOnce,
            // Offline mode or no network: only downloaded songs can play.
            downloadedOnly = { sources.offline.value || !connected.value },
            controller = get(),
        )
    }
    viewModel { PinMenuViewModel(get()) }
    viewModel { RootViewModel(get()) }
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { LibraryHubViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> HomeListViewModel(get(), get(), get(), params.get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ListenBrainzSettingsViewModel(get()) }
    viewModel {
        val sync = get<SyncManager>()
        val database = context.getDatabasePath("subtracks.db")
        StorageViewModel(
            counts = activeLibraryCounts(get()),
            syncStatus = sync.status,
            requestSync = { sync.requestSync() },
            artworkCache = CoilArtworkCache(context.applicationContext),
            databaseFiles = { listOf(database, File("${database.path}-wal")) },
        )
    }
    viewModel {
        DownloadsViewModel(
            get(),
            get(),
            get(),
            context.getString(R.string.unknown_artist),
            context.getString(R.string.unknown_album),
        )
    }
    viewModel { params -> AddSourceViewModel(get(), get(), get(), params.getOrNull(), context.resources) }
    viewModel { params -> AlbumDetailViewModel(get(), get(), get(), get(), params.get()) }
    viewModel { params -> ArtistDetailViewModel(get(), get(), get(), get(), params.get()) }
    viewModel { params ->
        val sources = get<SourceRepository>()
        PlaylistDetailViewModel(get(), sources, get(), get(), params.get(), get(), owner = sources::username, showMessage = showMessage)
    }
    viewModel { QueueViewModel(get(), get(), get(), get()) }
    single {
        val sources = get<SourceRepository>()
        PlaylistRepository(get(), writerFor = sources::playlistWriter, libraryChanged = get<QueueRepository>()::invalidateLibraryCache)
    }
    single {
        val library = get<LibraryRepository>()
        QuickPlaylistRepository(
            get(),
            get(),
            alsoFavorite = get<UserPreferences>()::quickPlaylistsAlsoFavorite,
            favorite = { songId: String -> library.setStar(StarType.Song, songId, true) },
        )
    }
    viewModel { params ->
        val request = params.get<AddToPlaylistRequest>()
        val sources = get<SourceRepository>()
        AddToPlaylistViewModel(request, get(), owner = { sources.username(request.sourceId) }, showMessage = showMessage)
    }
    viewModel { params -> RenamePlaylistViewModel(params.get<RenamePlaylistRequest>(), get(), showMessage) }
    viewModel { SaveQueueViewModel(get(), get(), showMessage, cleanup = get<CoroutineScope>(named("io"))) }
    viewModel { params -> QuickPlaylistMenuViewModel(params.get<Song>(), get(), showMessage, work = get<CoroutineScope>(named("io"))) }
    viewModel { params ->
        val sourceId = params.get<Long>()
        val sources = get<SourceRepository>()
        QuickPlaylistPickerViewModel(sourceId, get(), get(), owner = { sources.username(sourceId) })
    }
    viewModel { params -> QuickPlaylistsSettingsViewModel(params.get<Long>(), get(), get()) }
    viewModel { params ->
        val downloads = get<DownloadRepository>()
        val playback = get<PlaybackController>()
        DeletePlaylistViewModel(
            params.get<DeletePlaylistRequest>(),
            get(),
            purgeDownloads = { sourceId, playlistId ->
                downloads.deleteAll(sourceId, DownloadList.Playlist, playlistId)
                playback.refreshMediaItems()
            },
            showMessage = showMessage,
        )
    }
    single { LyricsCache(get<SubtracksDatabase>().lyricsCacheDao()) }
    single { LrclibProvider() }
    single {
        val sources = get<SourceRepository>()
        LyricsRepository(get(), server = LyricsProvider { sources.serverLyrics(it) }, lrclib = get<LrclibProvider>())
    }
    viewModel {
        val library = get<LibraryRepository>()
        val connected = networkAvailable(context.applicationContext).distinctUntilChanged()
        val offline = combine(get<SourceRepository>().offline, connected) { mode, online -> mode || !online }
        LyricsViewModel(
            song = playingSong(get<PlaybackController>().state, library::song),
            preferences = get<UserPreferences>().lyrics(),
            offline = offline,
            loader = LyricsLoader.from(get()),
        )
    }
}

private fun downloadsRoot(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "downloads")
