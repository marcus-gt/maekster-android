package com.subtracks.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.data.model.Artist
import com.subtracks.data.model.QueueKind
import com.subtracks.data.model.Source
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueCollection
import com.subtracks.playback.QueueContext
import com.subtracks.playback.QueueItem
import com.subtracks.ui.ViewModelRepositoryFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class RadioStationsWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = ViewModelRepositoryFixture()
    private val db = fixture.db

    @After
    fun tearDown() = fixture.close()

    @Test
    fun showsDiscoversArtistRadioPicksWithMoodsOnlineAndKeepsTheArtistsOffline() =
        runTest {
            val sourceId = source()
            db.libraryDao().upsertArtists(
                listOf(
                    Artist(sourceId, "old", "Old", 1, null, played = 100),
                    Artist(sourceId, "recent", "Recent", 1, null, played = 300),
                    Artist(sourceId, "star", "Star", 1, starred = 1),
                ),
            )
            val discover =
                db
                    .mixDao()
                    .stationArtists(sourceId)
                    .first()
                    .map { it.id }
            val offline = MutableStateFlow(false)
            val widget = radioStationsContent(flowOf(sourceId), offline, flowOf(true), db.mixDao()::stationArtists)

            val online = widget.first()
            assertEquals(listOf("star", "recent", "old"), online.artists.map { it.id })
            assertEquals(discover, online.artists.map { it.id })
            assertTrue(online.moods)
            assertFalse(online.offline)

            offline.value = true
            val away = widget.first { it.offline }
            assertEquals(discover, away.artists.map { it.id })
            assertFalse(away.moods)
        }

    @Test
    fun withoutAServerThereIsNothingToStart() =
        runTest {
            val content = radioStationsContent(flowOf(null), flowOf(false), flowOf(true)) { error("no server, no query") }.first()

            assertEquals(RadioStationsContent(null), content)
        }

    @Test
    fun moodRowShowsWithMoodsAndGivesWayToAnOfflineNote() {
        val artists = listOf(Artist(1, "a", "A", 1, null))
        val online = inflate(RadioStationsContent(1, moods = true, artists = artists))
        assertEquals(View.VISIBLE, online.findViewById<View>(R.id.widget_moods).visibility)
        assertEquals(View.GONE, online.findViewById<View>(R.id.widget_radio_offline).visibility)
        RadioMood.entries.forEach { assertTrue(online.findViewById<View>(it.widgetButtonId()).isEnabled) }
        assertEquals(View.GONE, online.findViewById<View>(R.id.widget_radio_empty).visibility)

        val noAudioMuse = inflate(RadioStationsContent(1, artists = artists))
        assertEquals(View.GONE, noAudioMuse.findViewById<View>(R.id.widget_moods).visibility)
        assertEquals(View.GONE, noAudioMuse.findViewById<View>(R.id.widget_radio_offline).visibility)

        val offline = inflate(RadioStationsContent(1, offline = true, artists = artists))
        assertEquals(View.GONE, offline.findViewById<View>(R.id.widget_moods).visibility)
        assertEquals(View.VISIBLE, offline.findViewById<View>(R.id.widget_radio_offline).visibility)
    }

    @Test
    fun emptyStatesExplainWhatIsMissing() {
        val noServer = inflate(RadioStationsContent(null))
        assertEquals(View.VISIBLE, noServer.findViewById<View>(R.id.widget_radio_empty).visibility)
        assertEquals(
            context.getString(R.string.widget_recent_albums_no_server),
            noServer.findViewById<TextView>(R.id.widget_radio_empty).text,
        )
        assertEquals(View.GONE, noServer.findViewById<View>(R.id.widget_radio_offline).visibility)

        val noArtists = inflate(RadioStationsContent(1, moods = true))
        assertEquals(context.getString(R.string.discover_stations_empty), noArtists.findViewById<TextView>(R.id.widget_radio_empty).text)
    }

    @Test
    fun whileAMoodStartsTheOtherMoodsDim() {
        val view = inflate(RadioStationsContent(1, moods = true, startingMood = RadioMood.FOCUS))

        RadioMood.entries.forEach { mood ->
            val button = view.findViewById<View>(mood.widgetButtonId())
            assertFalse(mood.name, button.isEnabled)
            assertEquals(mood.name, if (mood == RadioMood.FOCUS) 1f else WIDGET_DIMMED_ALPHA, button.alpha)
        }
    }

    @Test
    fun tilesNameTheirStationAndDimWhileAnotherStarts() {
        val artist = Artist(1, "a", "Björk", 1, null)
        val tile = radioStationTile(context, 1, artist, startingArtist = null, art = null).apply(context, FrameLayout(context))
        assertEquals("Björk", tile.findViewById<TextView>(R.id.widget_station_name).text)
        assertEquals("Björk radio", tile.findViewById<View>(R.id.widget_station_cell).contentDescription)
        assertEquals(1f, tile.findViewById<View>(R.id.widget_station_cell).alpha)

        val dimmed = radioStationTile(context, 1, artist, startingArtist = "b", art = null).apply(context, FrameLayout(context))
        assertEquals(WIDGET_DIMMED_ALPHA, dimmed.findViewById<View>(R.id.widget_station_cell).alpha)
    }

    @Test
    fun theTileOfTheRadioThePlayerHoldsShowsPauseOrPlay() {
        val artist = Artist(1, "a", "Björk", 1, null)

        fun tile(
            radio: String?,
            isPlaying: Boolean,
        ) = radioStationTile(context, 1, artist, null, null, radio, isPlaying).apply(context, FrameLayout(context))

        val playing = tile(QueueCollection.artistRadioLabel("Björk"), isPlaying = true)
        assertEquals(View.VISIBLE, playing.findViewById<View>(R.id.widget_station_playback).visibility)
        assertEquals("Pause: Björk radio", playing.findViewById<View>(R.id.widget_station_cell).contentDescription)

        val paused = tile(QueueCollection.artistRadioLabel("Björk"), isPlaying = false)
        assertEquals(View.VISIBLE, paused.findViewById<View>(R.id.widget_station_playback).visibility)
        assertEquals("Play: Björk radio", paused.findViewById<View>(R.id.widget_station_cell).contentDescription)

        listOf(QueueCollection.artistRadioLabel("Other"), QueueCollection.forMood(RadioMood.CHILL).storedLabel, null).forEach { radio ->
            val other = tile(radio, isPlaying = true)
            assertEquals(View.GONE, other.findViewById<View>(R.id.widget_station_playback).visibility)
            assertEquals("Björk radio", other.findViewById<View>(R.id.widget_station_cell).contentDescription)
        }
    }

    @Test
    fun theMoodRadioThePlayerHoldsShowsPauseOrPlayOnItsButton() {
        val chill = QueueCollection.forMood(RadioMood.CHILL).storedLabel
        val playing = inflate(RadioStationsContent(1, moods = true, radio = chill, isPlaying = true))
        assertEquals("Pause: Chill radio", playing.findViewById<View>(RadioMood.CHILL.widgetButtonId()).contentDescription)
        assertEquals("Night radio", playing.findViewById<View>(RadioMood.NIGHT.widgetButtonId()).contentDescription)

        val paused = inflate(RadioStationsContent(1, moods = true, radio = chill, isPlaying = false))
        assertEquals("Play: Chill radio", paused.findViewById<View>(RadioMood.CHILL.widgetButtonId()).contentDescription)

        val artistRadio = inflate(RadioStationsContent(1, moods = true, radio = QueueCollection.artistRadioLabel("A"), isPlaying = true))
        assertEquals("Chill radio", artistRadio.findViewById<View>(RadioMood.CHILL.widgetButtonId()).contentDescription)
    }

    @Test
    fun aMoodButtonsPlayAndPauseAreWhiteLikeTheMoodIcons() {
        val chill = QueueCollection.forMood(RadioMood.CHILL).storedLabel

        fun icon(isPlaying: Boolean): Int {
            val view = inflate(RadioStationsContent(1, moods = true, radio = chill, isPlaying = isPlaying))
            val image = view.findViewById<android.widget.ImageView>(RadioMood.CHILL.widgetIconId())
            return Shadows.shadowOf(image.drawable).createdFromResId
        }

        assertEquals(R.drawable.ic_widget_mood_pause, icon(isPlaying = true))
        assertEquals(R.drawable.ic_widget_mood_play, icon(isPlaying = false))
    }

    @Test
    fun tapsCarryTheSourceAndArtistBack() {
        val template = Shadows.shadowOf(RadioStationsWidget.playTemplate(context)).savedIntent
        assertEquals(RadioStationsWidget::class.java.name, template.component?.className)
        val tap = RadioStationsWidget.playArtistRadio(7, "artist", "Name").setAction(template.action)

        assertEquals(WidgetStationTap(7, "artist", "Name"), RadioStationsWidget.stationOf(tap))
        assertNull(RadioStationsWidget.stationOf(RadioStationsWidget.playArtistRadio(7, "artist", "Name")))
        assertNull(RadioStationsWidget.stationOf(RadioStationsWidget.playArtistRadio(7, " ", "Name").setAction(template.action)))
    }

    @Test
    fun theBackgroundFollowsWhatPlaysOnThisServerAndTapsOnlyMarkShownStations() {
        val content = RadioStationsContent(1, moods = false, artists = listOf(Artist(1, "a", "A", 1, null)))
        val item = QueueItem(id = "s", title = "Song", artist = null, album = null, coverArtId = "cover")
        val playing = PlaybackState(item = item, isPlaying = true, context = QueueContext(QueueKind.Song, sourceId = 1, refId = "s"))

        val shown = content.withTaps(playing, RadioMood.CHILL, "a")
        assertEquals("cover", shown.backgroundCoverArt)
        assertNull(shown.startingMood)
        assertEquals("a", shown.startingArtist)

        // Paused keeps the cover: pausing doesn't blank the background.
        assertEquals("cover", content.withTaps(playing.copy(isPlaying = false), null, null).backgroundCoverArt)
        assertNull(
            content
                .withTaps(
                    playing.copy(context = QueueContext(QueueKind.Song, sourceId = 2, refId = "s")),
                    null,
                    null,
                ).backgroundCoverArt,
        )
        assertNull(content.withTaps(playing, null, "gone").startingArtist)

        val label = QueueCollection.artistRadioLabel("A")
        assertEquals(label, content.withTaps(playing, null, null, label).radio)
        assertNull(content.withTaps(playing.copy(item = null), null, null, label).radio)
        assertNull(
            content
                .withTaps(
                    playing.copy(context = QueueContext(QueueKind.Song, sourceId = 2, refId = "s")),
                    null,
                    null,
                    label,
                ).radio,
        )
    }

    private fun inflate(content: RadioStationsContent): View =
        radioStationsFrame(context, content, null).apply(context, FrameLayout(context))

    private suspend fun source(): Long =
        db.sourcesDao().insertSource(Source(name = "Navidrome", address = "http://localhost", isActive = true, createdAt = 0))
}
