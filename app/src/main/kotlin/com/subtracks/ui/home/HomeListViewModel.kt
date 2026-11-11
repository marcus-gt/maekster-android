package com.subtracks.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumCollectionKind
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.AlbumSort
import com.subtracks.data.prefs.ArtistSort
import com.subtracks.data.prefs.PlaylistSort
import com.subtracks.data.prefs.StarredFilter
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.artistArt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeListRequest(
    val title: String,
    val section: HomeSection? = null,
    val genre: String? = null,
    val decade: Long? = null,
    val downloaded: OfflineListKind? = null,
    val favorites: FavoriteKind? = null,
    // A genre's or a label's albums, opened from the Library's Genres or Labels list.
    val collection: AlbumCollectionKind? = null,
    val collectionName: String? = null,
)

/** The three lists on the Favorites page. */
enum class FavoriteKind(
    @param:StringRes val title: Int,
) {
    Songs(R.string.search_songs),
    Albums(R.string.resources_album_name),
    Artists(R.string.resources_artist_name),
}

enum class OfflineListKind(
    @param:StringRes val title: Int,
) {
    Albums(R.string.home_downloaded_albums),
    Artists(R.string.home_downloaded_artists),
    Songs(R.string.home_downloaded_songs),
    Playlists(R.string.home_downloaded_playlists),
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeListViewModel(
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val playbackController: PlaybackController,
    val request: HomeListRequest,
) : ViewModel() {
    private val sourceId = libraryRepository.activeSourceId.filterNotNull()

    // Favorite songs are the same list as Home's recent favorites: newest first, played as the favorites queue.
    private val favoriteSongs =
        request.section == HomeSection.RecentlyStarredSongs || request.favorites == FavoriteKind.Songs

    // Offline, favorites show only what is downloaded, like every other list.
    private val sourceAndOffline = combine(sourceId, sourceRepository.offline) { id, offline -> id to offline }

    val canShuffle: Boolean =
        request.genre != null ||
            favoriteSongs ||
            request.downloaded == OfflineListKind.Songs

    val albums: Flow<PagingData<Album>> =
        sourceAndOffline
            .flatMapLatest { (id, offline) ->
                when {
                    request.favorites == FavoriteKind.Albums -> {
                        libraryRepository.albums(id, AlbumSort.Starred, false, StarredFilter.Starred, "", offline)
                    }

                    request.downloaded == OfflineListKind.Albums -> {
                        libraryRepository.downloadedAlbumsPage(id)
                    }

                    request.decade != null -> {
                        libraryRepository.albumsByDecade(id, request.decade)
                    }

                    request.collection != null && request.collectionName != null -> {
                        libraryRepository.collectionAlbums(id, request.collection, request.collectionName, offline)
                    }

                    request.section == HomeSection.RecentlyPlayedAlbums -> {
                        libraryRepository.homeRecentlyPlayedAlbums(id)
                    }

                    request.section == HomeSection.MostPlayedAlbums -> {
                        libraryRepository.homeMostPlayedAlbums(id)
                    }

                    request.section == HomeSection.RecentlyAddedAlbums -> {
                        libraryRepository.homeRecentlyAddedAlbums(id)
                    }

                    request.section == HomeSection.Rediscover -> {
                        libraryRepository.rediscoverAlbumsPage(id)
                    }

                    else -> {
                        flowOf(PagingData.empty<Album>())
                    }
                }
            }.cachedIn(viewModelScope)

    val artists: Flow<PagingData<Artist>> =
        sourceAndOffline
            .flatMapLatest { (id, offline) ->
                if (request.favorites == FavoriteKind.Artists) {
                    return@flatMapLatest libraryRepository.artists(id, ArtistSort.Starred, false, StarredFilter.Starred, "", offline)
                }
                when (request.section) {
                    HomeSection.RecentlyPlayedArtists -> {
                        libraryRepository.homeRecentlyPlayedArtists(id)
                    }

                    HomeSection.MostPlayedArtists -> {
                        libraryRepository.homeMostPlayedArtists(id)
                    }

                    else -> {
                        if (request.downloaded == OfflineListKind.Artists) {
                            libraryRepository.downloadedArtistsPage(id)
                        } else {
                            flowOf(PagingData.empty<Artist>())
                        }
                    }
                }
            }.cachedIn(viewModelScope)

    val playlists: Flow<PagingData<Playlist>> =
        sourceId
            .flatMapLatest { id ->
                if (request.downloaded == OfflineListKind.Playlists) {
                    libraryRepository.playlists(
                        sourceId = id,
                        sort = PlaylistSort.Name,
                        descending = false,
                        search = "",
                        downloaded = true,
                    )
                } else {
                    flowOf(PagingData.empty<Playlist>())
                }
            }.cachedIn(viewModelScope)

    val songs: Flow<PagingData<AlbumSongItem>> =
        sourceId
            .flatMapLatest { id ->
                when {
                    request.downloaded == OfflineListKind.Songs -> libraryRepository.downloadedSongsPage(id)
                    request.genre != null -> libraryRepository.songsByGenre(id, request.genre)
                    favoriteSongs -> libraryRepository.starredSongs(id)
                    else -> flowOf(PagingData.empty<AlbumSongItem>())
                }
            }.cachedIn(viewModelScope)

    val genres: StateFlow<List<String>> =
        sourceId
            .flatMapLatest { libraryRepository.genresByMostPlayed(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val decades: StateFlow<List<Long>> =
        sourceId
            .flatMapLatest { libraryRepository.decades(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun coverArt(
        coverArt: String?,
        thumbnail: Boolean,
    ): CoverArtRef? = sourceRepository.coverArt(coverArt, thumbnail)

    fun artistArt(
        artist: Artist,
        forIcon: Boolean = true,
    ): ArtistArt = sourceRepository.artistArt(artist, forIcon)

    fun play(song: Song) {
        val genre = request.genre
        when {
            genre != null -> {
                viewModelScope.playGenreList(libraryRepository, playbackController, genre, song)
            }

            request.downloaded == OfflineListKind.Songs -> {
                viewModelScope.playDownloadedList(libraryRepository, playbackController, song)
            }

            favoriteSongs -> {
                viewModelScope.playStarredList(
                    libraryRepository,
                    playbackController,
                    song,
                )
            }

            else -> {
                viewModelScope.playSongInContext(libraryRepository, playbackController, song)
            }
        }
    }

    fun shuffle() {
        val genre = request.genre
        val downloaded = request.downloaded
        val starred = favoriteSongs
        viewModelScope.launch {
            val id = sourceId.first()
            when {
                genre != null -> playbackController.shuffleGenre(id, genre)
                downloaded == OfflineListKind.Songs -> playbackController.shuffleDownloaded(id)
                starred -> playbackController.shuffleStarred(id)
            }
        }
    }
}
