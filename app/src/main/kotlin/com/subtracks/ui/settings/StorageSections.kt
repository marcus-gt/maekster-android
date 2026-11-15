package com.subtracks.ui.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import com.subtracks.R
import com.subtracks.data.db.LibraryCounts
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat

/** The on-disk cover cache (not the artwork stored with downloads). */
interface ArtworkCache {
    fun size(): Long

    fun clear()
}

/** Coil's disk and memory caches; downloaded music keeps its covers in the separate ArtworkStore. */
@OptIn(ExperimentalCoilApi::class)
class CoilArtworkCache(
    private val context: Context,
) : ArtworkCache {
    override fun size(): Long = SingletonImageLoader.get(context).diskCache?.size ?: 0

    override fun clear() {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
}

data class StorageUi(
    val counts: LibraryCounts? = null,
    val databaseBytes: Long = 0,
    val artworkBytes: Long = 0,
    val syncing: Boolean = false,
)

/** The active server's library counts, or null without a server. */
@OptIn(ExperimentalCoroutinesApi::class)
fun activeLibraryCounts(library: LibraryRepository): Flow<LibraryCounts?> =
    library.activeSourceId.flatMapLatest { id -> id?.let(library::libraryCounts) ?: flowOf(null) }

/** What the library and artwork take on the device, with clearing the cover cache and syncing on demand. */
class StorageViewModel(
    counts: Flow<LibraryCounts?>,
    syncStatus: StateFlow<SyncStatus>,
    private val requestSync: () -> Unit,
    private val artworkCache: ArtworkCache,
    private val databaseFiles: () -> List<File>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val sizesChanged = MutableStateFlow(0)

    val ui: StateFlow<StorageUi> =
        combine(counts, syncStatus, sizesChanged) { counts, status, _ ->
            StorageUi(
                counts = counts,
                databaseBytes = databaseFiles().sumOf { it.length() },
                artworkBytes = artworkCache.size(),
                syncing = status is SyncStatus.Running,
            )
        }.flowOn(io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUi())

    fun clearArtwork() {
        viewModelScope.launch(io) {
            artworkCache.clear()
            sizesChanged.update { it + 1 }
        }
    }

    fun syncNow() = requestSync()
}

/** Cover cache usage and clearing, with a confirmation as on iOS. */
@Composable
internal fun ArtworkStorageSection(
    ui: StorageUi,
    onClear: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Column {
        SectionHeader(stringResource(R.string.settings_artwork))
        InfoRow(stringResource(R.string.settings_artwork_usage), bytes(ui.artworkBytes))
        ActionRow(
            title = stringResource(R.string.settings_artwork_clear),
            icon = Icons.Rounded.DeleteSweep,
            onClick = { confirming = true },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_artwork_clear_title)) },
            text = { Text(stringResource(R.string.settings_artwork_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onClear()
                }) { Text(stringResource(R.string.settings_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.actions_cancel)) } },
        )
    }
}

/** What the mirror holds for the active server, its size, and syncing it now. */
@Composable
internal fun LibraryStorageRows(
    ui: StorageUi,
    onSyncNow: () -> Unit,
) {
    Column {
        ui.counts?.let { counts ->
            val number = remember { NumberFormat.getIntegerInstance() }
            InfoRow(
                stringResource(R.string.settings_library_contents),
                stringResource(
                    R.string.settings_library_counts,
                    number.format(counts.artists),
                    number.format(counts.albums),
                    number.format(counts.songs),
                    number.format(counts.playlists),
                ),
            )
        }
        InfoRow(stringResource(R.string.settings_library_database), bytes(ui.databaseBytes))
        ActionRow(
            title = stringResource(if (ui.syncing) R.string.settings_library_syncing else R.string.settings_library_sync_now),
            icon = Icons.Rounded.Sync,
            onClick = if (ui.syncing) null else onSyncNow,
        )
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun bytes(value: Long): String = Formatter.formatFileSize(LocalContext.current, value)
