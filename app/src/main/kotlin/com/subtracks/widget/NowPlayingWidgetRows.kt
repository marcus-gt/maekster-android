package com.subtracks.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.subtracks.R
import com.subtracks.data.db.favoriteSongListSpec
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueCollection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** What a large Now Playing widget shows below the player; each widget has its own (UX-45). */
enum class NowPlayingWidgetRow(
    @param:StringRes val title: Int,
) {
    Moods(R.string.widget_row_moods),
    RecentlyAdded(R.string.widget_row_recently_added),
    RecentlyPlayed(R.string.widget_row_recently_played),
    FavoriteSongs(R.string.widget_row_favorite_songs),
    ArtistRadio(R.string.widget_row_artist_radio),
    Nothing(R.string.widget_row_nothing),
    ;

    companion object {
        /** A widget nobody configured shows the mood row, as before the choice existed. */
        val Default = Moods

        fun of(name: String?): NowPlayingWidgetRow = entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * One tile or line of a row: [refId] is the album, song or artist a tap plays, [isPlaying] is null unless the player
 * holds it (then it shows pause while playing and play while paused).
 */
data class NowPlayingRowItem(
    val sourceId: Long,
    val refId: String,
    val title: String,
    val subtitle: String? = null,
    val art: Bitmap? = null,
    val isPlaying: Boolean? = null,
)

/** Each widget's choice by app widget id, and the items of every chosen row. */
data class NowPlayingRows(
    val choices: Map<Int, NowPlayingWidgetRow> = emptyMap(),
    val items: Map<NowPlayingWidgetRow, List<NowPlayingRowItem>> = emptyMap(),
) {
    fun choice(appWidgetId: Int): NowPlayingWidgetRow = choices[appWidgetId] ?: NowPlayingWidgetRow.Default
}

/** Tiles in a row (albums, artists) and lines in the favourites list. */
internal const val NOW_PLAYING_ROW_TILES = 5
internal const val NOW_PLAYING_ROW_SONGS = 3

/** Where the rows' items come from, for the active server. Each is read only while some widget shows it. */
internal class NowPlayingRowSources(
    val recentlyAdded: (sourceId: Long, limit: Int) -> Flow<List<WidgetAlbum>>,
    val recentlyPlayed: (sourceId: Long, limit: Int) -> Flow<List<WidgetAlbum>>,
    val favoriteSongs: (sourceId: Long, limit: Int) -> Flow<List<Song>>,
    val stationArtists: (sourceId: Long) -> Flow<List<Artist>>,
    val albumArt: suspend (sourceId: Long, coverArt: String) -> Bitmap?,
    val artistArt: suspend (Artist) -> Bitmap?,
)

/**
 * The rows' items as the matching widgets read them: Home's album shelves, the favourites in Library order (downloads
 * only in offline mode) and Discover's artist radio picks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun nowPlayingRowSources(
    context: Context,
    sources: SourceRepository,
    library: LibraryRepository,
    preferences: UserPreferences,
    stationArtists: (sourceId: Long) -> Flow<List<Artist>>,
): NowPlayingRowSources {
    val artwork = WidgetArtwork(context, sources, size = 96)
    val albums = { shelf: AlbumShelf ->
        { sourceId: Long, limit: Int ->
            shelf.albums(library, sourceId, limit).map { list -> list.map { WidgetAlbum(it.id, it.name, it.coverArt) } }
        }
    }
    val favoriteSpec =
        combine(preferences.favoriteSort(), preferences.offlineMode(), preferences.sortNameMode(), ::favoriteSongListSpec)
            .distinctUntilChanged()
    return NowPlayingRowSources(
        recentlyAdded = albums(AlbumShelf.RecentlyAdded),
        recentlyPlayed = albums(AlbumShelf.RecentlyPlayed),
        favoriteSongs = { sourceId, limit ->
            favoriteSpec
                .flatMapLatest { spec ->
                    library.favoriteSongsPreview(sourceId, spec, limit)
                }.map { items -> items.map { it.song } }
        },
        stationArtists = stationArtists,
        albumArt = { sourceId, cover -> artwork.load(cover, sourceId) },
        artistArt = { artist -> widgetArtistBitmap(sources, artwork, artist) },
    )
}

/**
 * The rows the placed widgets chose, with their items for [activeSourceId]: covers load when the list changes, and
 * the play state follows [playback] and the stored queue label (for artist radio).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun nowPlayingRows(
    choices: Flow<Map<Int, NowPlayingWidgetRow>>,
    activeSourceId: Flow<Long?>,
    playback: Flow<PlaybackState>,
    queueLabel: Flow<String?>,
    sources: NowPlayingRowSources,
): Flow<NowPlayingRows> =
    combine(choices.distinctUntilChanged(), activeSourceId.distinctUntilChanged()) { chosen, sourceId -> chosen to sourceId }
        .flatMapLatest { (chosen, sourceId) ->
            val rows = chosen.values.toSet().filter { it.hasItems }
            if (sourceId == null || rows.isEmpty()) {
                flowOf(NowPlayingRows(chosen))
            } else {
                val loaded = rows.map { row -> rowItems(row, sourceId, sources).map { row to it } }
                combine(combine(loaded) { it.toMap() }, playback, queueLabel) { items, state, label ->
                    NowPlayingRows(chosen, items.mapValues { (row, list) -> list.withPlayState(row, sourceId, state, label) })
                }
            }
        }.distinctUntilChanged()

private val NowPlayingWidgetRow.hasItems: Boolean get() = this != NowPlayingWidgetRow.Moods && this != NowPlayingWidgetRow.Nothing

@OptIn(ExperimentalCoroutinesApi::class)
private fun rowItems(
    row: NowPlayingWidgetRow,
    sourceId: Long,
    sources: NowPlayingRowSources,
): Flow<List<NowPlayingRowItem>> =
    when (row) {
        NowPlayingWidgetRow.RecentlyAdded, NowPlayingWidgetRow.RecentlyPlayed -> {
            val albums = if (row == NowPlayingWidgetRow.RecentlyAdded) sources.recentlyAdded else sources.recentlyPlayed
            albums(sourceId, NOW_PLAYING_ROW_TILES).distinctUntilChanged().mapLatest { list ->
                list.take(NOW_PLAYING_ROW_TILES).map { album ->
                    NowPlayingRowItem(sourceId, album.id, album.title, art = album.coverArt?.let { sources.albumArt(sourceId, it) })
                }
            }
        }

        NowPlayingWidgetRow.FavoriteSongs -> {
            sources.favoriteSongs(sourceId, NOW_PLAYING_ROW_SONGS).distinctUntilChanged().map { songs ->
                songs.take(NOW_PLAYING_ROW_SONGS).map { NowPlayingRowItem(sourceId, it.id, it.title, subtitle = it.artist) }
            }
        }

        NowPlayingWidgetRow.ArtistRadio -> {
            sources.stationArtists(sourceId).distinctUntilChanged().mapLatest { artists ->
                artists.take(NOW_PLAYING_ROW_TILES).map { artist ->
                    NowPlayingRowItem(sourceId, artist.id, artist.name, art = sources.artistArt(artist))
                }
            }
        }

        NowPlayingWidgetRow.Moods, NowPlayingWidgetRow.Nothing -> {
            flowOf(emptyList())
        }
    }

/** Marks the item the player holds: its album, its song, or its artist radio (by the stored queue label). */
internal fun List<NowPlayingRowItem>.withPlayState(
    row: NowPlayingWidgetRow,
    sourceId: Long,
    state: PlaybackState,
    queueLabel: String?,
): List<NowPlayingRowItem> =
    map { item ->
        val held =
            when (row) {
                NowPlayingWidgetRow.RecentlyAdded, NowPlayingWidgetRow.RecentlyPlayed -> {
                    state.albumIsPlaying(sourceId, item.refId) != null
                }

                NowPlayingWidgetRow.FavoriteSongs -> {
                    state.context?.sourceId == sourceId && state.item?.id == item.refId
                }

                NowPlayingWidgetRow.ArtistRadio -> {
                    state.radioLabel(sourceId, queueLabel) == QueueCollection.artistRadioLabel(item.title)
                }

                NowPlayingWidgetRow.Moods, NowPlayingWidgetRow.Nothing -> {
                    false
                }
            }
        item.copy(isPlaying = state.isPlaying.takeIf { held })
    }
