package com.subtracks.playback

import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.model.QueueKind

/**
 * Queues named by what they are rather than by one album or playlist. [kind] maps a single-entry queue to its
 * label; a mix made of plain song entries (Smart Shuffle) has no kind and is labelled explicitly.
 * An [endless] mix keeps its own name when autoplay extends it; anything else becomes "Similar to …".
 */
internal enum class QueueCollection(
    val kind: QueueKind?,
    val storedLabel: String,
    @param:StringRes val title: Int,
    val endless: Boolean = false,
) {
    Songs(QueueKind.Library, "\u0000maekster:collection:songs", R.string.search_songs),
    Favorites(QueueKind.Starred, "\u0000maekster:collection:favorites", R.string.resources_filter_starred),
    Downloads(QueueKind.Downloaded, "\u0000maekster:collection:downloads", R.string.downloads_title),
    SmartShuffle(null, "\u0000maekster:collection:smart-shuffle", R.string.smart_shuffle_title, endless = true),
    InstantMix(null, "\u0000maekster:collection:instant-mix", R.string.instant_mix_title, endless = true),
    ;

    companion object {
        fun forKind(kind: QueueKind): QueueCollection? = entries.firstOrNull { it.kind == kind }

        fun fromStored(label: String): QueueCollection? = entries.firstOrNull { it.storedLabel == label }
    }
}
