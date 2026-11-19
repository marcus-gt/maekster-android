package com.subtracks.playback

import android.content.res.Resources
import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.model.QueueKind

/**
 * Queues named by what they are rather than by one album or playlist. [kind] maps a single-entry queue to its
 * label; a mix made of plain song entries (Smart Shuffle) has no kind and is labelled explicitly.
 * An [endless] mix keeps its own name when autoplay extends it; anything else becomes "Similar to …".
 * A [finite] queue is never extended, by autoplay or otherwise (a Song Path ends at its destination).
 */
internal enum class QueueCollection(
    val kind: QueueKind?,
    val storedLabel: String,
    @param:StringRes val title: Int,
    val endless: Boolean = false,
    val finite: Boolean = false,
) {
    Songs(QueueKind.Library, "\u0000maekster:collection:songs", R.string.search_songs),
    Favorites(QueueKind.Starred, "\u0000maekster:collection:favorites", R.string.resources_filter_starred),
    Downloads(QueueKind.Downloaded, "\u0000maekster:collection:downloads", R.string.downloads_title),
    SmartShuffle(null, "\u0000maekster:collection:smart-shuffle", R.string.smart_shuffle_title, endless = true),
    InstantMix(null, "\u0000maekster:collection:instant-mix", R.string.instant_mix_title, endless = true),

    /** An Instant Mix seeded by an artist; its stored label is this prefix followed by the artist's name. */
    ArtistRadio(null, "\u0000maekster:collection:artist-radio:", R.string.artist_radio, endless = true),
    QueueBuilder(null, "\u0000maekster:collection:queue-builder", R.string.queue_builder_title, endless = true),
    MoodNight(null, "\u0000maekster:collection:mood-radio:night", R.string.mood_radio_queue_night, endless = true),
    MoodEnergetic(null, "\u0000maekster:collection:mood-radio:energetic", R.string.mood_radio_queue_energetic, endless = true),
    MoodWorkout(null, "\u0000maekster:collection:mood-radio:workout", R.string.mood_radio_queue_workout, endless = true),
    MoodChill(null, "\u0000maekster:collection:mood-radio:chill", R.string.mood_radio_queue_chill, endless = true),
    MoodFocus(null, "\u0000maekster:collection:mood-radio:focus", R.string.mood_radio_queue_focus, endless = true),
    SongPath(null, "\u0000maekster:collection:song-path", R.string.song_path_title, finite = true),
    ;

    companion object {
        fun forKind(kind: QueueKind): QueueCollection? = entries.firstOrNull { it.kind == kind }

        fun fromStored(label: String): QueueCollection? =
            entries.firstOrNull { it.storedLabel == label } ?: ArtistRadio.takeIf { label.startsWith(it.storedLabel) }

        /** The stored label for [artistName]'s radio, read back as "<artist> radio" by [displayName]. */
        fun artistRadioLabel(artistName: String?): String = ArtistRadio.storedLabel + artistName.orEmpty().trim()

        /** What the queue and Now Playing call a source stored as [label]. */
        fun displayName(
            label: String,
            resources: Resources,
        ): String {
            val collection = fromStored(label) ?: return NamedQueueSource.parse(label)?.second ?: label
            if (collection == ArtistRadio) {
                val artist = label.removePrefix(ArtistRadio.storedLabel)
                if (artist.isNotEmpty()) return resources.getString(R.string.artist_radio_queue, artist)
            }
            return resources.getString(collection.title)
        }

        fun forMood(mood: RadioMood): QueueCollection =
            when (mood) {
                RadioMood.NIGHT -> MoodNight
                RadioMood.ENERGETIC -> MoodEnergetic
                RadioMood.WORKOUT -> MoodWorkout
                RadioMood.CHILL -> MoodChill
                RadioMood.FOCUS -> MoodFocus
            }
    }
}
