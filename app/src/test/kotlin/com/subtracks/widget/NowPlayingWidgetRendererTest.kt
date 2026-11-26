package com.subtracks.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class NowPlayingWidgetRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = NowPlayingWidgetRenderer(context)

    private fun inflate(
        layout: NowPlayingWidgetLayout,
        content: NowPlayingWidgetContent,
    ): View = renderer.views(layout, content, null).apply(context, FrameLayout(context))

    private val playing = NowPlayingWidgetContent("Song", "Artist", null, isPlaying = true, live = true, hasNext = true)

    @Test
    fun eachHomeScreenSizeGetsALayoutThatFitsIt() {
        // Pixel 9 cells, measured from the owner's screenshots: 2x1, 3x1, 4x1 and 4x2.
        assertEquals(NowPlayingWidgetLayout.Small, NowPlayingWidgetLayout.forSize(180f, 102f))
        assertEquals(NowPlayingWidgetLayout.Small, NowPlayingWidgetLayout.forSize(277f, 102f))
        assertEquals(NowPlayingWidgetLayout.Row, NowPlayingWidgetLayout.forSize(374f, 102f))
        assertEquals(NowPlayingWidgetLayout.Moods, NowPlayingWidgetLayout.forSize(374f, 216f))
        assertEquals(NowPlayingWidgetLayout.Small, NowPlayingWidgetLayout.forSize(60f, 30f))
        // A shorter two-row cell on another launcher keeps the player without the mood row.
        assertEquals(NowPlayingWidgetLayout.Tall, NowPlayingWidgetLayout.forSize(300f, 180f))
    }

    @Test
    fun theFourByTwoWidgetHasARowOfAllFiveMoodsBelowThePlayer() {
        val view = inflate(NowPlayingWidgetLayout.Moods, playing.copy(moods = true))

        assertEquals("Song", view.findViewById<TextView>(R.id.widget_title).text)
        assertTrue(view.findViewById<View>(R.id.widget_next).isEnabled)
        RadioMood.entries.forEach { mood ->
            val button = view.findViewById<View>(mood.widgetButtonId())
            assertTrue(button.isEnabled)
            assertEquals(1f, button.alpha)
            assertEquals(View.VISIBLE, view.findViewById<View>(mood.widgetLabelId()).visibility)
        }
        assertEquals(
            listOf("Night radio", "Energetic radio", "Workout radio", "Chill radio", "Focus radio"),
            RadioMood.entries.map { view.findViewById<View>(it.widgetButtonId()).contentDescription.toString() },
        )
    }

    @Test
    fun withoutMoodsTheFourByTwoWidgetIsThePlainTallPlayer() {
        val view = inflate(NowPlayingWidgetLayout.Moods, playing)

        assertNull(view.findViewById<View>(R.id.widget_moods))
        val tallView = inflate(NowPlayingWidgetLayout.Tall, playing)
        assertEquals(
            tallView.findViewById<View>(R.id.widget_art).layoutParams.height,
            view.findViewById<View>(R.id.widget_art).layoutParams.height,
        )
    }

    @Test
    fun smallerSizesNeverShowMoods() {
        listOf(NowPlayingWidgetLayout.Small, NowPlayingWidgetLayout.Row, NowPlayingWidgetLayout.Tall).forEach { layout ->
            assertNull(layout.name, inflate(layout, playing.copy(moods = true)).findViewById<View>(R.id.widget_moods))
        }
    }

    @Test
    fun whileAMoodRadioStartsTheOtherMoodsDimAndNoneTakesAnotherTap() {
        val view = inflate(NowPlayingWidgetLayout.Moods, playing.copy(moods = true, startingMood = RadioMood.CHILL))

        RadioMood.entries.forEach { mood ->
            val button = view.findViewById<View>(mood.widgetButtonId())
            assertFalse(mood.name, button.isEnabled)
            assertEquals(mood.name, if (mood == RadioMood.CHILL) 1f else 0.4f, button.alpha)
        }
    }

    @Test
    fun atLargeFontSizesTheMoodsKeepTheirIconsButDropTheLabels() {
        RuntimeEnvironment.setFontScale(2f)
        val view = inflate(NowPlayingWidgetLayout.Moods, playing.copy(moods = true))

        RadioMood.entries.forEach { mood ->
            assertEquals(mood.name, View.GONE, view.findViewById<View>(mood.widgetLabelId()).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(mood.widgetButtonId()).visibility)
        }
    }

    @Test
    fun moodIntentsCarryTheirMoodBackToTheWidget() {
        RadioMood.entries.forEach { mood ->
            val intent = Shadows.shadowOf(NowPlayingWidget.moodIntent(context, mood)).savedIntent
            assertEquals(NowPlayingWidget::class.java.name, intent.component?.className)
            assertEquals(mood, NowPlayingWidget.moodOf(intent))
            assertNull(NowPlayingWidget.actionOf(intent))
        }
        NowPlayingWidgetAction.entries.forEach { action ->
            assertNull(NowPlayingWidget.moodOf(Shadows.shadowOf(NowPlayingWidget.commandIntent(context, action)).savedIntent))
        }
    }

    @Test
    fun theSmallWidgetGivesItsRoomToTheSongInsteadOfArtwork() {
        val view = inflate(NowPlayingWidgetLayout.Small, playing)

        assertEquals(null, view.findViewById<View>(R.id.widget_art))
        assertEquals("Song", view.findViewById<TextView>(R.id.widget_title).text)
        assertEquals("Artist", view.findViewById<TextView>(R.id.widget_artist).text)
        assertEquals(context.getString(R.string.controls_pause), view.findViewById<ImageView>(R.id.widget_play_pause).contentDescription)
    }

    @Test
    fun theOneRowWidgetHasArtworkAndAllThreeControls() {
        val view = inflate(NowPlayingWidgetLayout.Row, playing)

        assertTrue(view.findViewById<View>(R.id.widget_art) != null)
        assertTrue(view.findViewById<View>(R.id.widget_previous).isEnabled)
        assertTrue(view.findViewById<View>(R.id.widget_next).isEnabled)
        assertEquals("Song", view.findViewById<TextView>(R.id.widget_title).text)
    }

    @Test
    fun theTallWidgetShowsTheSongWithPauseAndEnabledSkipButtons() {
        val view = inflate(NowPlayingWidgetLayout.Tall, playing)

        assertEquals("Song", view.findViewById<TextView>(R.id.widget_title).text)
        assertEquals("Artist", view.findViewById<TextView>(R.id.widget_artist).text)
        assertEquals(context.getString(R.string.controls_pause), view.findViewById<ImageView>(R.id.widget_play_pause).contentDescription)
        assertTrue(view.findViewById<View>(R.id.widget_previous).isEnabled)
        assertTrue(view.findViewById<View>(R.id.widget_next).isEnabled)
    }

    @Test
    fun theSavedSongOfAStoppedAppShowsPlayWithSkippingDisabled() {
        val view =
            inflate(
                NowPlayingWidgetLayout.Tall,
                NowPlayingWidgetContent("Song", null, null, isPlaying = false, live = false, hasNext = false),
            )

        assertEquals(context.getString(R.string.controls_play), view.findViewById<ImageView>(R.id.widget_play_pause).contentDescription)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_artist).visibility)
        assertFalse(view.findViewById<View>(R.id.widget_previous).isEnabled)
        assertFalse(view.findViewById<View>(R.id.widget_next).isEnabled)
    }

    @Test
    fun theSmallWidgetSaysNothingIsPlayingWithoutAQueue() {
        val view = inflate(NowPlayingWidgetLayout.Small, NowPlayingWidgetContent.Empty)

        assertEquals(context.getString(R.string.widget_nothing_playing), view.findViewById<TextView>(R.id.widget_title).text)
        assertEquals(context.getString(R.string.widget_nothing_playing_hint), view.findViewById<TextView>(R.id.widget_artist).text)
    }

    @Test
    fun buttonIntentsCarryTheirActionBackToTheWidget() {
        NowPlayingWidgetAction.entries.forEach { action ->
            val intent = Shadows.shadowOf(NowPlayingWidget.commandIntent(context, action)).savedIntent
            assertEquals(NowPlayingWidget::class.java.name, intent.component?.className)
            assertEquals(action, NowPlayingWidget.actionOf(intent))
        }
        assertEquals(null, NowPlayingWidget.actionOf(Intent("android.appwidget.action.APPWIDGET_UPDATE")))
    }
}
