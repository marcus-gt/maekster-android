package com.subtracks.data.model

/** A way of grouping albums that the Library lists: by a song's genre tag or by the album's record label. */
enum class AlbumCollectionKind { Genre, Label }

/** One genre or record label in its list: the name, how many albums it covers, and one of their covers. */
data class AlbumCollectionSummary(
    val name: String,
    val albumCount: Long,
    val coverArt: String?,
)
