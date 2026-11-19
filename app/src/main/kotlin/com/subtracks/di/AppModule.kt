package com.subtracks.di

import android.content.Context
import android.os.Environment
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.UiMessages
import com.subtracks.data.artistimages.ExternalArtistImages
import com.subtracks.data.artistimages.OkHttpWikidata
import com.subtracks.data.artistimages.Wikidata
import com.subtracks.data.audiomuse.AudioMuseConfiguration
import com.subtracks.data.audiomuse.AudioMuseConnectionTester
import com.subtracks.data.audiomuse.AudioMuseMoodCache
import com.subtracks.data.audiomuse.AudioMuseScoreAccess
import com.subtracks.data.audiomuse.AudioMuseScoreFetcher
import com.subtracks.data.audiomuse.AudioMuseTrackResolver
import com.subtracks.data.audiomuse.MoodRadioBuilder
import com.subtracks.data.audiomuse.SongPathBuilder
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
import com.subtracks.data.mix.InstantMix
import com.subtracks.data.mix.QueueBuilder
import com.subtracks.data.mix.QueueBuilderMoodBuilder
import com.subtracks.data.mix.SimilarSongs
import com.subtracks.data.mix.SmartShuffle
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.Song
import com.subtracks.data.musicbrainz.FreshReleases
import com.subtracks.data.musicbrainz.FreshReleasesRefresher
import com.subtracks.data.musicbrainz.MusicBrainz
import com.subtracks.data.musicbrainz.MusicBrainzRateLimiter
import com.subtracks.data.musicbrainz.OkHttpMusicBrainz
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
import com.subtracks.playback.InstantMixPlayer
import com.subtracks.playback.MediaSessionConnection
import com.subtracks.playback.MoodRadioPlayer
import com.subtracks.playback.PlaybackController
import com.subtracks.playback.PlayerConnection
import com.subtracks.playback.QueueBuilderPlayer
import com.subtracks.playback.Scrobbler
import com.subtracks.playback.SmartShufflePlayer
import com.subtracks.playback.SongPathPlayer
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
import com.subtracks.ui.settings.AudioMuseSettingsViewModel
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
        val sources = get<SourceRepository>()
        val queues = get<QueueRepository>()
        val dao = get<SubtracksDatabase>().mixDao()
        val mix = InstantMix(dao, SimilarSongs(sources::similarSongs), get())
        val preferences = get<UserPreferences>()
        PlaybackController(
            get(),
            get(),
            get(),
            get(),
            showMessage = showMessage,
            scope = get(named("playback")),
            networkAvailable = networkAvailable(context.applicationContext),
            autoplayPreference = preferences.autoplaySimilar(),
            autoplayFactory = { enabled ->
                com.subtracks.playback.AutoplayRefill
                    .of(queues, dao, mix, enabled, sources::activeSourceIdOnce)
            },
            // The server's current picks, limited to what the library still offers, as the picker shows them.
            // Moods too, so a mood queue refills by the same picks (cached scores only when offline).
            builtQueueRefill =
                QueueBuilderMoodBuilder
                    .Refill(
                        get(),
                        picks = { preferences.queueBuilderPicks(it).first() },
                        moodsAvailable = {
                            get<AudioMuseConfiguration>()
                                .observe(it)
                                .first()
                                .available
                        },
                    )::invoke,
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
        com.subtracks.data.recommendations.SimilarArtistsCache(
            get<SubtracksDatabase>().similarArtistsCacheDao(),
            get<SourceRepository>()::similarArtists,
        )
    }
    single {
        com.subtracks.data.recommendations.SimilarArtistsRepository(
            get(),
            get<SubtracksDatabase>().libraryDao(),
            get<SourceRepository>()::activeSourceIdOnce,
            get(named("mixDownloadedOnly")),
        )
    }
    single {
        val sources = get<SourceRepository>()
        com.subtracks.data.recommendations.TopSongsRepository(
            get<SubtracksDatabase>().topSongsDao(),
            sources::topSongs,
            sources::activeSourceIdOnce,
        )
    }
    viewModel { params ->
        val sources = get<SourceRepository>()
        val library = get<LibraryRepository>()
        val topSongs = get<com.subtracks.data.recommendations.TopSongsRepository>()
        val playback = get<PlaybackController>()
        val artistId = params.get<String>()
        com.subtracks.ui.library.TopSongsViewModel(
            artistId = artistId,
            artistName = { id ->
                combine(library.artist(id, artistId), library.artistName(id, artistId)) { artist, fromSongs ->
                    artist?.name?.takeIf { it.isNotBlank() } ?: fromSongs
                }
            },
            sources = sources.activeSourceId(),
            offline = combine(sources.offline, networkAvailable(context.applicationContext)) { off, connected -> off || !connected },
            songs = topSongs::songs,
            refresh = topSongs::refresh,
            play = playback::playSongs,
        )
    }
    viewModel { params ->
        val sources = get<SourceRepository>()
        val recommendations = get<com.subtracks.data.recommendations.SimilarArtistsRepository>()
        com.subtracks.ui.library.SimilarArtistsViewModel(
            params.get(),
            sources.activeSourceId(),
            combine(sources.offline, networkAvailable(context.applicationContext)) { off, connected -> off || !connected },
            recommendations::read,
            recommendations::refresh,
            get<UserPreferences>().externalArtistImagesEnabled(),
            get<ExternalArtistImages>()::imageUrl,
        )
    }
    single { SmartShuffle(get<SubtracksDatabase>().mixDao()) }
    single { QueueBuilder(get<SubtracksDatabase>().mixDao()) }
    // One limiter for every MusicBrainz feature: together they stay at MusicBrainz' one request per second.
    single { MusicBrainzRateLimiter() }
    single<MusicBrainz> { OkHttpMusicBrainz(get()) }
    single<Wikidata> { OkHttpWikidata() }
    single { ExternalArtistImages(get(), get()) }
    single { FreshReleases(get<SubtracksDatabase>().freshReleaseDao(), get()) }
    single { FreshReleasesRefresher(get(), get<CoroutineScope>(), get<SourceRepository>()::activeSourceIdOnce) }
    viewModel {
        val sources = get<SourceRepository>()
        com.subtracks.ui.discover.FreshReleasesViewModel(
            activeSourceId = sources.activeSourceId(),
            offline =
                combine(
                    sources.offline,
                    networkAvailable(context.applicationContext),
                ) { offline, connected -> offline || !connected },
            preferences = get(),
            feed = get(),
            refresher = get(),
        )
    }
    // Offline mode or no network: mixes may only use downloaded songs.
    single(named("mixDownloadedOnly")) {
        val sources = get<SourceRepository>()
        val connected =
            networkAvailable(context.applicationContext).stateIn(get<CoroutineScope>(), SharingStarted.Eagerly, true)
        val downloadedOnly: () -> Boolean = { sources.offline.value || !connected.value }
        downloadedOnly
    }
    single {
        SmartShufflePlayer.of(
            mix = get(),
            activeSourceId = get<SourceRepository>()::activeSourceIdOnce,
            downloadedOnly = get(named("mixDownloadedOnly")),
            controller = get(),
        )
    }
    single {
        val sources = get<SourceRepository>()
        InstantMixPlayer.of(
            mix = InstantMix(get<SubtracksDatabase>().mixDao(), SimilarSongs(sources::similarSongs), get()),
            activeSourceId = sources::activeSourceIdOnce,
            downloadedOnly = get(named("mixDownloadedOnly")),
            controller = get(),
        )
    }
    single {
        QueueBuilderPlayer.of(
            builder = get<QueueBuilderMoodBuilder>(),
            activeSourceId = get<SourceRepository>()::activeSourceIdOnce,
            downloadedOnly = get(named("mixDownloadedOnly")),
            controller = get(),
        )
    }
    viewModel {
        val sources = get<SourceRepository>()
        val player = get<QueueBuilderPlayer>()
        com.subtracks.ui.discover.QueueBuilderViewModel(
            activeSourceId = sources.activeSourceId(),
            offline =
                combine(
                    sources.offline,
                    networkAvailable(context.applicationContext),
                ) { offline, connected -> offline || !connected },
            builder = get(),
            preferences = get(),
            play = player::play,
            showMessage = showMessage,
            moodsAvailable = { id -> get<AudioMuseConfiguration>().observe(id).map { it.available } },
        )
    }
    viewModel { PinMenuViewModel(get()) }
    viewModel { RootViewModel(get()) }
    viewModel {
        val sources = get<SourceRepository>()
        com.subtracks.ui.discover.DiscoverViewModel(
            sources,
            get<SubtracksDatabase>().mixDao(),
            combine(sources.offline, networkAvailable(context.applicationContext)) { offline, connected -> offline || !connected },
            get(),
            get(),
            showMessage,
            get(),
            moodsAvailable = { id -> get<AudioMuseConfiguration>().observe(id).map { it.available } },
            playMood = get<MoodRadioPlayer>()::play,
            library = get<SubtracksDatabase>().libraryDao(),
            playback = get(),
            pathPlayer = get(),
        )
    }
    viewModel {
        val player = get<InstantMixPlayer>()
        com.subtracks.ui.components.MixMenuViewModel(
            play = { sourceId, seed -> player.play(seed, expectedSourceId = sourceId) },
            showMessage = showMessage,
        )
    }
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { LibraryHubViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> HomeListViewModel(get(), get(), get(), get(), params.get()) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ListenBrainzSettingsViewModel(get()) }
    single { AudioMuseConfiguration(get<SubtracksDatabase>().audioMuseConfigDao(), get()) }
    // Denied until the active source and effective connectivity are known.
    single(named("audioMuseAccess")) {
        val sources = get<SourceRepository>()
        combine(sources.activeSourceId(), sources.offline, networkAvailable(context.applicationContext)) { id, offline, connected ->
            AudioMuseScoreAccess(id, offline || !connected)
        }.stateIn(get<CoroutineScope>(), SharingStarted.Eagerly, AudioMuseScoreAccess())
    }
    single {
        AudioMuseTrackResolver(get<SubtracksDatabase>().audioMuseTrackDao(), get<StateFlow<AudioMuseScoreAccess>>(named("audioMuseAccess")))
    }
    single { MoodRadioBuilder(get(), get(), get<StateFlow<AudioMuseScoreAccess>>(named("audioMuseAccess"))) }
    single { AudioMuseMoodCache(get<SubtracksDatabase>().audioMuseScoreDao()) }
    single { AudioMuseScoreFetcher(get(), get(), get<StateFlow<AudioMuseScoreAccess>>(named("audioMuseAccess"))) }
    single { QueueBuilderMoodBuilder(get(), get<AudioMuseScoreFetcher>()::fetch) }
    single { MoodRadioPlayer.of(get(), get<SourceRepository>()::activeSourceIdOnce, get()) }
    single { SongPathBuilder(get(), get(), get<StateFlow<AudioMuseScoreAccess>>(named("audioMuseAccess"))) }
    single { SongPathPlayer.of(get(), get<SourceRepository>()::activeSourceIdOnce, get()) }
    viewModel { params ->
        val sources = get<SourceRepository>()
        val tester = AudioMuseConnectionTester()
        AudioMuseSettingsViewModel(
            params.get<Long>(),
            get(),
            combine(sources.offline, networkAvailable(context.applicationContext)) { offline, connected -> offline || !connected },
            tester::test,
        )
    }
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
