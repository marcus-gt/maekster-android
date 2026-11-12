package com.subtracks.ui.search

import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumSongItem
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Playlist
import java.text.Normalizer
import java.util.Locale

enum class SearchScope(
    @param:StringRes val label: Int,
) {
    All(R.string.search_scope_all),
    Songs(R.string.search_songs),
    Albums(R.string.resources_album_name),
    Artists(R.string.resources_artist_name),
    Playlists(R.string.resources_playlist_name),
}

/** One search result of any type, with what the cross-type ranking compares. */
sealed interface SearchMatch {
    val key: String
    val scope: SearchScope
    val title: String
    val detail: String

    data class SongMatch(
        val item: AlbumSongItem,
    ) : SearchMatch {
        override val key get() = "song:${item.song.id}"
        override val scope get() = SearchScope.Songs
        override val title get() = item.song.title
        override val detail get() = listOfNotNull(item.song.artist, item.song.album).joinToString(" ")
    }

    data class AlbumMatch(
        val album: Album,
    ) : SearchMatch {
        override val key get() = "album:${album.id}"
        override val scope get() = SearchScope.Albums
        override val title get() = album.name
        override val detail get() = album.albumArtist.orEmpty()
    }

    data class ArtistMatch(
        val artist: Artist,
    ) : SearchMatch {
        override val key get() = "artist:${artist.id}"
        override val scope get() = SearchScope.Artists
        override val title get() = artist.name
        override val detail get() = ""
    }

    data class PlaylistMatch(
        val playlist: Playlist,
    ) : SearchMatch {
        override val key get() = "playlist:${playlist.id}"
        override val scope get() = SearchScope.Playlists
        override val title get() = playlist.name
        override val detail get() = playlist.comment.orEmpty()
    }
}

data class SearchGroup(
    val scope: SearchScope,
    val matches: List<SearchMatch>,
    val hasMore: Boolean,
)

data class RankedSearch(
    val top: SearchMatch? = null,
    val groups: List<SearchGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = top == null && groups.isEmpty()
}

const val SEARCH_GROUP_PREVIEW = 5

/**
 * Orders results as iOS does. All shows the single best match as the top result, then one group per type in the
 * order of each type's best match, five each with the top result left out. A type scope shows all of that type.
 * Within a type the database order is kept for equal scores.
 */
internal fun rankSearch(
    results: SearchResults,
    query: String,
    scope: SearchScope,
): RankedSearch {
    val matches =
        (
            results.artists.map { SearchMatch.ArtistMatch(it) } +
                results.albums.map { SearchMatch.AlbumMatch(it) } +
                results.songs.map { SearchMatch.SongMatch(it) } +
                results.playlists.map { SearchMatch.PlaylistMatch(it) }
        ).distinctBy { it.key }
    val scores = matches.associate { it.key to searchScore(it.title, it.detail, query) }
    val byScore = compareByDescending<SearchMatch> { scores.getValue(it.key) }
    val ranked = matches.sortedWith(byScore.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.key })

    fun group(type: SearchScope) = matches.filter { it.scope == type }.sortedWith(byScore)
    if (scope != SearchScope.All) {
        val values = group(scope)
        return RankedSearch(groups = if (values.isEmpty()) emptyList() else listOf(SearchGroup(scope, values, false)))
    }
    val top = ranked.firstOrNull() ?: return RankedSearch()
    val groups =
        ranked.map { it.scope }.distinct().mapNotNull { type ->
            val values = group(type).filter { it.key != top.key }
            if (values.isEmpty()) null else SearchGroup(type, values.take(SEARCH_GROUP_PREVIEW), values.size > SEARCH_GROUP_PREVIEW)
        }
    return RankedSearch(top, groups)
}

/** iOS `LibrarySearchRanking.score`: exact 1000, prefix 800, contains 600, word prefixes 500, title and detail 200. */
internal fun searchScore(
    title: String,
    detail: String,
    query: String,
): Int {
    val term = normalizedSearchText(query)
    if (term.isEmpty()) return 0
    val name = normalizedSearchText(title)
    if (name == term) return 1_000
    if (name.startsWith(term)) return 800
    if (name.contains(term)) return 600
    val tokens = term.split(" ")
    val words = name.split(" ")
    if (tokens.all { token -> words.any { it.startsWith(token) } }) return 500
    val combined = name + " " + normalizedSearchText(detail)
    return if (tokens.all { combined.contains(it) }) 200 else 0
}

/** Case- and diacritic-folded, with every run of non-alphanumerics turned into one space. */
internal fun normalizedSearchText(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .split(NON_ALPHANUMERIC)
        .filter { it.isNotEmpty() }
        .joinToString(" ")

private val DIACRITICS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
