package com.subtracks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.data.repo.QueueRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.log.Log
import com.subtracks.playback.QueueItem
import com.subtracks.playback.toQueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/** The home-screen Now Playing widget. Content comes from [NowPlayingWidgetUpdater]; buttons broadcast back here. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updater()?.refresh()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Below API 31 there is no size map, so the layout is picked per widget from its current width.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) updater()?.refresh()
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = actionOf(intent)
        if (action == null) {
            super.onReceive(context, intent)
            return
        }
        Log.i("widget", "button: $action")
        val koin = GlobalContext.getOrNull() ?: return
        val pending = goAsync()
        koin.get<CoroutineScope>(named("playback")).launch {
            try {
                koin.get<NowPlayingWidgetCommands>().run(action)
            } finally {
                pending.finish()
            }
        }
    }

    private fun updater(): NowPlayingWidgetUpdater? = GlobalContext.getOrNull()?.get()

    companion object {
        private const val EXTRA_ACTION = "io.github.marcus_gt.maekster.widget.ACTION"
        private const val ACTION_COMMAND = "io.github.marcus_gt.maekster.widget.COMMAND"

        fun commandIntent(
            context: Context,
            action: NowPlayingWidgetAction,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.ordinal + 1,
                Intent(context, NowPlayingWidget::class.java)
                    .setAction(ACTION_COMMAND)
                    .putExtra(EXTRA_ACTION, action.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun actionOf(intent: Intent): NowPlayingWidgetAction? {
            if (intent.action != ACTION_COMMAND) return null
            val name = intent.getStringExtra(EXTRA_ACTION) ?: return null
            return NowPlayingWidgetAction.entries.firstOrNull { it.name == name }
        }
    }
}

/** The widget's layouts and the smallest size, in dp, each one needs. */
enum class NowPlayingWidgetLayout(
    @param:LayoutRes val res: Int,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val hasArtwork: Boolean,
    val hasSkipButtons: Boolean,
) {
    /** 2x1 and other narrow sizes: title, artist and play, so the text keeps its room. */
    Small(R.layout.widget_now_playing_compact, 110f, 40f, hasArtwork = false, hasSkipButtons = false),

    /** One row (4x1): artwork, text and previous/play/next side by side. */
    Row(R.layout.widget_now_playing_row, 280f, 80f, hasArtwork = true, hasSkipButtons = true),

    /** Two rows or more (4x2): large artwork, text at the top and the controls at the bottom. */
    Tall(R.layout.widget_now_playing_wide, 250f, 170f, hasArtwork = true, hasSkipButtons = true),
    ;

    val size: SizeF get() = SizeF(minWidthDp, minHeightDp)

    companion object {
        /** As the launcher picks from a size map: the largest layout that fits, otherwise the smallest. */
        fun forSize(
            widthDp: Float,
            heightDp: Float,
        ): NowPlayingWidgetLayout =
            entries
                .filter { it.minWidthDp <= widthDp && it.minHeightDp <= heightDp }
                .maxByOrNull { it.minWidthDp * it.minHeightDp }
                ?: Small
    }
}

/** Builds the widget's [RemoteViews] and pushes them to every placed widget. */
class NowPlayingWidgetRenderer(
    private val context: Context,
) {
    private val manager: AppWidgetManager? get() = AppWidgetManager.getInstance(context)
    private val component get() = ComponentName(context, NowPlayingWidget::class.java)

    fun hasWidgets(): Boolean = manager?.getAppWidgetIds(component)?.isNotEmpty() == true

    fun render(
        content: NowPlayingWidgetContent,
        artwork: Bitmap?,
    ) {
        val manager = manager ?: return
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val views = RemoteViews(NowPlayingWidgetLayout.entries.associate { it.size to views(it, content, artwork) })
            manager.updateAppWidget(ids, views)
        } else {
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val layout =
                    NowPlayingWidgetLayout.forSize(
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(),
                    )
                manager.updateAppWidget(id, views(layout, content, artwork))
            }
        }
    }

    internal fun views(
        layout: NowPlayingWidgetLayout,
        content: NowPlayingWidgetContent,
        artwork: Bitmap?,
    ): RemoteViews =
        RemoteViews(context.packageName, layout.res).apply {
            val open = openAppIntent(content)
            setOnClickPendingIntent(R.id.widget_root, open)
            setTextViewText(R.id.widget_title, content.title ?: context.getString(R.string.widget_nothing_playing))
            val artist = if (content.title == null) context.getString(R.string.widget_nothing_playing_hint) else content.artist
            setTextViewText(R.id.widget_artist, artist.orEmpty())
            setViewVisibility(R.id.widget_artist, if (artist.isNullOrBlank()) View.GONE else View.VISIBLE)
            if (layout.hasArtwork) {
                if (artwork != null) {
                    setImageViewBitmap(R.id.widget_art, artwork)
                } else {
                    setImageViewResource(R.id.widget_art, R.drawable.ic_widget_artwork_placeholder)
                }
            }
            setImageViewResource(
                R.id.widget_play_pause,
                if (content.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (content.isPlaying) R.string.controls_pause else R.string.controls_play),
            )
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                if (content.title == null) open else NowPlayingWidget.commandIntent(context, NowPlayingWidgetAction.PlayPause),
            )
            if (layout.hasSkipButtons) {
                skipButton(R.id.widget_previous, NowPlayingWidgetAction.Previous, enabled = content.live)
                skipButton(R.id.widget_next, NowPlayingWidgetAction.Next, enabled = content.live && content.hasNext)
            }
        }

    private fun RemoteViews.skipButton(
        id: Int,
        action: NowPlayingWidgetAction,
        enabled: Boolean,
    ) {
        setBoolean(id, "setEnabled", enabled)
        setInt(id, "setImageAlpha", if (enabled) 255 else DISABLED_ALPHA)
        setOnClickPendingIntent(id, NowPlayingWidget.commandIntent(context, action))
    }

    private fun openAppIntent(content: NowPlayingWidgetContent): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (content.title != null) intent.action = MainActivity.ACTION_OPEN_NOW_PLAYING
        return PendingIntent.getActivity(
            context,
            if (content.title != null) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val DISABLED_ALPHA = 97
    }
}

/** A small artwork thumbnail for the widget, from the shared image cache. Bounded so the widget stays light. */
class NowPlayingWidgetArtwork(
    private val context: Context,
    private val sources: SourceRepository,
) {
    suspend fun load(coverArtId: String): Bitmap? {
        val ref = sources.coverArt(coverArtId, thumbnail = true) ?: return null
        val request =
            ImageRequest
                .Builder(context)
                .data(ref.url)
                .memoryCacheKey(ref.cacheKey)
                .diskCacheKey(ref.cacheKey)
                .size(ARTWORK_PX)
                .allowHardware(false)
                .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private companion object {
        const val ARTWORK_PX = 256
    }
}

/** The song at the saved queue cursor, as a restore would pick it, or null without a queue for the active server. */
suspend fun savedQueueItem(
    queues: QueueRepository,
    sources: SourceRepository,
): QueueItem? {
    val snapshot = queues.snapshot()
    if (snapshot.size == 0L) return null
    val queueSourceId =
        snapshot.entries
            .firstOrNull()
            ?.entry
            ?.sourceId
    if (queueSourceId == null || queueSourceId != sources.activeSourceIdOnce()) return null
    val position = queues.cursor().coerceIn(0, snapshot.size - 1)
    return queues.itemAt(snapshot, position)?.toQueueItem()
}
