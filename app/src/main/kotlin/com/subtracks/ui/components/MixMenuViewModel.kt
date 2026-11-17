package com.subtracks.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subtracks.R
import com.subtracks.UiMessage
import com.subtracks.data.mix.MixSeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun MenuTarget.mixRequest(): Pair<Long, MixSeed>? =
    when (this) {
        is MenuTarget.Song -> song.sourceId to MixSeed.Song(song.id)
        is MenuTarget.Album -> album.sourceId to MixSeed.Album(album.id)
        is MenuTarget.Artist -> artist.sourceId to MixSeed.Artist(artist.id)
        is MenuTarget.Playlist -> null
    }

class MixMenuViewModel(
    private val play: suspend (Long, MixSeed) -> Boolean,
    private val showMessage: (UiMessage) -> Unit,
) : ViewModel() {
    private var request: Job? = null

    fun start(target: MenuTarget) {
        val (sourceId, seed) = target.mixRequest() ?: return
        if (request?.isActive == true) return
        request =
            viewModelScope.launch {
                showMessage(UiMessage(R.string.mix_preparing))
                try {
                    if (!play(sourceId, seed)) showMessage(UiMessage(R.string.mix_empty))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    showMessage(UiMessage(R.string.mix_failed))
                }
            }
    }
}
