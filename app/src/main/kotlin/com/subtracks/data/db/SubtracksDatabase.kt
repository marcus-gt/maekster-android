package com.subtracks.data.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.subtracks.data.model.Album
import com.subtracks.data.model.AlbumArtistRef
import com.subtracks.data.model.AlbumLabel
import com.subtracks.data.model.AlbumReleaseType
import com.subtracks.data.model.AlbumSearch
import com.subtracks.data.model.Artist
import com.subtracks.data.model.ArtistBio
import com.subtracks.data.model.ArtistRefsConverter
import com.subtracks.data.model.ArtistSearch
import com.subtracks.data.model.ArtworkSeed
import com.subtracks.data.model.AudioMuseConfig
import com.subtracks.data.model.CachedAudioMuseScore
import com.subtracks.data.model.CachedLyrics
import com.subtracks.data.model.CachedSimilarArtists
import com.subtracks.data.model.Disc
import com.subtracks.data.model.DownloadCollection
import com.subtracks.data.model.DownloadErrorConverter
import com.subtracks.data.model.DownloadStatusConverter
import com.subtracks.data.model.FreshRelease
import com.subtracks.data.model.FreshReleaseState
import com.subtracks.data.model.MusicBrainzName
import com.subtracks.data.model.PendingAction
import com.subtracks.data.model.PendingActionKindConverter
import com.subtracks.data.model.PendingListen
import com.subtracks.data.model.Pin
import com.subtracks.data.model.PinKindConverter
import com.subtracks.data.model.PlayEvent
import com.subtracks.data.model.PlaybackCursor
import com.subtracks.data.model.Playlist
import com.subtracks.data.model.PlaylistDestination
import com.subtracks.data.model.PlaylistSaveDraft
import com.subtracks.data.model.PlaylistSaveDraftSong
import com.subtracks.data.model.PlaylistSearch
import com.subtracks.data.model.PlaylistSong
import com.subtracks.data.model.PlaylistUsage
import com.subtracks.data.model.QueueEntry
import com.subtracks.data.model.QueueKindConverter
import com.subtracks.data.model.QuickPlaylist
import com.subtracks.data.model.SearchHistoryEntry
import com.subtracks.data.model.SimilarSong
import com.subtracks.data.model.Song
import com.subtracks.data.model.SongArtistRef
import com.subtracks.data.model.SongDownload
import com.subtracks.data.model.SongGenre
import com.subtracks.data.model.SongSearch
import com.subtracks.data.model.Source
import com.subtracks.data.model.SourceHeader
import com.subtracks.data.model.StoredQueueOrder
import com.subtracks.data.model.StoredQueueOrderEntry
import com.subtracks.data.model.StringListConverter
import com.subtracks.data.model.SubsonicSource
import com.subtracks.data.model.TopSong
import com.subtracks.data.model.TopSongsFetch
import com.subtracks.data.model.UpNextEntry

@Database(
    entities = [
        Source::class,
        SubsonicSource::class,
        SourceHeader::class,
        AudioMuseConfig::class,
        CachedAudioMuseScore::class,
        Artist::class,
        ArtistBio::class,
        ArtistSearch::class,
        Album::class,
        AlbumSearch::class,
        Disc::class,
        AlbumArtistRef::class,
        AlbumLabel::class,
        AlbumReleaseType::class,
        Playlist::class,
        PlaylistSearch::class,
        PlaylistSong::class,
        PlaylistUsage::class,
        Song::class,
        SongGenre::class,
        SongArtistRef::class,
        SongSearch::class,
        QueueEntry::class,
        UpNextEntry::class,
        PlaybackCursor::class,
        ArtworkSeed::class,
        SongDownload::class,
        PendingAction::class,
        Pin::class,
        SearchHistoryEntry::class,
        StoredQueueOrder::class,
        StoredQueueOrderEntry::class,
        CachedLyrics::class,
        CachedSimilarArtists::class,
        PlaylistDestination::class,
        PlaylistSaveDraft::class,
        PlaylistSaveDraftSong::class,
        QuickPlaylist::class,
        DownloadCollection::class,
        PlayEvent::class,
        PendingListen::class,
        SimilarSong::class,
        FreshRelease::class,
        FreshReleaseState::class,
        MusicBrainzName::class,
        TopSong::class,
        TopSongsFetch::class,
    ],
    version = 65,
    exportSchema = true,
)
@ColumnTypeConverters(
    PinKindConverter::class,
    QueueKindConverter::class,
    DownloadStatusConverter::class,
    DownloadErrorConverter::class,
    PendingActionKindConverter::class,
    ArtistRefsConverter::class,
    StringListConverter::class,
)
abstract class SubtracksDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun sourcesDao(): SourcesDao

    abstract fun audioMuseConfigDao(): AudioMuseConfigDao

    abstract fun audioMuseScoreDao(): AudioMuseScoreDao

    abstract fun audioMuseTrackDao(): AudioMuseTrackDao

    abstract fun queueDao(): QueueDao

    abstract fun queueOrderDao(): QueueOrderDao

    abstract fun artworkSeedDao(): ArtworkSeedDao

    abstract fun downloadDao(): DownloadDao

    abstract fun pendingActionDao(): PendingActionDao

    abstract fun pinDao(): PinDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun lyricsCacheDao(): LyricsCacheDao

    abstract fun similarArtistsCacheDao(): SimilarArtistsCacheDao

    abstract fun playlistDestinationDao(): PlaylistDestinationDao

    abstract fun playlistSaveDraftDao(): PlaylistSaveDraftDao

    abstract fun quickPlaylistDao(): QuickPlaylistDao

    abstract fun playEventDao(): PlayEventDao

    abstract fun pendingListenDao(): PendingListenDao

    abstract fun mixDao(): MixDao

    abstract fun freshReleaseDao(): FreshReleaseDao

    abstract fun topSongsDao(): TopSongsDao
}
