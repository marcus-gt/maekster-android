package com.subtracks.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetArtworkBackgroundTest {
    private val cover =
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            val stripes = IntArray(width * height) { if (it % 2 == 0) Color.WHITE else Color.BLACK }
            setPixels(stripes, 0, width, 0, 0, width, height)
        }

    @Test
    fun theBackgroundRetainsFineArtworkDetailUnderTheScrim() {
        val background = WidgetArtworkBackground().forCover(cover)!!
        val row = background.height / 2
        val bright = Color.red(background.getPixel(64, row))
        val dark = Color.red(background.getPixel(65, row))
        assertTrue("Expected a darkened white stripe, got $bright", bright in 75..85)
        assertTrue("Expected an adjacent black stripe, got $dark", dark < 5)
    }

    @Test
    fun theSameCoverIsRenderedOnlyOnce() {
        val backgrounds = WidgetArtworkBackground()

        assertSame(backgrounds.forCover(cover), backgrounds.forCover(cover))
    }
}
