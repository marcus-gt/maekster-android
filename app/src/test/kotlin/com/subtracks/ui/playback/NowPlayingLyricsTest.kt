package com.subtracks.ui.playback

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.subtracks.data.lyrics.Lyrics
import com.subtracks.data.lyrics.LyricsLine
import com.subtracks.data.lyrics.LyricsProviderId
import com.subtracks.data.lyrics.LyricsState
import com.subtracks.data.lyrics.ProvidedLyrics
import com.subtracks.data.lyrics.display
import com.subtracks.playback.PlaybackState
import com.subtracks.playback.QueueItem
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class NowPlayingLyricsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val lines =
        listOf(
            LyricsLine("Everything in its right place", 1000),
            LyricsLine("Yesterday I woke up sucking a lemon", 5000),
            LyricsLine("There are two colours in my head", 9000),
        )

    private fun found(
        provider: LyricsProviderId = LyricsProviderId.Server,
        vararg variants: Lyrics = arrayOf(Lyrics(lines, synced = true)),
    ): LyricsUi {
        val state = LyricsState.Found(variants.map { ProvidedLyrics(provider, it) })
        return LyricsUi(state, state.display(null, 0, "en-US"), showCurrentLine = true)
    }

    private val seeks = mutableListOf<Long>()
    private val languages = mutableListOf<String>()
    private var retries = 0

    private fun show(
        lyrics: LyricsUi,
        open: MutableState<Boolean> = mutableStateOf(false),
        positionMs: Long = 6000,
        fontScale: Float = 1f,
    ): MutableState<Boolean> {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaeksterTheme {
                    NowPlayingScreen(
                        state =
                            PlaybackState(
                                item = QueueItem("s1", "Everything In Its Right Place", "Radiohead", "Kid A", null),
                                durationMs = 251000,
                            ),
                        positionMs = positionMs,
                        title = "Kid A",
                        coverArt = null,
                        artwork = null,
                        onBack = {},
                        onQueue = {},
                        onPlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = { seeks += it },
                        lyrics = lyrics,
                        showLyrics = open.value,
                        onShowLyrics = { open.value = it },
                        onRetryLyrics = { retries++ },
                        onSelectLyricsLanguage = { languages += it },
                    )
                }
            }
        }
        return open
    }

    @Test
    fun thePreviewShowsTheSungLineAndOpensTheLyrics() {
        val open = show(found())
        rule.onNodeWithTag(NOW_PLAYING_COVER_TAG).assertExists()
        rule.onNodeWithText("♪ Yesterday I woke up sucking a lemon ♪").assertIsDisplayed()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_lyric_preview.png")
        rule.onNodeWithTag(LYRICS_PREVIEW_TAG).performClick()
        assertEquals(true, open.value)
        rule.onNodeWithTag(LYRICS_PANE_TAG).assertExists()
        rule.onNodeWithTag(NOW_PLAYING_COVER_TAG).assertDoesNotExist()
        rule.onNodeWithTag(LYRICS_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun thePreviewSettingHidesTheLine() {
        show(found().copy(showCurrentLine = false))
        rule.onNodeWithTag(LYRICS_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun thePaneHighlightsTheCurrentLineAndSeeksToATappedOne() {
        show(found(LyricsProviderId.Lrclib), mutableStateOf(true))
        rule.onNodeWithTag(lyricsLineTag(1)).assertIsSelected()
        rule.onNodeWithTag(lyricsLineTag(0)).assertIsNotSelected()
        rule.onNodeWithText("Lyrics from LRCLIB").assertExists()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_lyrics.png")
        rule.onNodeWithTag(lyricsLineTag(2)).performClick()
        assertEquals(listOf(9000L), seeks)
    }

    @Test
    fun theLyricsButtonTogglesThePane() {
        val open = show(found())
        rule.onNodeWithContentDescription("Lyrics").performClick()
        assertEquals(true, open.value)
        rule.onNodeWithContentDescription("Lyrics").assertIsSelected().performClick()
        assertEquals(false, open.value)
    }

    @Test
    fun plainLyricsCannotSeek() {
        show(found(LyricsProviderId.Server, Lyrics(lines.map { it.copy(startMs = null) }, synced = false)), mutableStateOf(true))
        rule
            .onNodeWithTag(lyricsLineTag(1))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .performClick()
        assertEquals(emptyList<Long>(), seeks)
    }

    @Test
    fun missingAndFailedLyricsExplainThemselves() {
        val state = mutableStateOf(LyricsUi(LyricsState.Missing(offline = true)))
        rule.setContent {
            MaeksterTheme {
                LyricsPane(state.value, 0, 1000, onSeek = {}, onRetry = { retries++ }, onSelectLanguage = {})
            }
        }
        rule.onNodeWithText("No saved lyrics", substring = true).assertIsDisplayed()
        state.value = LyricsUi(LyricsState.Failed)
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aLanguageCanBeChosenWhenThereAreSeveral() {
        show(
            found(
                LyricsProviderId.Server,
                Lyrics(lines, synced = true, language = "eng"),
                Lyrics(lines.map { it.copy(text = "Alles an seinem Platz") }, synced = true, language = "deu"),
            ),
            mutableStateOf(true),
        )
        rule.onNodeWithContentDescription("Lyrics language").performClick()
        rule.onNodeWithText("German").performClick()
        assertEquals(listOf("de"), languages)
    }

    @Test
    fun thePaneFitsAtLargeFontScale() {
        show(found(), mutableStateOf(true), fontScale = 2f)
        rule.onNodeWithTag(lyricsLineTag(1)).assertIsDisplayed().assertIsSelected()
        rule.onRoot().captureRoboImage("src/test/screenshots/now_playing_lyrics_large_font.png")
    }
}
