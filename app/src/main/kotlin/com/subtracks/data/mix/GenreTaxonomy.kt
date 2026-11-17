package com.subtracks.data.mix

import java.text.Normalizer

/** How one library genre tag is grouped in the Queue Builder. */
data class ResolvedGenre(
    /** Spelling variants share a key ("Hip-Hop", "hip hop"), and "Modal Jazz" shares the Modal style's key. */
    val key: String,
    /** The main genres the tag belongs to; a style listed under two genres belongs to both. */
    val mains: List<String>,
    /** The Discogs style name when the tag is one, so its pill uses the canonical spelling. */
    val styleName: String?,
    /** The tag names a main genre itself ("Jazz"): it counts for that genre but gets no sub-genre pill. */
    val isMainTag: Boolean,
)

/**
 * Groups free-form library genre tags under the Discogs main genres. Resolution order: main genre name, exact
 * Discogs style, synonym, known sub-genre, "<style> <main>" spelling ("Modal Jazz" is Modal), keyword
 * ("East Coast Hip Hop" is Hip Hop), and otherwise [OTHER].
 */
class GenreTaxonomy(
    genres: Map<String, List<String>>,
) {
    /** Main genres in their given order. */
    val mainGenres: List<String> = genres.keys.toList()

    private val mainsByKey: Map<String, String> = genres.keys.associateBy { normalize(it) }

    private val stylesByKey: Map<String, Pair<String, List<String>>> =
        buildMap<String, Pair<String, MutableList<String>>> {
            genres.forEach { (main, styles) ->
                styles.forEach { style ->
                    val entry = getOrPut(normalize(style)) { style to mutableListOf() }
                    if (main !in entry.second) entry.second += main
                }
            }
        }

    fun resolve(tag: String): ResolvedGenre = resolve(tag, followAliases = true)

    private fun resolve(
        tag: String,
        followAliases: Boolean,
    ): ResolvedGenre {
        val key = normalize(tag)
        // Main names first: Discogs also lists "Hip Hop" as an Electronic style, which must not capture the genre.
        mainsByKey[key]?.let { return ResolvedGenre("main:$key", listOf(it), styleName = null, isMainTag = true) }
        stylesByKey[key]?.let { (name, mains) -> return ResolvedGenre(key, mains, name, isMainTag = false) }
        if (followAliases) ALIASES[key]?.let { return resolve(it, followAliases = false) }
        KNOWN_SUBGENRES[key]?.let { return ResolvedGenre(key, listOf(it), styleName = null, isMainTag = false) }
        styleWithMainWord(key)?.let { return it }
        KEYWORDS
            .firstOrNull { (fragment, main) -> fragment in key && normalize(main) in mainsByKey }
            ?.let { (_, main) -> return ResolvedGenre(key, listOf(main), styleName = null, isMainTag = false) }
        return ResolvedGenre(key, listOf(OTHER), styleName = null, isMainTag = false)
    }

    /** "Modal Jazz" and "Jazz Fusion" become the Modal and Fusion styles when those sit under Jazz. */
    private fun styleWithMainWord(key: String): ResolvedGenre? {
        for ((word, main) in MAIN_WORDS) {
            val remainder =
                when {
                    key.endsWith(word) -> key.removeSuffix(word)
                    key.startsWith(word) -> key.removePrefix(word)
                    else -> continue
                }
            if (remainder.isEmpty()) continue
            val (name, mains) = stylesByKey[remainder] ?: continue
            if (main in mains) return ResolvedGenre(remainder, mains, name, isMainTag = false)
        }
        return null
    }

    companion object {
        const val OTHER = "Other"

        val DISCOGS: GenreTaxonomy by lazy { GenreTaxonomy(DISCOGS_GENRES) }

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Lowercased, accents folded, "&" read as "and", and everything but letters and digits removed. */
        fun normalize(tag: String): String =
            Normalizer
                .normalize(tag, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase()
                .replace("&", "and")
                .filter { it.isLetterOrDigit() }

        /** Synonyms: the tag means exactly this style or main genre. */
        private val ALIASES =
            mapOf(
                "rap" to "Hip Hop",
                "randb" to "Rhythm & Blues",
                "rnb" to "Rhythm & Blues",
                "soul" to "Funk / Soul",
                "funk" to "Funk / Soul",
                "alternative" to "Alternative Rock",
                "indie" to "Indie Rock",
                "merseybeat" to "Beat",
                "beatmusic" to "Beat",
                "electronica" to "Electronic",
                "electronique" to "Electronic",
                "edm" to "Electronic",
                "electronicdancemusic" to "Electronic",
                "dance" to "Electronic",
                "drumandbass" to "Drum n Bass",
                "dnb" to "Drum n Bass",
                "futuregarage" to "UK Garage",
                "oldies" to "Pop",
                "folk" to "Folk, World, & Country",
                "country" to "Folk, World, & Country",
                "world" to "Folk, World, & Country",
                "worldmusic" to "Folk, World, & Country",
                "chansonfrancaise" to "Chanson",
                "metal" to "Heavy Metal",
                "videogamemusic" to "Soundtrack",
            )

        /** Sub-genres without a Discogs style: their own pill under this main genre. */
        private val KNOWN_SUBGENRES =
            mapOf(
                "thirdstream" to "Jazz",
                "spiritualjazz" to "Jazz",
                "dirtysouth" to "Hip Hop",
                "staxvolt" to "Funk / Soul",
                "rave" to "Electronic",
                "complextro" to "Electronic",
                "singersongwriter" to "Folk, World, & Country",
                "worldfusion" to "Folk, World, & Country",
                "klezmer" to "Folk, World, & Country",
                "concerto" to "Classical",
                "orchestra" to "Classical",
                "programmusic" to "Classical",
            )

        /** Words a tag may add to a style name: "<style> Jazz", "Jazz <style>". */
        private val MAIN_WORDS =
            listOf(
                "jazz" to "Jazz",
                "hiphop" to "Hip Hop",
                "rap" to "Hip Hop",
                "rock" to "Rock",
                "blues" to "Blues",
                "reggae" to "Reggae",
                "pop" to "Pop",
                "latin" to "Latin",
                "soul" to "Funk / Soul",
                "funk" to "Funk / Soul",
                "electronic" to "Electronic",
                "classical" to "Classical",
            )

        /** Fragment to main genre, most specific first; the first match wins. */
        private val KEYWORDS =
            listOf(
                "hiphop" to "Hip Hop",
                "rap" to "Hip Hop",
                "jazz" to "Jazz",
                "bebop" to "Jazz",
                "bop" to "Jazz",
                "swing" to "Jazz",
                "blues" to "Blues",
                "reggae" to "Reggae",
                "punk" to "Rock",
                "metal" to "Rock",
                "rock" to "Rock",
                "grunge" to "Rock",
                "house" to "Electronic",
                "techno" to "Electronic",
                "trance" to "Electronic",
                "electro" to "Electronic",
                "ambient" to "Electronic",
                "bass" to "Electronic",
                "dance" to "Electronic",
                "synth" to "Electronic",
                "idm" to "Electronic",
                "soul" to "Funk / Soul",
                "funk" to "Funk / Soul",
                "disco" to "Funk / Soul",
                "gospel" to "Funk / Soul",
                "bluegrass" to "Folk, World, & Country",
                "folk" to "Folk, World, & Country",
                "country" to "Folk, World, & Country",
                "latin" to "Latin",
                "salsa" to "Latin",
                "bossa" to "Latin",
                "classical" to "Classical",
                "baroque" to "Classical",
                "opera" to "Classical",
                "symphon" to "Classical",
                "pop" to "Pop",
                "soundtrack" to "Stage & Screen",
                "score" to "Stage & Screen",
            )
    }
}
