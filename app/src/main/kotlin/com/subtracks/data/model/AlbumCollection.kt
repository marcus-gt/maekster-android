package com.subtracks.data.model

import androidx.room3.Embedded

/** A way of grouping albums that the Library lists: by a song's genre tag or by the album's record label. */
enum class AlbumCollectionKind { Genre, Label }

/** One genre or record label in its list: the name, how many albums it covers, and one of their covers. */
data class AlbumCollectionSummary(
    val name: String,
    val albumCount: Long,
    val coverArt: String?,
)

data class HomeGenreShelf(
    val genre: String,
    val albums: List<Album>,
)

data class HomeGenreAlbum(
    val shelfGenre: String,
    @Embedded val album: Album,
)

/**
 * The collections an album page links to: up to [MAX_GENRES] of its songs' genres, most-tagged first, and its
 * first record label. Names are stored as synced, so each one matches its Genres or Labels collection.
 */
data class AlbumFacets(
    val genres: List<String> = emptyList(),
    val label: String? = null,
) {
    companion object {
        const val MAX_GENRES = 3
    }
}
