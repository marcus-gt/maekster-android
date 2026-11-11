package com.subtracks.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.data.prefs.LibraryListTab
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.data.sync.SyncManager
import com.subtracks.data.sync.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Library landing list's entries, in order. Songs, Genres, Favorites and Downloads join them in M2. */
internal val LIBRARY_HUB_ENTRIES = listOf(LibraryTab.Playlists, LibraryTab.Albums, LibraryTab.Artists)

/** The entries to show offline: only the lists that have something downloaded. */
internal fun offlineLibraryEntries(downloaded: Set<LibraryListTab>): List<LibraryTab> =
    LIBRARY_HUB_ENTRIES.filter { it.listTab() in downloaded }

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryHubViewModel(
    libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    val offline: StateFlow<Boolean> = sourceRepository.offline

    /** Every entry online; only the lists with downloads offline. Null until known, so nothing flashes. */
    val entries: StateFlow<List<LibraryTab>?> =
        combine(libraryRepository.activeSourceId.filterNotNull(), sourceRepository.offline) { sourceId, offline ->
            sourceId to offline
        }.flatMapLatest { (sourceId, offline) ->
            if (offline) {
                libraryRepository.downloadedLists(sourceId).map(::offlineLibraryEntries)
            } else {
                flowOf(LIBRARY_HUB_ENTRIES)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncing: StateFlow<Boolean> =
        syncManager.status
            .map { it is SyncStatus.Running }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val refreshing: StateFlow<Boolean> =
        syncManager.status
            .map { it is SyncStatus.Running && !it.silent }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun sync() {
        if (syncManager.status.value !is SyncStatus.Running) syncManager.requestSync()
    }

    fun goOnline() = sourceRepository.setOfflineMode(false)
}
