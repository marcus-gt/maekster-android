package com.subtracks.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.model.Playlist
import com.subtracks.data.repo.AddOutcome
import com.subtracks.data.repo.Duplicates
import com.subtracks.data.repo.PlaylistAddition
import com.subtracks.data.repo.PlaylistCreation
import com.subtracks.data.repo.PlaylistRepository
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

/** How much of the selection a playlist holds, for the mark beside it (iOS #14). */
enum class PlaylistMembership { None, Some, All }

data class AddToPlaylistUi(
    val query: String = "",
    val recent: List<Playlist> = emptyList(),
    val newName: String = "",
    /** The playlist being written to, or [CREATING] while a new one is made. */
    val busy: String? = null,
    val error: UiMessage? = null,
    /** Playlist id to how much of the selection it holds, from the mirror; missing means none. */
    val membership: Map<String, PlaylistMembership> = emptyMap(),
    /** A playlist holding every selected song, waiting for the user to confirm removing them (several songs). */
    val pendingRemoval: Playlist? = null,
    val songCount: Int = 0,
) {
    fun membership(playlist: Playlist): PlaylistMembership = membership[playlist.id] ?: PlaylistMembership.None

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

    private val selection = request.songIds.distinct()

    val ui: StateFlow<AddToPlaylistUi> =
        combine(state, recentAtOpen, ownerName, playlists.membership(request.sourceId, selection)) { current, recent, owner, held ->
            val query = current.query.trim()
            current.copy(
                songCount = selection.size,
                membership =
                    held.mapValues { (_, count) ->
                        if (count >= selection.size) PlaylistMembership.All else PlaylistMembership.Some
                    },
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

    /**
     * A tap on a playlist, as on iOS: one that holds every selected song takes them out (asking first for several),
     * any other is filled in with the songs it lacks, never adding a duplicate.
     */
    fun toggle(playlist: Playlist) {
        if (ui.value.membership(playlist) != PlaylistMembership.All) return addTo(playlist)
        if (selection.size == 1) removeFrom(playlist) else state.update { it.copy(pendingRemoval = playlist) }
    }

    fun confirmRemoval() {
        val playlist = state.value.pendingRemoval ?: return
        state.update { it.copy(pendingRemoval = null) }
        removeFrom(playlist)
    }

    fun dismissRemoval() = state.update { it.copy(pendingRemoval = null) }

    private fun addTo(playlist: Playlist) {
        val addition = additions.getOrPut(playlist.id) { PlaylistAddition(request.sourceId, playlist.id, request.songIds, Duplicates.Skip) }
        run(playlist.id) { finish(playlist, playlists.add(addition)) }
    }

    private fun removeFrom(playlist: Playlist) {
        run(playlist.id) {
            val removed = playlists.removeSongs(request.sourceId, playlist.id, selection)
            // A later tap on the same playlist starts a fresh addition.
            additions.remove(playlist.id)
            showMessage(
                if (selection.size == 1 || removed == 0) {
                    UiMessage(R.string.playlist_song_removed, listOf(playlist.name))
                } else {
                    UiMessage(pluralsId = R.plurals.playlist_songs_removed, quantity = removed, args = listOf(removed, playlist.name))
                },
            )
            finished.trySend(Unit)
        }
    }

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
                error("Additions from the sheet skip duplicates, which never asks")
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
                state.update { it.copy(error = playlistEditMessage(failure)) }
            } finally {
                state.update { it.copy(busy = null) }
            }
        }
    }
}
