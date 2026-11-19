package com.subtracks.data.model

/**
 * The artist's latest release for the artist page card: the album with the newest year among [albums]
 * (the artist's own albums, not Appears on). Among albums of that year, the most recently added to the
 * library wins, then the first in list order. Albums without a year are never picked, so this is null
 * when none has one.
 */
fun latestRelease(albums: List<Album>): Album? =
    albums
        .filter { (it.year ?: 0) > 0 }
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<Album>>({ it.value.year }, { it.value.created }).thenByDescending { it.index },
        )?.value
