package com.subtracks.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.media.readAudioEncoding
import com.subtracks.data.model.AudioEncoding
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.DownloadStatus
import com.subtracks.data.model.DownloadedSong
import com.subtracks.data.repo.DownloadRepository
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.playback.PlaybackController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadTree(
    val artists: List<DownloadArtistNode> = emptyList(),
) {
    val songs: Int get() = artists.sumOf { it.albums.sumOf { album -> album.songs.size } }
    val bytes: Long get() = artists.sumOf { it.bytes }
}

data class DownloadArtistNode(
    val id: String,
    val name: String,
    val bytes: Long,
    val albums: List<DownloadAlbumNode>,
    val sortName: String = name,
)

data class DownloadAlbumNode(
    val id: String,
    val name: String,
    val bytes: Long,
    val songs: List<DownloadedSong>,
    val sortName: String = name,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackController: PlaybackController,
    private val unknownArtist: String,
    private val unknownAlbum: String,
) : ViewModel() {
    private val sourceId = libraryRepository.activeSourceId.filterNotNull()

    private val songs = sourceId.flatMapLatest { downloadRepository.downloadedSongs(it) }

    val tree: StateFlow<DownloadTree> =
        songs
            .map { buildTree(it, unknownArtist, unknownAlbum) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadTree())

    /** Songs not yet on the device, as iOS's Transfers section: running, then queued, then failed. */
    val transfers: StateFlow<List<DownloadedSong>> =
        songs
            .map(::transfersOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Starts a failed download again. */
    fun retry(song: DownloadedSong) {
        viewModelScope.launch { downloadRepository.download(song.sourceId, song.songId) }
    }

    /** Stops an active transfer, or clears a failed one. */
    fun cancelTransfer(song: DownloadedSong) {
        viewModelScope.launch {
            if (song.status == DownloadStatus.Failed) {
                downloadRepository.remove(song.sourceId, song.songId)
            } else {
                downloadRepository.cancel(song.sourceId, listOf(song.songId))
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            downloadRepository.removeSource(sourceId.first())
            playbackController.refreshMediaItems()
        }
    }

    fun delete(
        list: DownloadList,
        refId: String,
    ) {
        viewModelScope.launch {
            downloadRepository.removeList(sourceId.first(), list, refId)
            playbackController.refreshMediaItems()
        }
    }

    fun deleteSongs(songIds: List<String>) {
        viewModelScope.launch {
            val source = sourceId.first()
            songIds.forEach { downloadRepository.remove(source, it) }
            playbackController.refreshMediaItems()
        }
    }

    fun cancelSongs(songIds: List<String>) {
        viewModelScope.launch {
            downloadRepository.cancel(sourceId.first(), songIds)
        }
    }

    suspend fun encoding(song: DownloadedSong): AudioEncoding? =
        downloadRepository.localFile(song.sourceId, song.songId)?.let { readAudioEncoding(it) }
}

internal fun transfersOf(songs: List<DownloadedSong>): List<DownloadedSong> =
    songs
        .filter { it.status != DownloadStatus.Completed }
        .sortedBy {
            when (it.status) {
                DownloadStatus.Running -> 0
                DownloadStatus.Queued -> 1
                else -> 2
            }
        }

internal fun buildTree(
    songs: List<DownloadedSong>,
    unknownArtist: String,
    unknownAlbum: String,
): DownloadTree =
    DownloadTree(
        artists =
            songs
                .groupBy { it.artistId.orEmpty() to it.artistName.orEmpty() }
                .map { (artistKey, artistSongs) ->
                    DownloadArtistNode(
                        id = artistKey.first,
                        name = artistKey.second.ifBlank { unknownArtist },
                        bytes = artistSongs.sumOf { it.size },
                        sortName = artistSongs.first().artistSort.ifBlank { artistKey.second },
                        albums =
                            artistSongs
                                .groupBy { it.albumId.orEmpty() to it.albumName.orEmpty() }
                                .map { (albumKey, albumSongs) ->
                                    DownloadAlbumNode(
                                        id = albumKey.first,
                                        name = albumKey.second.ifBlank { unknownAlbum },
                                        bytes = albumSongs.sumOf { it.size },
                                        sortName = albumSongs.first().albumSort.ifBlank { albumKey.second },
                                        songs = albumSongs,
                                    )
                                }.sortedBy { it.sortName.lowercase() },
                    )
                }.sortedBy { it.sortName.lowercase() },
    )
