package com.subtracks.ui.library

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
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

/** An entry on the Library landing list: one of the library's lists, or a page such as Favorites. */
enum class LibraryHubEntry(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val list: LibraryTab? = null,
) {
    Playlists(LibraryTab.Playlists.label, LibraryTab.Playlists.icon, LibraryTab.Playlists),
    Albums(LibraryTab.Albums.label, LibraryTab.Albums.icon, LibraryTab.Albums),
    Artists(LibraryTab.Artists.label, LibraryTab.Artists.icon, LibraryTab.Artists),
    Songs(LibraryTab.Songs.label, LibraryTab.Songs.icon, LibraryTab.Songs),
    Favorites(R.string.resources_filter_starred, Icons.Rounded.Favorite),
}

/** The entries in the iOS order. Genres, Labels and Downloads join them later in M2. */
internal val LIBRARY_HUB_ENTRIES = LibraryHubEntry.entries.toList()

/**
 * The entries to show offline: the lists with something downloaded, and Favorites while anything is downloaded
 * (offline it lists only downloaded favorites).
 */
internal fun offlineLibraryEntries(downloaded: Set<LibraryListTab>): List<LibraryHubEntry> =
    LIBRARY_HUB_ENTRIES.filter { entry ->
        val list = entry.list?.listTab()
        if (list != null) list in downloaded else downloaded.isNotEmpty()
    }

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryHubViewModel(
    libraryRepository: LibraryRepository,
    private val sourceRepository: SourceRepository,
    private val syncManager: SyncManager,
) : ViewModel() {
    val offline: StateFlow<Boolean> = sourceRepository.offline

    /** Every entry online; only the lists with downloads offline. Null until known, so nothing flashes. */
    val entries: StateFlow<List<LibraryHubEntry>?> =
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
