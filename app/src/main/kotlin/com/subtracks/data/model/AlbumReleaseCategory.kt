package com.subtracks.data.model

/** The discography shelf a release belongs to on an artist page. Declaration order is shelf order. */
enum class AlbumReleaseCategory {
    Album,
    EP,
    Single,
    Live,
    Compilation,
    Other,
    ;

    companion object {
        // MusicBrainz secondary types that are neither live nor compilations.
        private val otherTypes =
            setOf(
                "soundtrack",
                "remix",
                "mixtape",
                "street",
                "demo",
                "dj",
                "spokenword",
                "interview",
                "audiobook",
                "audio",
                "drama",
                "broadcast",
                "other",
            )

        /**
         * Maps synced release types (OpenSubsonic, from MusicBrainz) to a shelf. A release may carry a primary type
         * plus secondary ones ("album", "live"); the secondary type is the more specific, so it wins. No types at all
         * means an ordinary album. [compilation] is the server's flag, which marks a compilation by itself.
         */
        fun of(
            releaseTypes: List<String>,
            compilation: Boolean? = null,
        ): AlbumReleaseCategory {
            val lowered = releaseTypes.map { it.lowercase() }
            val words = lowered.flatMap { type -> type.split(Regex("[^\\p{L}]+")).filter(String::isNotEmpty) }.toSet()
            return when {
                "live" in words -> Live
                compilation == true || "compilation" in words -> Compilation
                "ep" in words || lowered.any { "extended play" in it } -> EP
                "single" in words -> Single
                words.any { it in otherTypes } -> Other
                else -> Album
            }
        }
    }
}

/** One shelf of an artist's own releases, newest first like the rest of the page. */
data class ReleaseShelf(
    val category: AlbumReleaseCategory,
    val albums: List<Album>,
)

/** Groups the artist's own albums into shelves in [AlbumReleaseCategory] order, keeping each shelf's album order. */
fun List<Album>.releaseShelves(): List<ReleaseShelf> {
    val grouped = groupBy { AlbumReleaseCategory.of(it.releaseTypes, it.compilation) }
    return AlbumReleaseCategory.entries.mapNotNull { category ->
        grouped[category]?.let { ReleaseShelf(category, it) }
    }
}
