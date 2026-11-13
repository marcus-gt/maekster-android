package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.model.BulkDownloadAction
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.DownloadList
import com.subtracks.data.model.ListDownloadStatus
import com.subtracks.data.model.ListTotals
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistSongItem
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongDownload
import com.subtracks.data.repo.DownloadRepository
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackController
import com.subtracks.ui.playlist.PlaylistEntryMover
import com.subtracks.ui.playlist.playlistEditMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A playlist can be edited (songs removed, renamed) outside offline mode when it is the user's own, or when the
 * server did not say whose it is or who the user is (the add sheet's rule).
 */
internal fun canEditPlaylist(
    playlist: Playlist?,
    offline: Boolean,
    user: String?,
): Boolean = !offline && playlist != null && (playlist.owner == null || user == null || playlist.owner == user)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(
    private val libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: DownloadRepository,
    private val playlistId: String,
    private val playlists: PlaylistRepository,
    private val owner: suspend (Long) -> String?,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private val sourceId = libraryRepository.activeSourceId.filterNotNull()

    private val ownerName: Flow<String?> = sourceId.mapLatest { owner(it) }

    val playlist: StateFlow<Playlist?> =
        sourceId
            .flatMapLatest { libraryRepository.playlist(it, playlistId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: Flow<PagingData<PlaylistSongItem>> =
        combine(sourceId, sourceRepository.offline) { id, offline -> id to offline }
            .flatMapLatest { (id, offline) -> libraryRepository.playlistSongs(id, playlistId, offline) }
            .cachedIn(viewModelScope)

    /** Null until counted, so the footer doesn't flash "0 songs". */
    val totals: StateFlow<ListTotals?> =
        combine(sourceId, sourceRepository.offline) { id, offline -> id to offline }
            .flatMapLatest { (id, offline) -> libraryRepository.playlistTotals(id, playlistId, offline) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val downloads: StateFlow<Map<String, SongDownload>> =
        downloadRepository.states()

    val offline: StateFlow<Boolean> = sourceRepository.offline

    val canEdit: StateFlow<Boolean> =
        combine(playlist, offline, ownerName, ::canEditPlaylist)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var moveSourceId: Long? = null

    private val mover =
        PlaylistEntryMover(
            viewModelScope,
            move = { position, songId, destination ->
                playlists.moveEntry(requireNotNull(moveSourceId), playlistId, position.toInt(), songId, destination)
            },
            showMessage = showMessage,
            retryMove = { playlists.retryReorder(requireNotNull(moveSourceId), playlistId) },
        )
    val moving = mover.busy
    val moveError = mover.error

    val canRetryMove: Boolean get() = canEdit.value && playlist.value?.sourceId == moveSourceId

    fun retryMove() {
        if (canRetryMove) mover.retry()
    }

    fun dismissMoveError() = mover.dismissFailure()

    fun moveFromPlaylist(
        target: com.subtracks.ui.components.MenuTarget.Song,
        offset: Int,
    ) {
        if (!canEdit.value || target.song.sourceId != playlist.value?.sourceId || offset !in listOf(-1, 1)) return
        val position = target.playlistPosition ?: return
        val destination = position + offset
        val count = playlist.value?.songCount ?: return
        if (destination !in 0 until count || destination > Int.MAX_VALUE || position > Int.MAX_VALUE) return
        if (moving.value || moveError.value != null) return
        moveSourceId = target.song.sourceId
        mover.move(position, target.song.id, destination.toInt())
    }

    /** Removes the entry at [position], which the user saw holding [songId]. */
    fun removeFromPlaylist(
        position: Long,
        songId: String,
    ) {
        viewModelScope.launch {
            try {
                playlists.removeEntry(sourceId.first(), playlistId, position.toInt(), songId)
                showMessage(UiMessage(R.string.playlist_song_removed, listOf(playlist.value?.name.orEmpty())))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                showMessage(playlistEditMessage(failure))
            }
        }
    }

    val downloadStatus: StateFlow<ListDownloadStatus> =
        sourceId
            .flatMapLatest { downloadRepository.status(it, DownloadList.Playlist, playlistId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDownloadStatus())

    suspend fun downloadedBytes(): Long = downloadRepository.downloadedBytes(sourceId.first(), DownloadList.Playlist, playlistId)

    fun onDownloadAction(action: BulkDownloadAction) {
        viewModelScope.launch {
            downloadRepository.applyAction(sourceId.first(), DownloadList.Playlist, playlistId, action)
            if (action == BulkDownloadAction.Delete) playbackController.refreshMediaItems()
        }
    }

    fun download(song: Song) {
        viewModelScope.launch { downloadRepository.download(song.sourceId, song.id) }
    }

    fun cancelDownload(song: Song) {
        viewModelScope.launch { downloadRepository.remove(song.sourceId, song.id) }
    }

    fun deleteDownload(song: Song) {
        viewModelScope.launch {
            downloadRepository.remove(song.sourceId, song.id)
            playbackController.refreshMediaItems()
        }
    }

    fun coverArt(
        coverArt: String?,
        thumbnail: Boolean,
    ): CoverArtRef? = sourceRepository.coverArt(coverArt, thumbnail)

    fun play(position: Long) {
        viewModelScope.launch {
            playbackController.playPlaylist(sourceId.first(), playlistId, position)
        }
    }

    fun playAll() {
        viewModelScope.launch {
            playbackController.playPlaylistInOrder(sourceId.first(), playlistId)
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            playbackController.shufflePlaylist(sourceId.first(), playlistId)
        }
    }
}
