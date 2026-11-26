package com.subtracks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.subtracks.R
import com.subtracks.data.db.favoriteSongListSpec
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.repo.LibraryRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackController
import com.subtracks.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

class FavoriteSongsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) = refresh()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) = refresh()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != ACTION_PLAY && action != ACTION_SHUFFLE && action != ACTION_TOGGLE && action != ACTION_NEXT) {
            super.onReceive(context, intent)
            return
        }
        val sourceId = intent.getLongExtra(EXTRA_SOURCE, -1)
        val shuffle = action == ACTION_SHUFFLE
        val songId = if (action == ACTION_PLAY) intent.getStringExtra(EXTRA_SONG)?.takeIf(String::isNotBlank) ?: return else null
        val koin = GlobalContext.getOrNull() ?: return
        val pending = goAsync()
        koin.get<CoroutineScope>(named("playback")).launch {
            try {
                val sources = koin.get<SourceRepository>()
                if (sources.activeSourceIdOnce() != sourceId) return@launch
                val playback = koin.get<PlaybackController>()
                val state = playback.state.value
                if (state.context?.sourceId == sourceId && state.item != null) {
                    when (action) {
                        ACTION_TOGGLE -> {
                            playback.togglePlayPause()
                            return@launch
                        }

                        ACTION_NEXT -> {
                            if (state.hasNext) playback.next()
                            return@launch
                        }
                    }
                }
                if (action == ACTION_NEXT) return@launch
                val preferences = koin.get<UserPreferences>()
                val spec =
                    favoriteSongListSpec(
                        preferences.favoriteSort().first(),
                        preferences.offlineMode().first(),
                        preferences.sortNameMode().first(),
                    )
                val songs = koin.get<LibraryRepository>().favoriteSongsPreview(sourceId, spec, WIDGET_SONG_LIMIT).first()
                if (songs.isEmpty()) return@launch
                val ordinal = if (songId == null) 0 else songs.indexOfFirst { it.song.id == songId }
                if (ordinal < 0 || sources.activeSourceIdOnce() != sourceId || preferences.offlineMode().first() != spec.downloaded) {
                    return@launch
                }
                playback.playFavoriteSongs(sourceId, spec, ordinal.toLong(), shuffled = shuffle, inOrder = true)
            } finally {
                pending.finish()
            }
        }
    }

    private fun refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlobalContext.getOrNull()?.get<FavoriteSongsWidgetUpdater>()?.refresh()
        }
    }

    companion object {
        private const val ACTION_PLAY = "com.subtracks.widget.PLAY_FAVORITE"
        private const val ACTION_SHUFFLE = "com.subtracks.widget.SHUFFLE_FAVORITES"
        private const val ACTION_TOGGLE = "com.subtracks.widget.TOGGLE_FAVORITES"
        private const val ACTION_NEXT = "com.subtracks.widget.NEXT_FAVORITE"
        private const val EXTRA_SOURCE = "widget.source"
        private const val EXTRA_SONG = "widget.song"

        internal fun playTemplate(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, FavoriteSongsWidget::class.java).setAction(ACTION_PLAY),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun playSong(
            sourceId: Long,
            songId: String,
        ): Intent = Intent().putExtra(EXTRA_SOURCE, sourceId).putExtra(EXTRA_SONG, songId)

        internal fun toggle(
            context: Context,
            sourceId: Long,
        ) = control(context, sourceId, ACTION_TOGGLE)

        internal fun shuffle(
            context: Context,
            sourceId: Long,
        ) = control(context, sourceId, ACTION_SHUFFLE)

        internal fun next(
            context: Context,
            sourceId: Long,
        ) = control(context, sourceId, ACTION_NEXT)

        private fun control(
            context: Context,
            sourceId: Long,
            action: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, FavoriteSongsWidget::class.java).setAction(action).putExtra(EXTRA_SOURCE, sourceId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

internal const val WIDGET_SONG_LIMIT = 12

private data class WidgetFavoriteSong(
    val id: String,
    val title: String,
    val artist: String?,
    val coverArt: String?,
)

private data class FavoriteSongsContent(
    val sourceId: Long?,
    val offline: Boolean,
    val songs: List<WidgetFavoriteSong>,
    val playback: FavoriteWidgetPlayback = FavoriteWidgetPlayback(),
)

internal data class FavoriteWidgetPlayback(
    val hasItem: Boolean = false,
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val coverArt: String? = null,
)

internal fun PlaybackState.favoriteWidgetPlayback(
    sourceId: Long?,
    fallbackCover: (String) -> String?,
): FavoriteWidgetPlayback {
    val song = item ?: return FavoriteWidgetPlayback()
    if (sourceId == null || context?.sourceId != sourceId) return FavoriteWidgetPlayback()
    return FavoriteWidgetPlayback(
        hasItem = true,
        isPlaying = isPlaying,
        hasNext = hasNext,
        coverArt = if (isPlaying) song.coverArtId ?: fallbackCover(song.id) else null,
    )
}

@RequiresApi(Build.VERSION_CODES.S)
internal class FavoriteSongsWidgetUpdater(
    private val context: Context,
    private val sources: SourceRepository,
    private val library: LibraryRepository,
    private val preferences: UserPreferences,
    private val playback: Flow<PlaybackState>,
    private val scope: CoroutineScope,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, FavoriteSongsWidget::class.java)
    private val revision = MutableStateFlow(0L)
    private val artwork = WidgetArtwork(context, sources, size = 96)
    private val artworkBackground = WidgetArtworkBackground()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        val spec =
            combine(preferences.favoriteSort(), preferences.offlineMode(), preferences.sortNameMode(), ::favoriteSongListSpec)
                .distinctUntilChanged()
        scope.launch {
            revision
                .flatMapLatest {
                    if (manager.getAppWidgetIds(component).isEmpty()) {
                        emptyFlow()
                    } else {
                        combine(sources.activeSourceId(), spec, ::Pair)
                            .flatMapLatest { (sourceId, spec) ->
                                val songs =
                                    if (sourceId == null) {
                                        flowOf(emptyList())
                                    } else {
                                        library
                                            .favoriteSongsPreview(sourceId, spec, WIDGET_SONG_LIMIT)
                                            .map { items ->
                                                items.map { WidgetFavoriteSong(it.song.id, it.song.title, it.song.artist, it.coverArt) }
                                            }.distinctUntilChanged()
                                    }
                                songs.map { FavoriteSongsContent(sourceId, spec.downloaded, it) }
                            }.combine(playback) { content, state ->
                                content.copy(
                                    playback =
                                        state.favoriteWidgetPlayback(content.sourceId) { id ->
                                            content.songs.firstOrNull { it.id == id }?.coverArt
                                        },
                                )
                            }.combine(sources.activeSource) { content, readySource -> content to readySource }
                            .distinctUntilChanged()
                    }
                }.collectLatest { (content, _) ->
                    val views = views(content)
                    if (sources.activeSourceIdOnce() == content.sourceId) manager.updateAppWidget(component, views)
                }
        }
    }

    fun refresh() {
        revision.update { it + 1 }
    }

    private suspend fun views(content: FavoriteSongsContent): RemoteViews {
        val (sourceId, offline, songs, playback) = content
        val rows = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
        if (sourceId != null) {
            for ((index, song) in songs.withIndex()) {
                val row = RemoteViews(context.packageName, R.layout.widget_favorite_song)
                row.setTextViewText(R.id.widget_favorite_title, song.title)
                row.setTextViewText(R.id.widget_favorite_artist, song.artist)
                row.setViewVisibility(R.id.widget_favorite_artist, if (song.artist.isNullOrBlank()) View.GONE else View.VISIBLE)
                val cover = song.coverArt?.let { artwork.load(it, sourceId) }
                if (cover != null) row.setImageViewBitmap(R.id.widget_favorite_cover, cover)
                row.setOnClickFillInIntent(R.id.widget_favorite_row, FavoriteSongsWidget.playSong(sourceId, song.id))
                rows.addItem(index.toLong(), row)
            }
        }
        val cover = if (sourceId != null) playback.coverArt?.let { artwork.load(it, sourceId) } else null
        return RemoteViews(context.packageName, R.layout.widget_favorite_songs).apply {
            setWidgetArtworkBackground(artworkBackground.forCover(cover))
            setImageViewResource(
                R.id.widget_favorites_play,
                if (playback.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            setContentDescription(
                R.id.widget_favorites_play,
                context.getString(if (playback.isPlaying) R.string.controls_pause else R.string.controls_play),
            )
            setEmptyView(R.id.widget_favorites_list, R.id.widget_favorites_empty)
            setTextViewText(
                R.id.widget_favorites_empty,
                context.getString(
                    when {
                        sourceId == null -> R.string.widget_recent_albums_no_server
                        offline -> R.string.widget_favorite_songs_offline_empty
                        else -> R.string.widget_favorite_songs_empty
                    },
                ),
            )
            setViewVisibility(R.id.widget_favorites_empty, if (songs.isEmpty()) View.VISIBLE else View.GONE)
            if (sourceId != null) {
                control(R.id.widget_favorites_play, playback.hasItem || songs.isNotEmpty(), FavoriteSongsWidget.toggle(context, sourceId))
                control(R.id.widget_favorites_shuffle, songs.isNotEmpty(), FavoriteSongsWidget.shuffle(context, sourceId))
                control(R.id.widget_favorites_next, playback.hasNext, FavoriteSongsWidget.next(context, sourceId))
            } else {
                control(R.id.widget_favorites_play, false, null)
                control(R.id.widget_favorites_shuffle, false, null)
                control(R.id.widget_favorites_next, false, null)
            }
            setPendingIntentTemplate(R.id.widget_favorites_list, FavoriteSongsWidget.playTemplate(context))
            setRemoteAdapter(R.id.widget_favorites_list, rows.build())
        }
    }

    private fun RemoteViews.control(
        id: Int,
        enabled: Boolean,
        pendingIntent: PendingIntent?,
    ) {
        setBoolean(id, "setEnabled", enabled)
        setInt(id, "setImageAlpha", if (enabled) 255 else 97)
        setOnClickPendingIntent(id, pendingIntent.takeIf { enabled })
    }
}
