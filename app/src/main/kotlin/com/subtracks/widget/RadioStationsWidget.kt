package com.subtracks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.subtracks.MainActivity
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.model.Artist
import com.subtracks.data.repo.SourceRepository
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueCollection
import com.subtracks.ui.components.ArtistArt
import com.subtracks.ui.components.artistArt
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The home-screen radio widget: Discover's mood radios on top and its artist radio picks as a grid below. Mood taps
 * go through [NowPlayingWidget]; artist taps come back here and start through [RadioStationsWidgetCommands].
 */
class RadioStationsWidget : AppWidgetProvider() {
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
        val station = stationOf(intent)
        if (station == null) {
            super.onReceive(context, intent)
            return
        }
        val koin = GlobalContext.getOrNull() ?: return
        val commands = koin.getOrNull<RadioStationsWidgetCommands>() ?: return
        val pending = goAsync()
        koin.get<CoroutineScope>(named("playback")).launch {
            try {
                commands.startArtistRadio(station.sourceId, station.artistId, station.name)
            } finally {
                pending.finish()
            }
        }
    }

    private fun refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlobalContext.getOrNull()?.getOrNull<RadioStationsWidgetUpdater>()?.refresh()
        }
    }

    internal companion object {
        private const val ACTION_PLAY_ARTIST_RADIO = "com.subtracks.widget.PLAY_ARTIST_RADIO"
        private const val EXTRA_SOURCE = "widget.source"
        private const val EXTRA_ARTIST = "widget.artist"
        private const val EXTRA_NAME = "widget.artistName"

        fun playTemplate(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, RadioStationsWidget::class.java).setAction(ACTION_PLAY_ARTIST_RADIO),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun playArtistRadio(
            sourceId: Long,
            artistId: String,
            name: String,
        ): Intent =
            Intent()
                .putExtra(EXTRA_SOURCE, sourceId)
                .putExtra(EXTRA_ARTIST, artistId)
                .putExtra(EXTRA_NAME, name)

        /** The tapped station, or null for any other broadcast. */
        fun stationOf(intent: Intent): WidgetStationTap? {
            if (intent.action != ACTION_PLAY_ARTIST_RADIO) return null
            val sourceId = intent.getLongExtra(EXTRA_SOURCE, -1).takeIf { it >= 0 } ?: return null
            val artistId = intent.getStringExtra(EXTRA_ARTIST)?.takeIf(String::isNotBlank) ?: return null
            return WidgetStationTap(sourceId, artistId, intent.getStringExtra(EXTRA_NAME).orEmpty())
        }
    }
}

internal data class WidgetStationTap(
    val sourceId: Long,
    val artistId: String,
    val name: String,
)

/**
 * Starts an artist radio from the widget, one at a time; taps while one starts, or from a widget still showing another
 * server, are ignored. A tap on the radio the player already holds pauses or resumes it. Nothing opens: a short
 * message says why when nothing could start.
 */
internal class RadioStationsWidgetCommands(
    private val activeSourceId: suspend () -> Long?,
    private val playArtistRadio: suspend (sourceId: Long, artistId: String) -> Boolean,
    private val showStartingArtist: (String?) -> Unit,
    private val showMessage: (messageRes: Int) -> Unit,
    /** The stored queue label of what the player holds for the server, or null when it holds nothing of it. */
    private val currentRadio: suspend (sourceId: Long) -> String? = { null },
    private val togglePlayPause: () -> Unit = {},
) {
    private val starting = AtomicBoolean(false)

    suspend fun startArtistRadio(
        sourceId: Long,
        artistId: String,
        name: String,
    ) {
        if (activeSourceId() != sourceId) return
        if (name.isNotBlank() && currentRadio(sourceId) == QueueCollection.artistRadioLabel(name)) {
            togglePlayPause()
            return
        }
        if (!starting.compareAndSet(false, true)) return
        showStartingArtist(artistId)
        try {
            if (!playArtistRadio(sourceId, artistId)) showMessage(R.string.mix_empty)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            showMessage(R.string.mix_failed)
        } finally {
            showStartingArtist(null)
            starting.set(false)
        }
    }
}

internal data class RadioStationsContent(
    val sourceId: Long?,
    val offline: Boolean = false,
    /** Mood radios can start: AudioMuse is set up for the active server and the app is online. */
    val moods: Boolean = false,
    val artists: List<Artist> = emptyList(),
    val startingMood: RadioMood? = null,
    val startingArtist: String? = null,
    val backgroundCoverArt: String? = null,
    /** The stored queue label of the radio the player holds for this server, shown with play or pause. */
    val radio: String? = null,
    val isPlaying: Boolean = false,
)

/** The queue label of what [this] state holds for [sourceId], or null when it holds nothing of that server. */
internal fun PlaybackState.radioLabel(
    sourceId: Long?,
    queueLabel: String?,
): String? = queueLabel?.takeIf { item != null && sourceId != null && context?.sourceId == sourceId }

/**
 * Discover's stations for the active server, from the mirror. Unlike Discover, artist radio stays offline, where it
 * plays a cached mix or downloaded songs; the mood radios, which need the server, go away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun radioStationsContent(
    activeSourceId: Flow<Long?>,
    offline: Flow<Boolean>,
    moodsAvailable: Flow<Boolean>,
    stationArtists: (Long) -> Flow<List<Artist>>,
): Flow<RadioStationsContent> =
    activeSourceId
        .distinctUntilChanged()
        .flatMapLatest { sourceId ->
            if (sourceId == null) {
                flowOf(RadioStationsContent(null))
            } else {
                combine(stationArtists(sourceId), offline, moodsAvailable) { artists, off, moods ->
                    RadioStationsContent(sourceId, offline = off, moods = moods && !off, artists = artists)
                }
            }
        }.distinctUntilChanged()

internal fun RadioStationsContent.withTaps(
    state: PlaybackState,
    startingMood: RadioMood?,
    startingArtist: String?,
    queueLabel: String? = null,
): RadioStationsContent =
    copy(
        radio = state.radioLabel(sourceId, queueLabel),
        isPlaying = state.isPlaying,
        startingMood = startingMood.takeIf { moods },
        startingArtist = startingArtist?.takeIf { id -> artists.any { it.id == id } },
        backgroundCoverArt =
            state.item
                // Playing or paused, so pausing doesn't blank the background.
                ?.takeIf { sourceId != null && state.context?.sourceId == sourceId }
                ?.coverArtId,
    )

/** Everything but the grid's items: background, mood row or offline note, empty text and header. */
internal fun radioStationsFrame(
    context: Context,
    content: RadioStationsContent,
    background: Bitmap?,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_radio_stations).apply {
        setWidgetArtworkBackground(background)
        setViewVisibility(R.id.widget_moods, if (content.moods) View.VISIBLE else View.GONE)
        if (content.moods) setWidgetMoodButtons(context, content.startingMood, radioMoodOf(content.radio), content.isPlaying)
        setViewVisibility(R.id.widget_radio_offline, if (content.sourceId != null && content.offline) View.VISIBLE else View.GONE)
        setEmptyView(R.id.widget_radio_grid, R.id.widget_radio_empty)
        setTextViewText(
            R.id.widget_radio_empty,
            context.getString(if (content.sourceId == null) R.string.widget_recent_albums_no_server else R.string.discover_stations_empty),
        )
        setViewVisibility(R.id.widget_radio_empty, if (content.artists.isEmpty()) View.VISIBLE else View.GONE)
        setOnClickPendingIntent(
            R.id.widget_radio_header,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

/**
 * One artist radio tile. While a station starts, the other tiles dim. The radio the player holds ([radio]) shows pause
 * while it plays and play while paused, as the album widgets do.
 */
internal fun radioStationTile(
    context: Context,
    sourceId: Long,
    artist: Artist,
    startingArtist: String?,
    art: Bitmap?,
    radio: String? = null,
    isPlaying: Boolean = false,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_radio_station).apply {
        setTextViewText(R.id.widget_station_name, artist.name)
        val title = context.getString(R.string.artist_radio_queue, artist.name)
        val current = radio != null && radio == QueueCollection.artistRadioLabel(artist.name)
        setViewVisibility(R.id.widget_station_playback, if (current) View.VISIBLE else View.GONE)
        if (current) {
            setImageViewResource(R.id.widget_station_playback, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            val action = context.getString(if (isPlaying) R.string.controls_pause else R.string.controls_play)
            setContentDescription(R.id.widget_station_cell, context.getString(R.string.widget_album_action, action, title))
        } else {
            setContentDescription(R.id.widget_station_cell, title)
        }
        val dimmed = startingArtist != null && startingArtist != artist.id
        setFloat(R.id.widget_station_cell, "setAlpha", if (dimmed) WIDGET_DIMMED_ALPHA else 1f)
        if (art != null) setImageViewBitmap(R.id.widget_station_art, art)
        setOnClickFillInIntent(R.id.widget_station_cell, RadioStationsWidget.playArtistRadio(sourceId, artist.id, artist.name))
    }

@RequiresApi(Build.VERSION_CODES.S)
internal class RadioStationsWidgetUpdater(
    private val context: Context,
    private val sources: SourceRepository,
    private val stationArtists: (Long) -> Flow<List<Artist>>,
    private val offline: Flow<Boolean>,
    private val moodsAvailable: Flow<Boolean>,
    private val startingMood: Flow<RadioMood?>,
    private val playback: Flow<PlaybackState>,
    private val queueLabel: Flow<String?>,
    private val scope: CoroutineScope,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, RadioStationsWidget::class.java)
    private val revision = MutableStateFlow(0L)
    private val startingArtist = MutableStateFlow<String?>(null)
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
                        combine(
                            radioStationsContent(sources.activeSourceId(), offline, moodsAvailable, stationArtists),
                            playback,
                            startingMood,
                            startingArtist,
                            queueLabel,
                        ) { content, state, mood, artist, label -> content.withTaps(state, mood, artist, label) }
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

    fun showStartingArtist(artistId: String?) {
        startingArtist.value = artistId
    }

    private suspend fun views(content: RadioStationsContent): RemoteViews {
        val rows = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
        val sourceId = content.sourceId
        if (sourceId != null) {
            for ((index, artist) in content.artists.withIndex()) {
                val tile =
                    radioStationTile(
                        context,
                        sourceId,
                        artist,
                        content.startingArtist,
                        artistBitmap(artist),
                        content.radio,
                        content.isPlaying,
                    )
                rows.addItem(index.toLong(), tile)
            }
        }
        val cover = if (sourceId != null) content.backgroundCoverArt?.let { artwork.load(it, sourceId) } else null
        return radioStationsFrame(context, content, artworkBackground.forCover(cover)).apply {
            setPendingIntentTemplate(R.id.widget_radio_grid, RadioStationsWidget.playTemplate(context))
            setRemoteAdapter(R.id.widget_radio_grid, rows.build())
        }
    }

    /** The artist's picture as the app shows it, falling back to its album covers when an image doesn't load. */
    private suspend fun artistBitmap(artist: Artist): Bitmap? {
        var art: ArtistArt? = sources.artistArt(artist, forIcon = true)
        while (art != null) {
            for (ref in listOfNotNull(art.image, art.covers.firstOrNull())) {
                val bitmap =
                    try {
                        artwork.load(ref)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (bitmap != null) return bitmap
            }
            art = art.fallback
        }
        return null
    }
}
