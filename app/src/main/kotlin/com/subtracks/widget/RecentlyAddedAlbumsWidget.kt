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
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.data.model.Album
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

class RecentlyAddedAlbumsWidget : AppWidgetProvider() {
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
        if (intent.action != ACTION_PLAY_ALBUM) {
            super.onReceive(context, intent)
            return
        }
        val sourceId = intent.getLongExtra(EXTRA_SOURCE, -1)
        val albumId = intent.getStringExtra(EXTRA_ALBUM)?.takeIf { it.isNotBlank() } ?: return
        val koin = GlobalContext.getOrNull() ?: return
        val pending = goAsync()
        koin.get<CoroutineScope>(named("playback")).launch {
            try {
                if (koin.get<SourceRepository>().activeSourceIdOnce() == sourceId) {
                    val playback = koin.get<PlaybackController>()
                    if (playback.state.value.albumIsPlaying(sourceId, albumId) != null) {
                        playback.togglePlayPause()
                    } else {
                        playback.playAlbumInOrder(sourceId, albumId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlobalContext.getOrNull()?.get<RecentlyAddedAlbumsWidgetUpdater>()?.refresh()
        }
    }

    companion object {
        private const val ACTION_PLAY_ALBUM = "com.subtracks.widget.PLAY_ALBUM"
        private const val EXTRA_SOURCE = "widget.source"
        private const val EXTRA_ALBUM = "widget.album"

        internal fun playTemplate(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, RecentlyAddedAlbumsWidget::class.java).setAction(ACTION_PLAY_ALBUM),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun playAlbum(
            sourceId: Long,
            albumId: String,
        ): Intent =
            Intent()
                .putExtra(EXTRA_SOURCE, sourceId)
                .putExtra(EXTRA_ALBUM, albumId)
    }
}

internal data class RecentAlbumsContent(
    val sourceId: Long?,
    val albums: List<WidgetAlbum> = emptyList(),
    val backgroundCoverArt: String? = null,
)

internal data class WidgetAlbum(
    val id: String,
    val title: String,
    val coverArt: String?,
    val isPlaying: Boolean? = null,
)

internal fun PlaybackState.albumIsPlaying(
    sourceId: Long,
    albumId: String,
): Boolean? = isPlaying.takeIf { context?.sourceId == sourceId && item?.albumId == albumId }

internal fun RecentAlbumsContent.withPlayback(state: PlaybackState): RecentAlbumsContent {
    val sourceId = sourceId ?: return this
    val playingAlbum = state.item?.takeIf { state.isPlaying && state.context?.sourceId == sourceId }
    return copy(
        albums = albums.map { it.copy(isPlaying = state.albumIsPlaying(sourceId, it.id)) },
        backgroundCoverArt = playingAlbum?.let { item -> item.coverArtId ?: albums.firstOrNull { it.id == item.albumId }?.coverArt },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun recentAlbumsContent(
    activeSourceId: Flow<Long?>,
    albums: (Long, Int) -> Flow<List<Album>>,
): Flow<RecentAlbumsContent> =
    activeSourceId.distinctUntilChanged().flatMapLatest { sourceId ->
        if (sourceId == null) {
            flowOf(RecentAlbumsContent(null))
        } else {
            albums(sourceId, 12)
                .map { shelf -> RecentAlbumsContent(sourceId, shelf.map { WidgetAlbum(it.id, it.name, it.coverArt) }) }
                .distinctUntilChanged()
        }
    }

@RequiresApi(Build.VERSION_CODES.S)
internal class RecentlyAddedAlbumsWidgetUpdater(
    private val context: Context,
    private val sources: SourceRepository,
    private val library: LibraryRepository,
    private val playback: Flow<PlaybackState>,
    private val scope: CoroutineScope,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, RecentlyAddedAlbumsWidget::class.java)
    private val revision = MutableStateFlow(0L)
    private val artwork = WidgetArtwork(context, sources, size = 96)
    private val artworkBackground = WidgetArtworkBackground()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            revision
                .flatMapLatest {
                    if (manager.getAppWidgetIds(component).isEmpty()) {
                        emptyFlow()
                    } else {
                        recentAlbumsContent(sources.activeSourceId(), library::recentlyAddedAlbums)
                            .combine(playback, RecentAlbumsContent::withPlayback)
                            .distinctUntilChanged()
                    }
                }.collectLatest { content ->
                    val views = views(content)
                    if (sources.activeSourceIdOnce() == content.sourceId) manager.updateAppWidget(component, views)
                }
        }
    }

    fun refresh() {
        revision.update { it + 1 }
    }

    private suspend fun views(content: RecentAlbumsContent): RemoteViews {
        val rows = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
        val sourceId = content.sourceId
        if (sourceId != null) addAlbums(rows, sourceId, content.albums)
        val cover = if (sourceId != null) content.backgroundCoverArt?.let { artwork.load(it, sourceId) } else null
        return RemoteViews(context.packageName, R.layout.widget_recent_albums).apply {
            setWidgetArtworkBackground(artworkBackground.forCover(cover))
            setEmptyView(R.id.widget_album_grid, R.id.widget_album_empty)
            setTextViewText(
                R.id.widget_album_empty,
                context.getString(
                    if (content.sourceId == null) R.string.widget_recent_albums_no_server else R.string.widget_recent_albums_empty,
                ),
            )
            setViewVisibility(R.id.widget_album_empty, if (content.albums.isEmpty()) View.VISIBLE else View.GONE)
            setPendingIntentTemplate(R.id.widget_album_grid, RecentlyAddedAlbumsWidget.playTemplate(context))
            setRemoteAdapter(R.id.widget_album_grid, rows.build())
            setOnClickPendingIntent(
                R.id.widget_album_header,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .setAction(if (content.sourceId == null) Intent.ACTION_MAIN else MainActivity.ACTION_OPEN_RECENT_ALBUMS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }

    private suspend fun addAlbums(
        rows: RemoteViews.RemoteCollectionItems.Builder,
        sourceId: Long,
        albums: List<WidgetAlbum>,
    ) {
        for ((index, album) in albums.withIndex()) {
            val row = RemoteViews(context.packageName, R.layout.widget_recent_album)
            row.setTextViewText(R.id.widget_album_title, album.title)
            val isPlaying = album.isPlaying
            row.setViewVisibility(R.id.widget_album_playback, if (isPlaying == null) View.GONE else View.VISIBLE)
            val action = if (isPlaying == true) R.string.controls_pause else R.string.controls_play
            row.setContentDescription(
                R.id.widget_album_cell,
                context.getString(R.string.widget_album_action, context.getString(action), album.title),
            )
            if (isPlaying != null) {
                row.setImageViewResource(
                    R.id.widget_album_playback,
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
            }
            val bitmap = album.coverArt?.let { artwork.load(it, sourceId) }
            if (bitmap != null) row.setImageViewBitmap(R.id.widget_album_cover, bitmap)
            row.setOnClickFillInIntent(R.id.widget_album_cell, RecentlyAddedAlbumsWidget.playAlbum(sourceId, album.id))
            rows.addItem(index.toLong(), row)
        }
    }
}
