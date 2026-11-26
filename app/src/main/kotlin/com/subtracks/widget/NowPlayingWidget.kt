package com.subtracks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.model.coverArtKey
import com.subtracks.data.repo.QueueRepository
import com.subtracks.data.repo.SourceRepository
import com.subtracks.log.Log
import com.subtracks.playback.QueueCollection
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
        val mood = moodOf(intent)
        if (action == null && mood == null) {
            super.onReceive(context, intent)
            return
        }
        Log.i("widget", "button: ${action ?: "mood ${mood?.key}"}")
        val koin = GlobalContext.getOrNull() ?: return
        val pending = goAsync()
        koin.get<CoroutineScope>(named("playback")).launch {
            try {
                val commands = koin.get<NowPlayingWidgetCommands>()
                if (action != null) {
                    commands.run(action)
                } else if (mood != null) {
                    commands.startMood(mood)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun updater(): NowPlayingWidgetUpdater? = GlobalContext.getOrNull()?.get()

    companion object {
        private const val EXTRA_ACTION = "io.github.marcus_gt.maekster.widget.ACTION"
        private const val ACTION_COMMAND = "io.github.marcus_gt.maekster.widget.COMMAND"
        private const val EXTRA_MOOD = "io.github.marcus_gt.maekster.widget.MOOD"
        private const val ACTION_MOOD = "io.github.marcus_gt.maekster.widget.MOOD_RADIO"
        private const val MOOD_REQUEST_CODE = 100

        fun moodIntent(
            context: Context,
            mood: RadioMood,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                MOOD_REQUEST_CODE + mood.ordinal,
                Intent(context, NowPlayingWidget::class.java)
                    .setAction(ACTION_MOOD)
                    .putExtra(EXTRA_MOOD, mood.key),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun moodOf(intent: Intent): RadioMood? {
            if (intent.action != ACTION_MOOD) return null
            val key = intent.getStringExtra(EXTRA_MOOD) ?: return null
            return RadioMood.entries.firstOrNull { it.key == key }
        }

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
    val hasMoods: Boolean = false,
) {
    /** 2x1 and other narrow sizes: title, artist and play, so the text keeps its room. */
    Small(R.layout.widget_now_playing_compact, 110f, 40f, hasArtwork = false, hasSkipButtons = false),

    /** One row (4x1): artwork, text and previous/play/next side by side. */
    Row(R.layout.widget_now_playing_row, 280f, 80f, hasArtwork = true, hasSkipButtons = true),

    /** Two rows or more (4x2): large artwork, text at the top and the controls at the bottom. */
    Tall(R.layout.widget_now_playing_wide, 250f, 170f, hasArtwork = true, hasSkipButtons = true),

    /** Tall with room to spare (4x2 on a Pixel): a smaller player and a row of mood radios. Tall without moods. */
    Moods(R.layout.widget_now_playing_moods, 250f, 200f, hasArtwork = true, hasSkipButtons = true, hasMoods = true),
    ;

    /** What this size shows: without mood radios to offer, the mood layout gives its room back to the player. */
    fun shown(moods: Boolean): NowPlayingWidgetLayout = if (hasMoods && !moods) Tall else this

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
    private val artworkBackground = WidgetArtworkBackground()

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
        size: NowPlayingWidgetLayout,
        content: NowPlayingWidgetContent,
        artwork: Bitmap?,
    ): RemoteViews {
        val layout = size.shown(content.moods)
        return RemoteViews(context.packageName, layout.res).apply {
            val open = openAppIntent(content)
            setOnClickPendingIntent(R.id.widget_root, open)
            val coverBackground = artworkBackground.forCover(artwork)
            setWidgetArtworkBackground(coverBackground)
            setTextViewText(R.id.widget_title, content.title ?: context.getString(R.string.widget_nothing_playing))
            val artist = if (content.title == null) context.getString(R.string.widget_nothing_playing_hint) else content.artist
            setTextViewText(R.id.widget_artist, artist.orEmpty())
            val artistColor = if (coverBackground == null) R.color.widget_text_secondary else R.color.widget_text_secondary_on_artwork
            setTextColor(R.id.widget_artist, context.getColor(artistColor))
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
            if (layout.hasMoods) setWidgetMoodButtons(context, content.startingMood, content.playingMood, content.isPlaying)
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

/**
 * The row of mood radios shared by the widgets (`widget_mood_row`). Taps go to [NowPlayingWidget], so only one mood
 * radio starts at a time whichever widget was tapped. While one starts, the others dim and no button takes a tap.
 * The mood radio the player holds ([playing]) shows pause or play instead of its icon, and a tap on it toggles.
 */
internal fun RemoteViews.setWidgetMoodButtons(
    context: Context,
    starting: RadioMood?,
    playing: RadioMood? = null,
    isPlaying: Boolean = false,
) {
    val labels = context.resources.configuration.fontScale < WIDGET_LARGE_TEXT_SCALE
    RadioMood.entries.forEach { mood ->
        val button = mood.widgetButtonId()
        setOnClickPendingIntent(button, NowPlayingWidget.moodIntent(context, mood))
        setBoolean(button, "setEnabled", starting == null)
        setFloat(button, "setAlpha", if (starting == null || starting == mood) 1f else WIDGET_DIMMED_ALPHA)
        setViewVisibility(mood.widgetLabelId(), if (labels) View.VISIBLE else View.GONE)
        val title = context.getString(mood.widgetTitle())
        if (mood == playing) {
            setImageViewResource(mood.widgetIconId(), if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            val action = context.getString(if (isPlaying) R.string.controls_pause else R.string.controls_play)
            setContentDescription(button, context.getString(R.string.widget_album_action, action, title))
        } else {
            setImageViewResource(mood.widgetIconId(), mood.widgetIcon())
            setContentDescription(button, title)
        }
    }
}

internal const val WIDGET_DIMMED_ALPHA = 0.4f

/** From this font scale the mood labels no longer fit under their icons, so only the icons stay. */
private const val WIDGET_LARGE_TEXT_SCALE = 1.3f

internal class WidgetArtworkBackground {
    private var backgroundCover: Bitmap? = null
    private var background: Bitmap? = null

    fun forCover(cover: Bitmap?): Bitmap? {
        if (cover !== backgroundCover) {
            background = cover?.let(::softenedBackground)
            backgroundCover = cover
        }
        return background
    }

    private fun softenedBackground(cover: Bitmap): Bitmap {
        val sample = cover.scale(BACKGROUND_SAMPLE_PX, BACKGROUND_SAMPLE_PX, filter = true)
        return createBitmap(BACKGROUND_PX, BACKGROUND_PX).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(sample, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawColor(BACKGROUND_SCRIM)
        }
    }

    private companion object {
        const val BACKGROUND_SAMPLE_PX = 24
        const val BACKGROUND_PX = 64
        const val BACKGROUND_SCRIM = 0xAE000000.toInt()
    }
}

internal fun RemoteViews.setWidgetArtworkBackground(background: Bitmap?) {
    if (background == null) {
        setImageViewResource(R.id.widget_art_background, 0)
        setViewVisibility(R.id.widget_art_background, View.GONE)
    } else {
        setImageViewBitmap(R.id.widget_art_background, background)
        setViewVisibility(R.id.widget_art_background, View.VISIBLE)
    }
}

@IdRes
internal fun RadioMood.widgetButtonId(): Int =
    when (this) {
        RadioMood.NIGHT -> R.id.widget_mood_night
        RadioMood.ENERGETIC -> R.id.widget_mood_energetic
        RadioMood.WORKOUT -> R.id.widget_mood_workout
        RadioMood.CHILL -> R.id.widget_mood_chill
        RadioMood.FOCUS -> R.id.widget_mood_focus
    }

@IdRes
internal fun RadioMood.widgetIconId(): Int =
    when (this) {
        RadioMood.NIGHT -> R.id.widget_mood_night_icon
        RadioMood.ENERGETIC -> R.id.widget_mood_energetic_icon
        RadioMood.WORKOUT -> R.id.widget_mood_workout_icon
        RadioMood.CHILL -> R.id.widget_mood_chill_icon
        RadioMood.FOCUS -> R.id.widget_mood_focus_icon
    }

@DrawableRes
private fun RadioMood.widgetIcon(): Int =
    when (this) {
        RadioMood.NIGHT -> R.drawable.ic_widget_mood_night
        RadioMood.ENERGETIC -> R.drawable.ic_widget_mood_energetic
        RadioMood.WORKOUT -> R.drawable.ic_widget_mood_workout
        RadioMood.CHILL -> R.drawable.ic_widget_mood_chill
        RadioMood.FOCUS -> R.drawable.ic_widget_mood_focus
    }

@StringRes
private fun RadioMood.widgetTitle(): Int =
    when (this) {
        RadioMood.NIGHT -> R.string.mood_radio_queue_night
        RadioMood.ENERGETIC -> R.string.mood_radio_queue_energetic
        RadioMood.WORKOUT -> R.string.mood_radio_queue_workout
        RadioMood.CHILL -> R.string.mood_radio_queue_chill
        RadioMood.FOCUS -> R.string.mood_radio_queue_focus
    }

/** The mood radio a queue stored as [label] plays, if it is one. */
internal fun radioMoodOf(label: String?): RadioMood? = RadioMood.entries.firstOrNull { QueueCollection.forMood(it).storedLabel == label }

@IdRes
internal fun RadioMood.widgetLabelId(): Int =
    when (this) {
        RadioMood.NIGHT -> R.id.widget_mood_night_label
        RadioMood.ENERGETIC -> R.id.widget_mood_energetic_label
        RadioMood.WORKOUT -> R.id.widget_mood_workout_label
        RadioMood.CHILL -> R.id.widget_mood_chill_label
        RadioMood.FOCUS -> R.id.widget_mood_focus_label
    }

/** A small artwork thumbnail for the widget, from the shared image cache. Bounded so the widget stays light. */
internal class WidgetArtwork(
    private val context: Context,
    private val sources: SourceRepository,
    private val size: Int = 256,
) {
    suspend fun load(
        coverArtId: String,
        sourceId: Long? = null,
    ): Bitmap? {
        val ref = sources.coverArt(coverArtId, thumbnail = true) ?: return null
        if (sourceId != null && ref.cacheKey != coverArtKey(sourceId, coverArtId, thumbnail = true)) return null
        return load(ref)
    }

    /** Loads [ref] through the shared image cache, scaled down to the widget size. */
    suspend fun load(ref: CoverArtRef): Bitmap? {
        val request =
            ImageRequest
                .Builder(context)
                .data(ref.url)
                .memoryCacheKey("${ref.cacheKey}:widget:$size")
                .diskCacheKey(ref.cacheKey)
                .size(size)
                .allowHardware(false)
                .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        val longest = maxOf(bitmap.width, bitmap.height)
        return if (longest > size) {
            bitmap.scale((bitmap.width * size / longest).coerceAtLeast(1), (bitmap.height * size / longest).coerceAtLeast(1))
        } else {
            bitmap
        }
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
