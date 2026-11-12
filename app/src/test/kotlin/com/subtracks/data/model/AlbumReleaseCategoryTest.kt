package com.subtracks.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumReleaseCategoryTest {
    // The cases from the iOS AlbumReleaseCategoryTests; Android stores the types lowercase, so both cases are covered.
    @Test
    fun releaseTypesMapToTheirShelfWithTheSecondaryTypeWinning() {
        val cases =
            mapOf(
                listOf("Single") to AlbumReleaseCategory.Single,
                listOf("album", "single") to AlbumReleaseCategory.Single,
                listOf("EP") to AlbumReleaseCategory.EP,
                listOf("Extended Play") to AlbumReleaseCategory.EP,
                listOf("ep", "single") to AlbumReleaseCategory.EP,
                listOf("Album", "Live") to AlbumReleaseCategory.Live,
                listOf("live") to AlbumReleaseCategory.Live,
                listOf("single", "live") to AlbumReleaseCategory.Live,
                listOf("album", "compilation") to AlbumReleaseCategory.Compilation,
                listOf("Album", "Soundtrack") to AlbumReleaseCategory.Other,
                listOf("album/remix") to AlbumReleaseCategory.Other,
            )
        cases.forEach { (types, expected) -> assertEquals(types.toString(), expected, AlbumReleaseCategory.of(types)) }
    }

    @Test
    fun albumsAndMissingMetadataStayAlbumsAndTheCompilationFlagCountsAlone() {
        assertEquals(AlbumReleaseCategory.Album, AlbumReleaseCategory.of(emptyList()))
        assertEquals(AlbumReleaseCategory.Album, AlbumReleaseCategory.of(listOf("album")))
        assertEquals(AlbumReleaseCategory.Album, AlbumReleaseCategory.of(emptyList(), compilation = false))
        assertEquals(AlbumReleaseCategory.Compilation, AlbumReleaseCategory.of(emptyList(), compilation = true))
    }

    @Test
    fun shelvesFollowCategoryOrderAndKeepTheAlbumOrderWithinEach() {
        val albums =
            listOf(
                album("single-new", listOf("single")),
                album("album-new", listOf("album")),
                album("live", listOf("album", "live")),
                album("album-old", emptyList()),
                album("single-old", listOf("single")),
            )

        val shelves = albums.releaseShelves()

        assertEquals(
            listOf(AlbumReleaseCategory.Album, AlbumReleaseCategory.Single, AlbumReleaseCategory.Live),
            shelves.map { it.category },
        )
        assertEquals(
            listOf(listOf("album-new", "album-old"), listOf("single-new", "single-old"), listOf("live")),
            shelves.map { shelf -> shelf.albums.map { it.id } },
        )
    }

    private fun album(
        id: String,
        types: List<String>,
    ) = Album(
        sourceId = 1,
        id = id,
        artistId = "ar",
        name = id,
        albumArtist = "Artist",
        created = 0,
        coverArt = null,
        genre = null,
        year = null,
        starred = null,
        songCount = 1,
        releaseTypes = types,
    )
}
