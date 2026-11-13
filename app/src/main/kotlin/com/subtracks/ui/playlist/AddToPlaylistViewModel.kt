package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.model.Playlist
import com.subtracks.data.net.connectionErrorMessage
import com.subtracks.data.repo.AddOutcome
import com.subtracks.data.repo.Duplicates
import com.subtracks.data.repo.PlaylistAddition
import com.subtracks.data.repo.PlaylistCreation
import com.subtracks.data.repo.PlaylistEditException
import com.subtracks.data.repo.PlaylistRepository
import com.subtracks.data.source.subsonic.SubsonicException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The songs to add and the server they belong to. */
data class AddToPlaylistRequest(
    val sourceId: Long,
    val songIds: List<String>,
)

data class AddToPlaylistUi(
    val query: String = "",
    val recent: List<Playlist> = emptyList(),
    val newName: String = "",
    /** The playlist being written to, or [CREATING] while a new one is made. */
    val busy: String? = null,
    val error: UiMessage? = null,
    /** A playlist that already holds some of the songs, waiting for Add anyway or Skip. */
    val duplicatesIn: Playlist? = null,
) {
    val canCreate: Boolean get() = newName.isNotBlank() && busy == null

    companion object {
        const val CREATING = "\u0000new"
    }
}

/**
 * Adds a selection of songs to one of the user's playlists or to a new one. A failed attempt keeps the sheet,
 * the draft name and its addition, so trying again only sends what is still missing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistViewModel(
    private val request: AddToPlaylistRequest,
    private val playlists: PlaylistRepository,
    owner: suspend () -> String?,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(AddToPlaylistUi())
    private val recentAtOpen = MutableStateFlow<List<Playlist>?>(null)
    private val ownerName = MutableStateFlow<Owner?>(null)
    private val additions = mutableMapOf<String, PlaylistAddition>()
    private var creation: PlaylistCreation? = null
    private val finished = Channel<Unit>(Channel.CONFLATED)

    private data class Owner(
        val name: String?,
    )

    init {
        viewModelScope.launch { ownerName.value = Owner(owner()) }
        // The recent order is taken once: adding to one must not move rows under the user's finger.
        viewModelScope.launch { recentAtOpen.value = playlists.recentDestinations(request.sourceId).first() }
    }

    val ui: StateFlow<AddToPlaylistUi> =
        combine(state, recentAtOpen, ownerName) { current, recent, owner ->
            val query = current.query.trim()
            current.copy(
                recent =
                    recent.orEmpty().filter {
                        owner != null &&
                            (it.owner == null || owner.name == null || it.owner == owner.name) &&
                            (query.isEmpty() || it.name.contains(query, ignoreCase = true))
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AddToPlaylistUi())

    val destinations: Flow<PagingData<Playlist>> =
        combine(state, recentAtOpen.filterNotNull(), ownerName.filterNotNull()) { current, recent, owner ->
            Triple(current.query, recent.map { it.id }, owner.name)
        }.flatMapLatest { (query, recent, owner) -> playlists.destinations(request.sourceId, owner, query, recent) }
            .cachedIn(viewModelScope)

    /** Emits once the songs have been added; the sheet then closes. */
    val done: Flow<Unit> = finished.receiveAsFlow()

    fun setQuery(query: String) = state.update { it.copy(query = query) }

    fun setNewName(name: String) = state.update { it.copy(newName = name, error = null) }

    fun addTo(playlist: Playlist) {
        val addition = additions.getOrPut(playlist.id) { PlaylistAddition(request.sourceId, playlist.id, request.songIds, Duplicates.Ask) }
        run(playlist.id) { finish(playlist, playlists.add(addition)) }
    }

    /** Answers the duplicate question: [skip] adds only the songs the playlist lacks, otherwise all of them. */
    fun resolveDuplicates(skip: Boolean) {
        val playlist = state.value.duplicatesIn ?: return
        additions.getValue(playlist.id).duplicates = if (skip) Duplicates.Skip else Duplicates.Keep
        state.update { it.copy(duplicatesIn = null) }
        addTo(playlist)
    }

    fun dismissDuplicates() = state.update { it.copy(duplicatesIn = null) }

    fun create() {
        val name = state.value.newName.trim()
        if (name.isEmpty() || state.value.busy != null) return
        // Same name, same playlist: a retry finishes what was created rather than making another.
        val pending = creation?.takeIf { it.name == name } ?: PlaylistCreation(request.sourceId, name, request.songIds)
        creation = pending
        run(AddToPlaylistUi.CREATING) {
            playlists.create(pending)
            showMessage(UiMessage(R.string.playlist_created, listOf(name)))
            finished.trySend(Unit)
        }
    }

    private fun finish(
        playlist: Playlist,
        outcome: AddOutcome,
    ) {
        when (outcome) {
            AddOutcome.Duplicates -> {
                state.update { it.copy(duplicatesIn = playlist) }
            }

            is AddOutcome.Added -> {
                showMessage(
                    if (outcome.count == 0) {
                        UiMessage(R.string.playlist_already_has_songs, listOf(playlist.name))
                    } else {
                        UiMessage(R.string.playlist_songs_added, listOf(playlist.name))
                    },
                )
                finished.trySend(Unit)
            }
        }
    }

    private fun run(
        busy: String,
        block: suspend () -> Unit,
    ) {
        if (state.value.busy != null) return
        state.update { it.copy(busy = busy, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                state.update { it.copy(error = messageFor(failure)) }
            } finally {
                state.update { it.copy(busy = null) }
            }
        }
    }

    private fun messageFor(failure: Exception): UiMessage =
        when {
            failure is PlaylistEditException -> {
                UiMessage(
                    when (failure.reason) {
                        PlaylistEditException.Reason.Offline -> R.string.playlist_edit_offline
                        PlaylistEditException.Reason.NotFound -> R.string.playlist_edit_not_found
                        PlaylistEditException.Reason.Changed -> R.string.playlist_edit_changed
                        PlaylistEditException.Reason.NotReported -> R.string.playlist_edit_not_reported
                    },
                )
            }

            failure is SubsonicException && failure.uiMessage != null -> {
                failure.uiMessage
            }

            else -> {
                connectionErrorMessage(failure) ?: UiMessage(R.string.playlist_edit_failed)
            }
        }
}
