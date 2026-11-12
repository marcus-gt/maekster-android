package com.subtracks.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.db.SEARCH_MIN_LENGTH
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.PinRef
import com.subtracks.data.model.PinnedItem
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.MenuTarget
import com.subtracks.ui.components.artistArt
import com.subtracks.ui.components.resolveMenuTarget
import com.subtracks.ui.home.playSongInContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResults(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val songs: List<AlbumSongItem> = emptyList(),
    val loading: Boolean = false,
) {
    val isEmpty: Boolean
        get() = albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() && songs.isEmpty()
}

internal fun searchReady(query: String): Boolean = query.codePointCount(0, query.length) >= SEARCH_MIN_LENGTH

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<SearchResults> =
        libraryRepository.activeSourceId
            .flatMapLatest { sourceId ->
                _query.flatMapLatest { text ->
                    if (sourceId == null || !searchReady(text)) {
                        flowOf(SearchResults())
                    } else {
                        combine(
                            libraryRepository.searchAlbums(sourceId, text),
                            libraryRepository.searchArtists(sourceId, text),
                            libraryRepository.searchPlaylists(sourceId, text),
                            libraryRepository.searchSongs(sourceId, text),
                        ) { albums, artists, playlists, songs ->
                            SearchResults(albums, artists, playlists, songs)
                        }.onStart { emit(SearchResults(loading = true)) }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Albums, playlists and artists opened from Search on the active server, newest first. */
    val history: StateFlow<List<PinnedItem>> =
        libraryRepository.activeSourceId
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else libraryRepository.searchHistory(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordVisit(ref: PinRef) {
        viewModelScope.launch { libraryRepository.recordSearchVisit(ref) }
    }

    /** Opens a history item (moving it to the top) if it is still mirrored and its server is still active. */
    fun openRecent(
        item: PinnedItem,
        open: (MenuTarget) -> Unit,
    ) {
        viewModelScope.launch {
            val target = resolveMenuTarget(item, libraryRepository, sourceRepository) ?: return@launch
            if (libraryRepository.activeSourceId.first() != item.sourceId) return@launch
            libraryRepository.recordSearchVisit(PinRef(item.sourceId, item.kind, item.refId))
            open(target)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { libraryRepository.activeSourceId.first()?.let { libraryRepository.clearSearchHistory(it) } }
    }

    fun coverArt(
        coverArt: String?,
        thumbnail: Boolean,
    ): CoverArtRef? = sourceRepository.coverArt(coverArt, thumbnail)

    fun artistArt(
        artist: Artist,
        forIcon: Boolean = true,
    ): ArtistArt = sourceRepository.artistArt(artist, forIcon)

    fun play(song: Song) {
        viewModelScope.playSongInContext(libraryRepository, playbackController, song)
    }
}
