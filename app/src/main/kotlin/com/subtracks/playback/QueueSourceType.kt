package com.subtracks.playback

import android.content.res.Resources
import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.model.QueueKind

/**
 * A named source (an album, playlist, artist or genre, or a single song labelled by its album) stored with its
 * kind, so the type survives process death: `"\u0000maekster:source:<kind>:<name>"`. Labels saved before the
 * kind was stored are the plain name.
 */
internal object NamedQueueSource {
    private const val PREFIX = "\u0000maekster:source:"

    fun label(
        kind: QueueKind,
        name: String,
    ): String = "$PREFIX${kind.name}:$name"

    fun parse(label: String): Pair<QueueKind, String>? {
        if (!label.startsWith(PREFIX)) return null
        val rest = label.removePrefix(PREFIX)
        val kind = QueueKind.entries.firstOrNull { it.name == rest.substringBefore(':') } ?: return null
        return kind to rest.substringAfter(':')
    }
}

/**
 * Now Playing's second line: what is playing. A playlist, artist or genre is named ("Jazz tunes", "Bob Dylan",
 * "Contemporary Jazz"); an album is only "Album", since its name sits under the cover; everything else is its
 * type or collection (Favorites, Instant Mix, "Bob Dylan radio"…). Once autoplay extends a finite source it
 * reads "Similar to <source>"; mixes and radios keep their own name. [contextKind] covers a queue with no
 * stored source.
 */
internal fun nowPlayingSourceType(
    label: String?,
    contextKind: QueueKind?,
    generated: Boolean,
    resources: Resources,
): String {
    val collection = label?.let(QueueCollection::fromStored)
    if (label != null && collection != null) {
        val name = QueueCollection.displayName(label, resources)
        return if (generated && !collection.endless) resources.getString(R.string.queue_similar_to, name) else name
    }
    val named = label?.let(NamedQueueSource::parse)
    if (generated) {
        val name = named?.second ?: label
        return name?.takeIf { it.isNotBlank() }?.let { resources.getString(R.string.queue_similar_to, it) }
            ?: resources.getString(R.string.queue_type_autoplay)
    }
    val kind = named?.first ?: contextKind
    val name = named?.second ?: label
    if (kind in NAMED_KINDS && !name.isNullOrBlank()) return name
    val title =
        when {
            named != null -> named.first.typeTitle(labelledByAlbum = true)
            else -> contextKind?.typeTitle(labelledByAlbum = false)
        }
    return resources.getString(title ?: R.string.queue_type_queue)
}

/** Sources Now Playing names rather than types, as the owner asked in #54. */
private val NAMED_KINDS = setOf(QueueKind.Playlist, QueueKind.Artist, QueueKind.ArtistAppearances, QueueKind.Genre)

/** A stored single-song source is labelled by its album; a bare song entry is just songs. */
@StringRes
private fun QueueKind.typeTitle(labelledByAlbum: Boolean): Int =
    when (this) {
        QueueKind.Album -> R.string.queue_type_album
        QueueKind.Song -> if (labelledByAlbum) R.string.queue_type_album else R.string.search_songs
        QueueKind.Playlist -> R.string.queue_type_playlist
        QueueKind.Artist, QueueKind.ArtistAppearances -> R.string.queue_type_artist
        QueueKind.Genre -> R.string.queue_type_genre
        QueueKind.Downloaded -> R.string.downloads_title
        QueueKind.Starred -> R.string.resources_filter_starred
        QueueKind.Library, QueueKind.Order -> R.string.search_songs
    }
