package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.data.db.SongListSpec
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.AlbumCollectionSummary
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.BulkDownloadAction
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.SongDownload
import com.subtracks.data.prefs.AlbumSort
import com.subtracks.data.prefs.ArtistSort
import com.subtracks.data.prefs.CollectionSort
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.data.prefs.ListQuery
import com.subtracks.data.prefs.PlaylistSort
import com.subtracks.data.prefs.SongSort
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.DownloadRepository
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.data.sync.SyncManager
import com.subtracks.data.sync.SyncStatus
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.artistArt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val syncManager: SyncManager,
    private val playbackController: PlaybackController,
    private val userPreferences: UserPreferences,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    private val listQueries: Map<LibraryListTab, StateFlow<ListQuery>> =
        LibraryListTab.entries.associateWith { tab ->
            userPreferences
                .listQuery(tab)
                .stateIn(viewModelScope, SharingStarted.Eagerly, ListQuery(tab.defaultSort))
        }

    private val searches: Map<LibraryListTab, MutableStateFlow<String>> =
        LibraryListTab.entries.associateWith { MutableStateFlow("") }

    val albums: Flow<PagingData<Album>> =
        libraryRepository.activeSourceId
            .filterNotNull()
            .flatMapLatest { sourceId ->
                combine(
                    listQueries.getValue(LibraryListTab.Albums),
                    searches.getValue(LibraryListTab.Albums),
                    sourceRepository.offline,
                ) { query, search, offline -> Triple(query, search, offline) }
                    .flatMapLatest { (query, search, offline) ->
                        libraryRepository.albums(
                            sourceId,
                            query.albumSort(),
                            query.descending,
                            query.starred,
                            search,
                            query.downloaded || offline,
                        )
                    }
            }.cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> =
        libraryRepository.activeSourceId
            .filterNotNull()
            .flatMapLatest { sourceId ->
                combine(
                    listQueries.getValue(LibraryListTab.Artists),
                    searches.getValue(LibraryListTab.Artists),
                    sourceRepository.offline,
                ) { query, search, offline -> Triple(query, search, offline) }
                    .flatMapLatest { (query, search, offline) ->
                        libraryRepository.artists(
                            sourceId,
                            query.artistSort(),
                            query.descending,
                            query.starred,
                            search,
                            query.downloaded || offline,
                        )
                    }
            }.cachedIn(viewModelScope)

    val playlists: Flow<PagingData<Playlist>> =
        libraryRepository.activeSourceId
            .filterNotNull()
            .flatMapLatest { sourceId ->
                combine(
                    listQueries.getValue(LibraryListTab.Playlists),
                    searches.getValue(LibraryListTab.Playlists),
                    sourceRepository.offline,
                ) { query, search, offline -> Triple(query, search, offline) }
                    .flatMapLatest { (query, search, offline) ->
                        libraryRepository.playlists(sourceId, query.playlistSort(), query.descending, search, query.downloaded || offline)
                    }
            }.cachedIn(viewModelScope)

    val genres: Flow<PagingData<AlbumCollectionSummary>> = collections(LibraryListTab.Genres, AlbumCollectionKind.Genre)

    val labels: Flow<PagingData<AlbumCollectionSummary>> = collections(LibraryListTab.Labels, AlbumCollectionKind.Label)

    private fun collections(
        tab: LibraryListTab,
        kind: AlbumCollectionKind,
    ): Flow<PagingData<AlbumCollectionSummary>> =
        libraryRepository.activeSourceId
            .filterNotNull()
            .flatMapLatest { sourceId ->
                combine(listQueries.getValue(tab), searches.getValue(tab), sourceRepository.offline) { query, search, offline ->
                    Triple(query, search, offline)
                }.flatMapLatest { (query, search, offline) ->
                    libraryRepository.albumCollections(
                        sourceId = sourceId,
                        kind = kind,
                        sort = query.collectionSort(),
                        descending = query.descending,
                        search = search,
                        downloaded = query.downloaded || offline,
                    )
                }
            }.cachedIn(viewModelScope)

    /** What the Songs list shows; playing from it queues the same spec, so the queue follows the list. */
    val songListSpec: StateFlow<SongListSpec> =
        combine(
            listQueries.getValue(LibraryListTab.Songs),
            searches.getValue(LibraryListTab.Songs),
            sourceRepository.offline,
            userPreferences.sortNameMode(),
        ) { query, search, offline, mode ->
            SongListSpec(
                sort = query.songSort(),
                descending = query.descending,
                starred = query.starred,
                downloaded = query.downloaded || offline,
                search = search,
                sortNameMode = mode,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SongListSpec())

    val songs: Flow<PagingData<AlbumSongItem>> =
        libraryRepository.activeSourceId
            .filterNotNull()
            .flatMapLatest { sourceId -> songListSpec.flatMapLatest { spec -> libraryRepository.songs(sourceId, spec) } }
            .cachedIn(viewModelScope)

    val songDownloads: StateFlow<Map<String, SongDownload>> = downloadRepository.states()

    /** Plays the Songs list as shown, starting at the tapped row. */
    fun playSongs(index: Int) {
        val spec = songListSpec.value
        viewModelScope.launch { playbackController.playLibrarySongs(activeSourceId.first(), spec, index.toLong()) }
    }

    fun shuffleSongs() {
        val spec = songListSpec.value
        viewModelScope.launch { playbackController.shuffleLibrarySongs(activeSourceId.first(), spec) }
    }

    val offline: StateFlow<Boolean> = sourceRepository.offline

    fun setOffline(enabled: Boolean) = sourceRepository.setOfflineMode(enabled)

    val refreshing: StateFlow<Boolean> =
        syncManager.status
            .map { it is SyncStatus.Running && !it.silent }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val activeSourceId = libraryRepository.activeSourceId.filterNotNull()

    val playSortAvailability: StateFlow<PlaySortAvailability> =
        activeSourceId
            .flatMapLatest { sourceId ->
                combine(
                    libraryRepository.hasAlbumPlayCount(sourceId),
                    libraryRepository.hasAlbumPlayed(sourceId),
                ) { frequent, recent -> PlaySortAvailability(frequent, recent) }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PlaySortAvailability(frequent = false, recent = false),
            )

    private val albumDownloads: StateFlow<Map<String, ListDownloadStatus>> =
        activeSourceId
            .flatMapLatest { downloadRepository.statuses(it, DownloadList.Album) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val artistDownloads: StateFlow<Map<String, ListDownloadStatus>> =
        activeSourceId
            .flatMapLatest { downloadRepository.statuses(it, DownloadList.Artist) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val playlistDownloads: StateFlow<Map<String, ListDownloadStatus>> =
        activeSourceId
            .flatMapLatest { downloadRepository.statuses(it, DownloadList.Playlist) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun downloads(tab: LibraryListTab): StateFlow<Map<String, ListDownloadStatus>> =
        when (tab) {
            LibraryListTab.Albums -> albumDownloads
            LibraryListTab.Artists -> artistDownloads
            LibraryListTab.Playlists -> playlistDownloads
            LibraryListTab.Songs, LibraryListTab.Genres, LibraryListTab.Labels -> noListDownloads
        }

    // Song rows show each song's own download state (songDownloads) rather than a list status.
    private val noListDownloads: StateFlow<Map<String, ListDownloadStatus>> = MutableStateFlow(emptyMap())

    suspend fun downloadedBytes(
        list: DownloadList,
        refId: String,
    ): Long = downloadRepository.downloadedBytes(activeSourceId.first(), list, refId)

    fun onDownloadAction(
        list: DownloadList,
        refId: String,
        action: BulkDownloadAction,
    ) {
        viewModelScope.launch {
            downloadRepository.applyAction(activeSourceId.first(), list, refId, action)
            if (action == BulkDownloadAction.Delete) playbackController.refreshMediaItems()
        }
    }

    fun listQuery(tab: LibraryListTab): StateFlow<ListQuery> = listQueries.getValue(tab)

    fun setListQuery(
        tab: LibraryListTab,
        query: ListQuery,
    ) {
        viewModelScope.launch { userPreferences.setListQuery(tab, query) }
    }

    fun search(tab: LibraryListTab): StateFlow<String> = searches.getValue(tab)

    fun setSearch(
        tab: LibraryListTab,
        value: String,
    ) {
        searches.getValue(tab).value = value
    }

    fun coverArt(
        coverArt: String?,
        thumbnail: Boolean,
    ): CoverArtRef? = sourceRepository.coverArt(coverArt, thumbnail)

    fun artistArt(
        artist: Artist,
        forIcon: Boolean = true,
    ): ArtistArt = sourceRepository.artistArt(artist, forIcon)

    fun sync() {
        if (syncManager.status.value !is SyncStatus.Running) syncManager.requestSync()
    }
}

internal fun ListQuery.albumSort(): AlbumSort = AlbumSort.entries.firstOrNull { it.name == sort } ?: AlbumSort.Name

internal fun ListQuery.artistSort(): ArtistSort = ArtistSort.entries.firstOrNull { it.name == sort } ?: ArtistSort.Name

internal fun ListQuery.playlistSort(): PlaylistSort = PlaylistSort.entries.firstOrNull { it.name == sort } ?: PlaylistSort.Name

internal fun ListQuery.songSort(): SongSort = SongSort.entries.firstOrNull { it.name == sort } ?: SongSort.Name

internal fun ListQuery.collectionSort(): CollectionSort = CollectionSort.entries.firstOrNull { it.name == sort } ?: CollectionSort.Name

data class PlaySortAvailability(
    val frequent: Boolean,
    val recent: Boolean,
)
