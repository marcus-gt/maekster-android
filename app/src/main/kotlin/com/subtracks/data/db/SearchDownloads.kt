package com.subtracks.data.db

internal const val SEARCH_ALBUM_DOWNLOADED =
    "EXISTS (SELECT 1 FROM songs JOIN song_downloads sd " +
        "ON sd.sourceId = songs.sourceId AND sd.songId = songs.id AND sd.status = 'Completed' " +
        "WHERE songs.sourceId = albums.sourceId AND songs.albumId = albums.id)"

internal const val SEARCH_ARTIST_DOWNLOADED =
    "EXISTS (SELECT 1 FROM ($ARTIST_DOWNLOAD_ROWS) dl WHERE dl.id = artists.id AND dl.status = 'Completed')"

internal const val SEARCH_PLAYLIST_DOWNLOADED =
    "EXISTS (SELECT 1 FROM playlist_songs ps JOIN song_downloads sd " +
        "ON sd.sourceId = ps.sourceId AND sd.songId = ps.songId AND sd.status = 'Completed' " +
        "WHERE ps.sourceId = playlists.sourceId AND ps.playlistId = playlists.id)"

internal const val SEARCH_SONG_DOWNLOADED =
    "EXISTS (SELECT 1 FROM song_downloads sd WHERE sd.sourceId = songs.sourceId " +
        "AND sd.songId = songs.id AND sd.status = 'Completed')"
