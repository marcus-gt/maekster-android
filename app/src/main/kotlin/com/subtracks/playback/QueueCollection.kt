package com.subtracks.playback

import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.model.QueueKind

internal enum class QueueCollection(
    val kind: QueueKind,
    val storedLabel: String,
    @param:StringRes val title: Int,
) {
    Songs(QueueKind.Library, "\u0000maekster:collection:songs", R.string.search_songs),
    Favorites(QueueKind.Starred, "\u0000maekster:collection:favorites", R.string.resources_filter_starred),
    Downloads(QueueKind.Downloaded, "\u0000maekster:collection:downloads", R.string.downloads_title),
    ;

    companion object {
        fun forKind(kind: QueueKind): QueueCollection? = entries.firstOrNull { it.kind == kind }

        fun fromStored(label: String): QueueCollection? = entries.firstOrNull { it.storedLabel == label }
    }
}
