package com.subtracks.data.musicbrainz

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Matching rules for "new releases by artists in your library that you don't own", as on iOS. */
object NewReleaseMatching {
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val ARTICLES = Regex("\\b(the|a|an)\\b")
    private const val FURNITURE = "(deluxe|expanded|remaster(ed)?|edition|anniversary|reissue|bonus|version|mono|stereo)"
    private val BRACKETED_FURNITURE = Regex("[(\\[][^)\\]]*\\b$FURNITURE\\b[^)\\]]*[)\\]]")
    private val DASHED_FURNITURE = Regex("\\s[-–—]\\s.*\\b(deluxe|remaster(ed)?|edition|reissue)\\b.*$")

    /** Letters accent folding leaves alone, spelled out: "Røyksopp" matches "Royksopp". */
    private val LETTERS = mapOf('ø' to "o", 'æ' to "ae", 'ß' to "ss", 'đ' to "d", 'ð' to "d", 'ł' to "l", 'þ' to "th", 'œ' to "oe")

    /** Taggers sometimes write Discogs integers into MusicBrainz id fields; only real UUIDs are usable. */
    fun isMusicBrainzId(value: String?): Boolean = value != null && UUID.matches(value)

    /**
     * A title folded down to what two catalogues agree on: diacritics, curly quotes, articles and edition furniture
     * ("(Deluxe Edition)", " - 2011 Remaster") are dropped, so "Amygdala (deluxe edition)" equals "Amygdala".
     */
    fun normalisedTitle(title: String): String {
        var text = folded(title)
        text =
            text
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
        text = ARTICLES.replace(text, " ")
        text = BRACKETED_FURNITURE.replace(text, " ")
        text = DASHED_FURNITURE.replace(text, " ")
        return words(text)
    }

    /** Artist names compared for a name lookup: case, diacritics and punctuation don't matter. */
    fun normalisedName(name: String): String = words(folded(name))

    /** "Album", "Single · Remix", "Album · Live". */
    fun typeLabel(
        primary: String?,
        secondary: List<String>,
    ): String = (listOf(primary ?: "Release") + secondary).joinToString(" · ")

    /** MusicBrainz dates are partial ("2026", "2026-09", "2026-09-22"); comparing them as text handles all of them. Unknown dates count as released. */
    fun isReleased(
        date: String?,
        today: String,
    ): Boolean = date.isNullOrEmpty() || date <= today

    /** "2026-10-27" in UTC, the day format MusicBrainz' date range queries take. */
    fun musicBrainzDay(epochMillis: Long): String =
        Instant
            .ofEpochMilli(epochMillis)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .toString()

    /** "2026-09" becomes 1 September 2026; null when the date has no year. */
    fun date(musicBrainz: String?): LocalDate? {
        val parts = musicBrainz?.split("-")?.map { it.toIntOrNull() ?: return null } ?: return null
        val year = parts.firstOrNull() ?: return null
        return runCatching { LocalDate.of(year, parts.getOrElse(1) { 1 }, parts.getOrElse(2) { 1 }) }.getOrNull()
    }

    private fun words(text: String): String =
        NOT_ALPHANUMERIC
            .replace(text, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private fun folded(text: String): String {
        val spelled = buildString { text.lowercase().forEach { append(LETTERS[it] ?: it) } }
        return MARKS.replace(Normalizer.normalize(spelled, Normalizer.Form.NFKD), "")
    }
}
