package com.subtracks.data.recommendations

/** A provider result, not a mirrored library artist. A null server id denotes an external recommendation. */
data class SimilarArtist(
    val serverId: String?,
    val name: String,
    val coverArt: String? = null,
    val musicBrainzId: String? = null,
)

/** Remote candidates only. Callers must cache results and resolve membership against the local mirror. */
fun interface SimilarArtists {
    suspend fun similar(
        artistId: String,
        count: Int,
    ): List<SimilarArtist>
}
