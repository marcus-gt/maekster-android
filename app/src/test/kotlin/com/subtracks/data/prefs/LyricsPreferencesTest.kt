package com.subtracks.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.data.lyrics.LyricsSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LyricsPreferencesTest {
    @Test
    fun defaultsAndChangesPersistAcrossReadersWithoutEnablingExternalLookup() =
        runTest {
            val file = File.createTempFile("lyrics-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)
            assertEquals(LyricsPreferences(), prefs.lyrics().first())
            prefs.setLyricsSource(LyricsSource.Server)
            prefs.setShowCurrentLyricLine(false)
            prefs.setLyricsTimingOffsetMs(-125)
            assertEquals(LyricsPreferences(LyricsSource.Server, false, false, -125), UserPreferences(store).lyrics().first())
            prefs.setLyricsSource(LyricsSource.Lrclib)
            assertEquals(LyricsPreferences(LyricsSource.Auto, false, false, -125), prefs.lyrics().first())
            file.delete()
        }

    @Test
    fun disablingExternalLookupResetsProviderChoiceAndDoesNotRestoreItOnReenable() =
        runTest {
            val prefs = fakeUserPreferences()
            prefs.setLrclibAllowed(true)
            prefs.setLyricsSource(LyricsSource.Lrclib)
            assertEquals(LyricsPreferences(LyricsSource.Lrclib, true), prefs.lyrics().first())
            prefs.setLrclibAllowed(false)
            assertEquals(LyricsPreferences(), prefs.lyrics().first())
            prefs.setLrclibAllowed(true)
            assertEquals(LyricsPreferences(allowLrclib = true), prefs.lyrics().first())
            prefs.setLyricsSource(LyricsSource.Server)
            prefs.setLrclibAllowed(false)
            assertEquals(LyricsPreferences(source = LyricsSource.Server), prefs.lyrics().first())
        }

    @Test
    fun timingCorrectionClampsOnWriteAndReadAndUnknownSourceRecoversSafely() =
        runTest {
            val file = File.createTempFile("lyrics-prefs", ".preferences_pb").apply { delete() }
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val prefs = UserPreferences(store)
            prefs.setLyricsTimingOffsetMs(Int.MAX_VALUE)
            assertEquals(3000, prefs.lyrics().first().timingOffsetMs)
            prefs.setLyricsTimingOffsetMs(Int.MIN_VALUE)
            assertEquals(-3000, prefs.lyrics().first().timingOffsetMs)
            store.edit {
                it[intPreferencesKey("lyrics_offset_ms")] = Int.MAX_VALUE
                it[stringPreferencesKey("lyrics_source")] = "Unknown"
            }
            assertEquals(LyricsPreferences(timingOffsetMs = 3000), prefs.lyrics().first())
            store.edit {
                it[intPreferencesKey("lyrics_offset_ms")] = Int.MIN_VALUE
                it[stringPreferencesKey("lyrics_source")] = LyricsSource.Lrclib.name
                it[booleanPreferencesKey("lyrics_lrclib_allowed")] = false
            }
            assertEquals(LyricsPreferences(timingOffsetMs = -3000), prefs.lyrics().first())
            file.delete()
        }
}
