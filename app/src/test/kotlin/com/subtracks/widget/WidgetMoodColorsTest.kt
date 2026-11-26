package com.subtracks.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.audiomuse.RadioMood
import com.subtracks.ui.discover.cardColors
import com.subtracks.ui.discover.discoverCardStops
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetMoodColorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun background(mood: RadioMood): Int =
        when (mood) {
            RadioMood.NIGHT -> R.drawable.widget_mood_night
            RadioMood.ENERGETIC -> R.drawable.widget_mood_energetic
            RadioMood.WORKOUT -> R.drawable.widget_mood_workout
            RadioMood.CHILL -> R.drawable.widget_mood_chill
            RadioMood.FOCUS -> R.drawable.widget_mood_focus
        }

    private fun hex(colors: List<Int>) = colors.map { "#%08X".format(it) }

    @Test
    fun eachMoodButtonUsesTheGradientOfItsDiscoverCard() {
        val expected = RadioMood.entries.map { it.name to hex(it.cardColors().discoverCardStops().map { color -> color.toArgb() }) }
        val actual =
            RadioMood.entries.map { mood ->
                val shape = (context.getDrawable(background(mood)) as RippleDrawable).getDrawable(0) as GradientDrawable
                mood.name to hex(shape.colors!!.toList())
            }

        assertEquals(expected, actual)
    }
}
