package com.subtracks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.subtracks.data.model.ArtistRef
import com.subtracks.data.model.PinKind
import com.subtracks.data.model.PinRef
import com.subtracks.data.model.Song
import com.subtracks.data.repo.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

fun MenuTarget.pinRef(): PinRef? =
    when (this) {
        is MenuTarget.Album -> PinRef(album.sourceId, PinKind.Album, album.id)
        is MenuTarget.Playlist -> PinRef(playlist.sourceId, PinKind.Playlist, playlist.id)
        is MenuTarget.Artist -> PinRef(artist.sourceId, PinKind.Artist, artist.id)
        is MenuTarget.Song -> null
    }

class PinMenuViewModel(
    private val library: LibraryRepository,
) : ViewModel() {
    fun pinned(ref: PinRef?): Flow<Boolean?> = ref?.let(library::isPinned) ?: flowOf(null)

    fun setPinned(
        ref: PinRef,
        pinned: Boolean,
    ) {
        viewModelScope.launch { library.setPinned(ref, pinned) }
    }
}

@Composable
fun PinMenuRoute(
    target: MenuTarget,
    actions: ItemActions,
    onDismiss: () -> Unit,
    onInfo: (MenuTarget) -> Unit,
    onChooseArtists: (List<ArtistRef>) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    viewModel: PinMenuViewModel = koinViewModel(),
) {
    val ref = target.pinRef()
    key(ref) {
        val pinned by remember(ref) { viewModel.pinned(ref) }.collectAsStateWithLifecycle(initialValue = null)
        ItemContextMenu(
            target,
            actions,
            onDismiss,
            onInfo,
            onChooseArtists,
            pinned = pinned,
            onSetPinned = ref?.let { reference -> { value -> viewModel.setPinned(reference, value) } },
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}
