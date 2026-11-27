package com.subtracks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.data.audiomuse.QueueBuilderMood
import com.subtracks.data.mix.GenreTaxonomy
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.data.mix.QueueBuilderSelection
import com.subtracks.data.mix.available
import com.subtracks.data.repo.SourceRepository
import com.subtracks.log.Log
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueBuilderOutcome
import com.subtracks.playback.QueueCollection
import com.subtracks.ui.discover.GenreTint
import com.subtracks.ui.discover.queueBuilderMessage
import com.subtracks.ui.discover.title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The home-screen Queue Builder: pick the library's top main genres, decade groups and moods, then build and play a
 * queue without opening the app. The picks are the app's own (one preference), so both always agree.
 */
class QueueBuilderWidget : AppWidgetProvider() {
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
        val tap = tapOf(intent)
        if (tap == null) {
            super.onReceive(context, intent)
            return
        }
        val koin = GlobalContext.getOrNull() ?: return
        val commands = koin.getOrNull<QueueBuilderWidgetCommands>() ?: return
        // A build can outlast the broadcast, after which Android freezes the idle app: build in a short foreground
        // service. If Android refuses one, fall back to building within the broadcast's budget.
        if ((tap.kind == QueueBuilderWidgetTap.Kind.Play || tap.kind == QueueBuilderWidgetTap.Kind.Resume) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            QueueBuilderBuildService.start(context, tap)
        ) {
            return
        }
        val pending = goAsync()
        val scope = koin.get<CoroutineScope>(named("playback"))
        val work = scope.launch { commands.handle(tap) }
        scope.launch { releaseWithin(work, BROADCAST_BUDGET_MS) { pending.finish() } }
    }

    private fun refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlobalContext.getOrNull()?.getOrNull<QueueBuilderWidgetUpdater>()?.refresh()
        }
    }

    internal companion object {
        /**
         * How long a tap may hold its broadcast. A widget tap is a foreground broadcast, which Android stops as not
         * responding after 10 s and kills the app with its playback; a build with moods can take longer on a phone.
         */
        const val BROADCAST_BUDGET_MS = 8_000L

        private const val ACTION_TAP = "com.subtracks.widget.QUEUE_BUILDER_TAP"
        private const val EXTRA_SOURCE = "widget.source"
        private const val EXTRA_KIND = "widget.kind"
        private const val EXTRA_VALUE = "widget.value"

        fun tapIntent(
            context: Context,
            tap: QueueBuilderWidgetTap,
        ): Intent =
            Intent(context, QueueBuilderWidget::class.java)
                .setAction(ACTION_TAP)
                .putExtra(EXTRA_SOURCE, tap.sourceId)
                .putExtra(EXTRA_KIND, tap.kind.name)
                .putExtra(EXTRA_VALUE, tap.value)

        /** The tapped control, or null for any other broadcast. */
        fun tapOf(intent: Intent): QueueBuilderWidgetTap? {
            if (intent.action != ACTION_TAP) return null
            val sourceId = intent.getLongExtra(EXTRA_SOURCE, -1).takeIf { it >= 0 } ?: return null
            val kind = QueueBuilderWidgetTap.Kind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: return null
            return QueueBuilderWidgetTap(sourceId, kind, intent.getStringExtra(EXTRA_VALUE).orEmpty())
        }
    }
}

/**
 * Waits for [work] at most [budgetMs], then [release]s the broadcast. Work still running keeps going in its own scope,
 * so a slow build finishes and plays instead of the app being stopped as not responding.
 */
internal suspend fun releaseWithin(
    work: Job,
    budgetMs: Long,
    release: () -> Unit,
) {
    try {
        withTimeoutOrNull(budgetMs) { work.join() }
    } finally {
        release()
    }
}

/** A tap on the widget: a genre (by main genre), a decade group or a mood (by name), Reset or Play. */
internal data class QueueBuilderWidgetTap(
    val sourceId: Long,
    val kind: Kind,
    val value: String = "",
) {
    enum class Kind { Genre, Decades, Mood, Reset, Play, Pause, Resume }
}

/** The widget's decade groups, newest first: each covers the library decades (first years) it [contains]. */
internal enum class DecadeGroup(
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
) {
    Recent(R.string.widget_queue_builder_2000s, R.string.widget_queue_builder_2000s_description),
    Nineties(R.string.widget_queue_builder_90s, R.string.widget_queue_builder_90s),
    Eighties(R.string.widget_queue_builder_80s, R.string.widget_queue_builder_80s),
    Seventies(R.string.widget_queue_builder_70s, R.string.widget_queue_builder_70s),
    Older(R.string.widget_queue_builder_older, R.string.widget_queue_builder_older_description),
    ;

    fun contains(decade: Int): Boolean =
        when (this) {
            Recent -> decade >= 2000
            Nineties -> decade == 1990
            Eighties -> decade == 1980
            Seventies -> decade == 1970
            Older -> decade < 1970
        }

    /** Picked when any of its decades is picked. */
    fun isPicked(selection: QueueBuilderSelection): Boolean = selection.decades.any(::contains)

    /** Picks every library decade in the group, or clears all of them when the group is picked. */
    fun toggle(
        selection: QueueBuilderSelection,
        libraryDecades: Set<Int>,
    ): QueueBuilderSelection =
        if (isPicked(selection)) {
            selection.copy(decades = selection.decades.filterNotTo(mutableSetOf(), ::contains))
        } else {
            selection.copy(decades = selection.decades + libraryDecades.filter(::contains))
        }
}

/** The library's biggest main genres for the widget (never Other), biggest first as in the app. */
internal fun QueueBuilderIndex.widgetGenres(limit: Int = WIDGET_GENRES): List<String> =
    columns.filter { it.main != GenreTaxonomy.OTHER }.take(limit).map { it.main }

internal const val WIDGET_GENRES = 5

/** Picked whole or through some of its sub-genres, as on the app's card. */
internal fun QueueBuilderSelection.isGenrePicked(
    main: String,
    index: QueueBuilderIndex,
): Boolean = main in mains || index.column(main)?.pills?.any { it.key in tags } == true

/** Picks [main] whole, or clears it together with its sub-genres when it is picked. */
internal fun QueueBuilderSelection.toggleGenre(
    main: String,
    index: QueueBuilderIndex,
): QueueBuilderSelection {
    val pills =
        index
            .column(main)
            ?.pills
            ?.mapTo(mutableSetOf()) { it.key }
            .orEmpty()
    return if (isGenrePicked(main, index)) {
        copy(mains = mains - main, tags = tags - pills)
    } else {
        copy(mains = mains + main, tags = tags - pills)
    }
}

/**
 * Edits the shared picks and builds queues from the widget. Edits are applied one at a time to the saved picks, as in
 * the app; taps from a widget still showing another server are ignored, and one build runs at a time.
 */
internal class QueueBuilderWidgetCommands(
    private val activeSourceId: suspend () -> Long?,
    private val picks: suspend (Long) -> QueueBuilderPicks,
    private val savePicks: suspend (Long, QueueBuilderPicks) -> Unit,
    private val index: suspend (Long) -> QueueBuilderIndex,
    private val moodsAvailable: suspend (Long) -> Boolean,
    private val play: suspend (QueueBuilderSelection, Int) -> QueueBuilderOutcome,
    private val showBuilding: (Boolean) -> Unit,
    private val showMessage: (messageRes: Int) -> Unit,
    /** Pauses the player if it is playing (the Pause shown while a Queue Builder queue plays). */
    private val pause: () -> Unit = {},
    /** Continues the paused queue; false when there is nothing to continue. */
    private val resume: suspend () -> Boolean = { false },
) {
    private val edits = Mutex()
    private val building = AtomicBoolean(false)

    suspend fun handle(tap: QueueBuilderWidgetTap) {
        if (activeSourceId() != tap.sourceId) return
        when (tap.kind) {
            QueueBuilderWidgetTap.Kind.Play -> {
                build(tap.sourceId)
            }

            QueueBuilderWidgetTap.Kind.Pause -> {
                pause()
            }

            // Shown while the paused queue still matches the picks; builds anew if it can no longer continue.
            QueueBuilderWidgetTap.Kind.Resume -> {
                if (!resume()) build(tap.sourceId)
            }

            QueueBuilderWidgetTap.Kind.Reset -> {
                edit(tap.sourceId) { QueueBuilderSelection() }
            }

            QueueBuilderWidgetTap.Kind.Genre -> {
                val library = index(tap.sourceId)
                edit(tap.sourceId) { it.toggleGenre(tap.value, library) }
            }

            QueueBuilderWidgetTap.Kind.Decades -> {
                val group = DecadeGroup.entries.firstOrNull { it.name == tap.value } ?: return
                val library = index(tap.sourceId)
                edit(tap.sourceId) { group.toggle(it, library.decades.keys) }
            }

            QueueBuilderWidgetTap.Kind.Mood -> {
                val mood = QueueBuilderMood.entries.firstOrNull { it.wireName == tap.value } ?: return
                edit(tap.sourceId) { it.copy(moods = if (mood in it.moods) it.moods - mood else it.moods + mood) }
            }
        }
    }

    private suspend fun edit(
        sourceId: Long,
        change: (QueueBuilderSelection) -> QueueBuilderSelection,
    ) = edits.withLock {
        val current = picks(sourceId)
        val updated = current.copy(selection = change(current.selection))
        if (updated != current) savePicks(sourceId, updated)
    }

    private suspend fun build(sourceId: Long) {
        if (!building.compareAndSet(false, true)) return
        showBuilding(true)
        try {
            val saved = picks(sourceId)
            val selection = index(sourceId).available(saved.selection, moodsAvailable(sourceId))
            queueBuilderMessage(play(selection, saved.length))?.let(showMessage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("widget", "queue builder widget build failed", e)
            showMessage(R.string.mix_failed)
        } finally {
            showBuilding(false)
            building.set(false)
        }
    }
}

internal data class QueueBuilderWidgetContent(
    val sourceId: Long?,
    val offline: Boolean = false,
    val index: QueueBuilderIndex = QueueBuilderIndex(),
    /** The saved picks still available in [index], as the app shows them. */
    val selection: QueueBuilderSelection = QueueBuilderSelection(),
    val moods: Boolean = false,
    val building: Boolean = false,
    /** The saved queue length, which is part of what a queue was built from. */
    val length: Int = QueueBuilderPicks.DEFAULT_LENGTH,
    /** What this server's last Queue Builder queue was built from, or null before any. */
    val builtPicks: QueueBuilderPicks? = null,
    /** The player holds this server's Queue Builder queue, playing or paused. */
    val queueHeld: Boolean = false,
    /** The player is playing this server's Queue Builder queue, so Play shows Pause. */
    val queuePlaying: Boolean = false,
    /** The cover of what plays for this server, for the softened background the other widgets show. */
    val backgroundCoverArt: String? = null,
) {
    val genres: List<String> get() = index.widgetGenres()

    val decadeGroups: List<DecadeGroup> get() = DecadeGroup.entries.filter { group -> index.decades.keys.any(group::contains) }

    val empty: Boolean get() = index.columns.isEmpty() && index.decades.isEmpty()

    /** The Queue Builder queue is paused. */
    val paused: Boolean get() = queueHeld && !queuePlaying

    /** Paused and the picks are still what it was built from, so Play continues it instead of building anew. */
    val resumable: Boolean get() = paused && builtPicks == QueueBuilderPicks(selection, length)
}

/**
 * The widget's state for the active server: the index (reloaded on a successful sync and when going offline or back),
 * the saved picks and whether moods can be picked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun queueBuilderWidgetContent(
    activeSourceId: Flow<Long?>,
    offline: Flow<Boolean>,
    librarySynced: Flow<Unit>,
    index: suspend (sourceId: Long, offline: Boolean) -> QueueBuilderIndex,
    picks: (Long) -> Flow<QueueBuilderPicks>,
    moodsAvailable: (Long) -> Flow<Boolean>,
    builtPicks: (Long) -> Flow<QueueBuilderPicks?> = { flowOf(null) },
): Flow<QueueBuilderWidgetContent> =
    activeSourceId
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            if (sourceId == null) {
                flowOf(QueueBuilderWidgetContent(null))
            } else {
                val loaded =
                    combine(offline.distinctUntilChanged(), librarySynced.onStart { emit(Unit) }) { off, _ -> off }
                        .mapLatest { off ->
                            val library =
                                try {
                                    index(sourceId, off)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w("widget", "queue builder widget index failed", e)
                                    QueueBuilderIndex()
                                }
                            off to library
                        }
                combine(
                    loaded,
                    picks(sourceId),
                    moodsAvailable(sourceId).distinctUntilChanged(),
                    builtPicks(sourceId),
                ) { (off, library), saved, moods, built ->
                    QueueBuilderWidgetContent(
                        sourceId,
                        off,
                        library,
                        library.available(saved.selection, moods),
                        moods,
                        length = saved.length,
                        builtPicks = built,
                    )
                }
            }
        }.distinctUntilChanged()

/**
 * What the player does for the widget: whether it plays a Queue Builder queue of this server (Pause instead of Play)
 * and, while the player holds something of this server (playing or paused), its cover for the background, as on the
 * other widgets.
 */
internal fun QueueBuilderWidgetContent.withPlayback(
    state: PlaybackState,
    queueLabel: String?,
): QueueBuilderWidgetContent {
    // Playing or paused, so pausing doesn't blank the background.
    val playingHere = sourceId != null && state.context?.sourceId == sourceId
    val held = state.radioLabel(sourceId, queueLabel) == QueueCollection.QueueBuilder.storedLabel
    return copy(
        queueHeld = held,
        queuePlaying = state.isPlaying && held,
        backgroundCoverArt = state.item?.takeIf { playingHere }?.coverArtId,
    )
}

/** "A", "A and B", "A, B and C". */
private fun joinNames(
    context: Context,
    names: List<String>,
): String =
    when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> context.getString(R.string.queue_builder_list_two, names.dropLast(1).joinToString(", "), names.last())
    }

/** The app card's summary line: every pick, including sub-genres the widget has no chip for. */
internal fun queueBuilderWidgetSummary(
    context: Context,
    content: QueueBuilderWidgetContent,
): String {
    val selection = content.selection
    val genres =
        content.index.columns.flatMap { column ->
            if (column.main in selection.mains) listOf(column.main) else column.pills.filter { it.key in selection.tags }.map { it.name }
        }
    val decades = selection.decades.sortedDescending().map { context.getString(R.string.queue_builder_decade, it) }
    val moods = QueueBuilderMood.entries.filter { it in selection.moods }.map { context.getString(it.title()) }
    return listOf(joinNames(context, genres), joinNames(context, decades), joinNames(context, moods))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { context.getString(R.string.queue_builder_anything) }
}

private val GENRE_CHIPS =
    intArrayOf(R.id.widget_qb_genre_0, R.id.widget_qb_genre_1, R.id.widget_qb_genre_2, R.id.widget_qb_genre_3, R.id.widget_qb_genre_4)
private val DECADE_CHIPS =
    intArrayOf(R.id.widget_qb_decade_0, R.id.widget_qb_decade_1, R.id.widget_qb_decade_2, R.id.widget_qb_decade_3, R.id.widget_qb_decade_4)
private val MOOD_CHIPS =
    intArrayOf(
        R.id.widget_qb_mood_0,
        R.id.widget_qb_mood_1,
        R.id.widget_qb_mood_2,
        R.id.widget_qb_mood_3,
        R.id.widget_qb_mood_4,
        R.id.widget_qb_mood_5,
    )

private const val CHIP_NEUTRAL = 0x26FFFFFF

/** Request codes: one per control, so each keeps its own extras. */
private const val REQUEST_PLAY = 1
private const val REQUEST_RESET = 2
private const val REQUEST_PAUSE = 3
private const val REQUEST_RESUME = 4
private const val REQUEST_GENRE = 100
private const val REQUEST_DECADES = 200
private const val REQUEST_MOOD = 300

/** The whole widget. Picked chips are filled: genres in their app colour, decades and moods in the accent. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun queueBuilderViews(
    context: Context,
    content: QueueBuilderWidgetContent,
    background: Bitmap? = null,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_queue_builder).apply {
        setWidgetArtworkBackground(background)
        setOnClickPendingIntent(R.id.widget_qb_header, openApp(context))
        val sourceId = content.sourceId
        val showPicks = sourceId != null && !content.empty
        setViewVisibility(R.id.widget_qb_empty, if (showPicks) View.GONE else View.VISIBLE)
        setTextViewText(
            R.id.widget_qb_empty,
            context.getString(
                when {
                    sourceId == null -> R.string.widget_recent_albums_no_server
                    content.offline -> R.string.queue_builder_nothing_offline
                    else -> R.string.queue_builder_nothing
                },
            ),
        )
        val sections = intArrayOf(R.id.widget_qb_genres, R.id.widget_qb_decades, R.id.widget_qb_moods, R.id.widget_qb_summary)
        sections.forEach { setViewVisibility(it, if (showPicks) View.VISIBLE else View.GONE) }
        setViewVisibility(R.id.widget_qb_offline, if (showPicks && content.offline) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_qb_reset, if (showPicks && !content.selection.isEmpty) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_qb_play_frame, if (showPicks) View.VISIBLE else View.GONE)
        if (sourceId == null || !showPicks) return@apply

        // While a queue builds a spinner takes Play's place; taps then would be ignored anyway.
        setViewVisibility(R.id.widget_qb_play, if (content.building) View.INVISIBLE else View.VISIBLE)
        setViewVisibility(R.id.widget_qb_building, if (content.building) View.VISIBLE else View.GONE)
        // While the Queue Builder queue plays, Play becomes Pause; otherwise Play builds a new queue from the picks.
        if (content.queuePlaying) {
            setImageViewResource(R.id.widget_qb_play, R.drawable.ic_widget_pause)
            setContentDescription(R.id.widget_qb_play, context.getString(R.string.controls_pause))
            setOnClickPendingIntent(
                R.id.widget_qb_play,
                tap(context, REQUEST_PAUSE, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Pause)),
            )
        } else if (content.resumable) {
            setImageViewResource(R.id.widget_qb_play, R.drawable.ic_widget_play)
            setContentDescription(R.id.widget_qb_play, context.getString(R.string.controls_play))
            setOnClickPendingIntent(
                R.id.widget_qb_play,
                tap(context, REQUEST_RESUME, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Resume)),
            )
        } else {
            setImageViewResource(R.id.widget_qb_play, R.drawable.ic_widget_play)
            setContentDescription(R.id.widget_qb_play, context.getString(R.string.queue_builder_play_queue))
            setOnClickPendingIntent(
                R.id.widget_qb_play,
                tap(context, REQUEST_PLAY, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Play)),
            )
        }
        // While the queue is paused, say what Play will do: continue it, or build anew from changed picks.
        setViewVisibility(R.id.widget_qb_status, if (content.paused) View.VISIBLE else View.GONE)
        if (content.paused) {
            setTextViewText(
                R.id.widget_qb_status,
                context.getString(
                    if (content.resumable) R.string.widget_queue_builder_paused else R.string.widget_queue_builder_picks_changed,
                ),
            )
        }
        setOnClickPendingIntent(
            R.id.widget_qb_reset,
            tap(context, REQUEST_RESET, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Reset)),
        )

        val genres = content.genres
        setViewVisibility(R.id.widget_qb_genres, if (genres.isEmpty()) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_qb_genres_second, if (genres.size > 3) View.VISIBLE else View.GONE)
        GENRE_CHIPS.forEachIndexed { slot, id ->
            val main = genres.getOrNull(slot)
            setViewVisibility(id, if (main == null) View.GONE else View.VISIBLE)
            if (main == null) return@forEachIndexed
            val picked = content.selection.isGenrePicked(main, content.index)
            val tint = GenreTint.of(main)?.toArgb()
            chip(context, id, main, main, picked, pickedColor = tint)
            setOnClickPendingIntent(
                id,
                tap(context, REQUEST_GENRE + slot, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Genre, main)),
            )
        }

        val groups = content.decadeGroups
        setViewVisibility(R.id.widget_qb_decades, if (groups.isEmpty()) View.GONE else View.VISIBLE)
        DecadeGroup.entries.forEachIndexed { slot, group ->
            val id = DECADE_CHIPS[slot]
            setViewVisibility(id, if (group in groups) View.VISIBLE else View.GONE)
            if (group !in groups) return@forEachIndexed
            chip(context, id, context.getString(group.label), context.getString(group.description), group.isPicked(content.selection))
            setOnClickPendingIntent(
                id,
                tap(context, REQUEST_DECADES + slot, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Decades, group.name)),
            )
        }

        setViewVisibility(R.id.widget_qb_moods, if (content.moods) View.VISIBLE else View.GONE)
        if (content.moods) {
            QueueBuilderMood.entries.forEachIndexed { slot, mood ->
                val id = MOOD_CHIPS.getOrNull(slot) ?: return@forEachIndexed
                val name = context.getString(mood.title())
                chip(context, id, name, name, mood in content.selection.moods)
                setOnClickPendingIntent(
                    id,
                    tap(context, REQUEST_MOOD + slot, QueueBuilderWidgetTap(sourceId, QueueBuilderWidgetTap.Kind.Mood, mood.wireName)),
                )
            }
        }

        setTextViewText(R.id.widget_qb_summary, queueBuilderWidgetSummary(context, content))
    }

/** A chip's text, state for screen readers and colours. [pickedColor] is a genre's colour (white text) or the accent. */
@RequiresApi(Build.VERSION_CODES.S)
private fun RemoteViews.chip(
    context: Context,
    id: Int,
    label: String,
    description: String,
    picked: Boolean,
    pickedColor: Int? = null,
) {
    setTextViewText(id, label)
    val state = context.getString(if (picked) R.string.widget_chip_picked else R.string.widget_chip_not_picked)
    setContentDescription(id, context.getString(R.string.widget_album_action, description, state))
    val background =
        when {
            !picked -> CHIP_NEUTRAL
            pickedColor != null -> pickedColor
            else -> context.getColor(R.color.widget_accent)
        }
    setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(background))
    val text = if (picked && pickedColor == null) context.getColor(R.color.widget_ink) else context.getColor(R.color.widget_text_primary)
    setTextColor(id, text)
}

private fun tap(
    context: Context,
    requestCode: Int,
    tap: QueueBuilderWidgetTap,
): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        requestCode,
        QueueBuilderWidget.tapIntent(context, tap),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

@RequiresApi(Build.VERSION_CODES.S)
internal class QueueBuilderWidgetUpdater(
    private val context: Context,
    private val sources: SourceRepository,
    private val content: Flow<QueueBuilderWidgetContent>,
    private val scope: CoroutineScope,
    private val playback: Flow<PlaybackState> = flowOf(PlaybackState()),
    private val queueLabel: Flow<String?> = flowOf(null),
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, QueueBuilderWidget::class.java)
    private val revision = MutableStateFlow(0L)
    private val building = MutableStateFlow(false)
    private val artwork = WidgetArtwork(context, sources, size = 96)
    private val artworkBackground = WidgetArtworkBackground()

    @Volatile
    private var latest: QueueBuilderWidgetContent? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            revision
                .flatMapLatest {
                    if (manager.getAppWidgetIds(component).isEmpty()) {
                        emptyFlow()
                    } else {
                        combine(content, building, playback, queueLabel) { shown, busy, state, label ->
                            shown.copy(building = busy).withPlayback(state, label)
                        }.distinctUntilChanged()
                    }
                }.collectLatest { shown ->
                    latest = shown
                    val sourceId = shown.sourceId
                    val cover = if (sourceId != null) shown.backgroundCoverArt?.let { artwork.load(it, sourceId) } else null
                    val views = queueBuilderViews(context, shown, artworkBackground.forCover(cover))
                    if (sources.activeSourceIdOnce() == sourceId) manager.updateAppWidget(component, views)
                }
        }
    }

    fun refresh() {
        revision.update { it + 1 }
    }

    fun showBuilding(busy: Boolean) {
        building.value = busy
    }

    /** The index the widget shows for [sourceId], so a tap acts on what is on screen without reading it again. */
    fun shownIndex(sourceId: Long): QueueBuilderIndex? = latest?.takeIf { it.sourceId == sourceId && !it.empty }?.index
}
