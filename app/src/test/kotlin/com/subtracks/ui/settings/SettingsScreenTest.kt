package com.subtracks.ui.settings

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.subtracks.R
import com.subtracks.data.prefs.SortNameMode
import com.subtracks.data.prefs.StreamQuality
import com.subtracks.data.prefs.ThemeMode
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
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    @Test
    fun theMainPageHasTheServersEachPageAndAbout() {
        val opened = mutableListOf<SettingsPage>()
        composeRule.setContent { MaeksterTheme { Settings(page = null, onOpenPage = { opened += it }) } }
        awaitText(text(R.string.settings_servers_actions_add))

        SettingsPage.entries.forEach { page ->
            scrollTo(text(page.title))
            composeRule.onNodeWithText(text(page.title)).performClick()
        }
        scrollTo(text(R.string.settings_about_actions_licenses))

        assertEquals(SettingsPage.entries, opened)
        composeRule.onNodeWithText(text(R.string.settings_theme_title)).assertDoesNotExist()
    }

    @Test
    fun autoplayIsChangedFromPlaybackSettings() {
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            MaeksterTheme { Settings(SettingsPage.Playback, autoplay = enabled.value, onAutoplay = { enabled.value = it }) }
        }
        scrollTo("Autoplay similar")
        composeRule.onNodeWithText("Autoplay similar").performClick()
        assertEquals(true, enabled.value)
    }

    @Test
    fun freshReleasesConsentIsChangedFromApplicationSettingsWithDisclosure() {
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            MaeksterTheme { Settings(SettingsPage.Application, fresh = enabled.value, onFresh = { enabled.value = it }) }
        }
        scrollTo("Fresh releases")
        composeRule.onNodeWithText(text(R.string.fresh_releases_disclosure)).assertExists()
        composeRule.onNodeWithText("Fresh releases").performClick()
        assertEquals(true, enabled.value)
    }

    @Test
    fun recommendedArtistPhotosAreAnApplicationOptInWithDisclosure() {
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            MaeksterTheme { Settings(SettingsPage.Application, photos = enabled.value, onPhotos = { enabled.value = it }) }
        }
        scrollTo(text(R.string.external_artist_images_title))
        composeRule.onNodeWithText(text(R.string.external_artist_images_disclosure)).assertExists()
        composeRule.onNodeWithText(text(R.string.external_artist_images_title)).performClick()
        assertEquals(true, enabled.value)
    }

    @Test
    fun playbackLyricsRowOpensPrivacyControls() {
        composeRule.setContent { MaeksterTheme { Settings(page = SettingsPage.Playback) } }
        scrollTo(text(R.string.lyrics_title))
        composeRule.onNodeWithText(text(R.string.lyrics_title)).performClick()
        composeRule.onNodeWithText(text(R.string.lyrics_source_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.lyrics_allow_lrclib)).assertExists()
    }

    @Test
    fun everySettingIsOnItsPage() {
        val page = mutableStateOf<SettingsPage?>(null)
        composeRule.setContent { MaeksterTheme { Settings(page = page.value) } }
        val expected =
            mapOf(
                null to
                    listOf(
                        R.string.settings_servers_actions_add,
                        R.string.settings_about_actions_licenses,
                        R.string.settings_about_actions_project_homepage,
                        R.string.settings_about_credits,
                    ),
                SettingsPage.Application to
                    listOf(
                        R.string.settings_network_options_offline_mode,
                        R.string.settings_theme_title,
                        R.string.settings_home_sections,
                        R.string.settings_discover_sections,
                        R.string.fresh_releases_title,
                        R.string.external_artist_images_title,
                        R.string.settings_material_you_title,
                        R.string.settings_sort_names_title,
                        R.string.settings_about_actions_share_logs,
                        R.string.settings_about_verbose_logging,
                    ),
                SettingsPage.Playback to
                    listOf(
                        R.string.settings_music_options_scrobble_title,
                        R.string.autoplay_similar,
                        R.string.listenbrainz_title,
                        R.string.settings_music_options_keep_screen_on_title,
                        R.string.lyrics_title,
                        R.string.settings_network_options_max_bitrate_wifi_title,
                        R.string.settings_network_options_max_bitrate_mobile_title,
                        R.string.settings_replay_gain_title,
                        R.string.settings_replay_gain_preamp_title,
                    ),
                SettingsPage.Storage to
                    listOf(
                        R.string.download_quality,
                        R.string.download_over_mobile,
                        R.string.manage_downloads,
                        R.string.settings_sync_auto_title,
                        R.string.sync_interval,
                        R.string.sync_concurrency,
                    ),
            )

        expected.forEach { (shown, labels) ->
            composeRule.runOnIdle { page.value = shown }
            composeRule.waitForIdle()
            labels.forEach { label ->
                scrollTo(text(label))
                composeRule.onNodeWithText(text(label)).assertExists()
            }
        }
    }

    @Test
    fun theKeepScreenOnRowTurnsTheSettingOn() {
        var requested: Boolean? = null
        renderPage(SettingsPage.Playback, "Keep screen on") {
            Settings(page = SettingsPage.Playback, keepScreenOn = false, onKeepScreenOnChange = { requested = it })
        }

        composeRule.onNodeWithText("Keep screen on").performClick()
        composeRule.waitForIdle()

        assertEquals(true, requested)
    }

    @Test
    fun theKeepScreenOnRowTurnsTheSettingOff() {
        var requested: Boolean? = null
        renderPage(SettingsPage.Playback, "Keep screen on") {
            Settings(page = SettingsPage.Playback, keepScreenOn = true, onKeepScreenOnChange = { requested = it })
        }

        composeRule.onNodeWithText("Keep screen on").performClick()
        composeRule.waitForIdle()

        assertEquals(false, requested)
    }

    @Test
    fun theKeepScreenOnSwitchTurnsTheSettingOn() {
        var requested: Boolean? = null
        renderPage(SettingsPage.Playback, "Keep screen on") {
            Settings(page = SettingsPage.Playback, keepScreenOn = false, onKeepScreenOnChange = { requested = it })
        }

        composeRule
            .onNode(isToggleable() and hasAnySibling(hasText("Keep screen on")), useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        assertEquals(true, requested)
    }

    @Test
    fun theSortNamesRowChangesTheSortNameMode() {
        var requested: SortNameMode? = null
        renderPage(SettingsPage.Application, "Sort names") {
            Settings(page = SettingsPage.Application, onSortNameModeChange = { requested = it })
        }

        composeRule.onNodeWithText("Sort names").performClick()
        composeRule.onNodeWithText("Use sort names").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertEquals(SortNameMode.SortName, requested)
    }

    @Test
    fun theThemeRowShowsTheCurrentModeAndChangesIt() {
        var requested: ThemeMode? = null
        renderPage(SettingsPage.Application, "Theme") {
            Settings(page = SettingsPage.Application, themeMode = ThemeMode.System, onThemeModeChange = { requested = it })
        }

        composeRule.onNodeWithText("Follow system").assertExists()
        composeRule.onNodeWithText("Theme").performClick()
        composeRule.onNodeWithText("Light").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertEquals(ThemeMode.Light, requested)
    }

    @Test
    fun theMaterialYouRowTurnsTheSettingOn() {
        var requested: Boolean? = null
        renderPage(SettingsPage.Application, "Material You colours") {
            Settings(page = SettingsPage.Application, onMaterialYouChange = { requested = it })
        }

        composeRule.onNodeWithText("Material You colours").performClick()
        composeRule.waitForIdle()

        assertEquals(true, requested)
    }

    @Test
    fun theMaterialYouRowIsHiddenWhereTheDeviceCannotShowIt() {
        renderPage(SettingsPage.Application, "Theme") {
            Settings(page = SettingsPage.Application, materialYouAvailable = false)
        }

        composeRule.onNodeWithText("Material You colours").assertDoesNotExist()
    }

    @Test
    fun qualityRowsNameTheirPresetOrTheCustomChoice() {
        renderPage(SettingsPage.Playback, "Stream quality: Mobile") {
            Settings(
                page = SettingsPage.Playback,
                wifiQuality = StreamQuality(),
                mobileQuality = StreamQuality(maxBitrate = 192, format = "mp3"),
            )
        }

        composeRule.onNodeWithText("Original").assertExists()
        composeRule.onNodeWithText("MP3 192 kbps").assertExists()
    }

    @Test
    fun aCustomQualityKeepsItsBitrateAndFormatSummary() {
        renderPage(SettingsPage.Playback, "Stream quality: Wi-Fi") {
            Settings(page = SettingsPage.Playback, wifiQuality = StreamQuality(maxBitrate = 256, format = "opus"))
        }

        composeRule.onNodeWithText("opus · 256kbps").assertExists()
    }

    @Test
    fun choosingAPresetSavesItsBitrateAndFormat() {
        var requested: StreamQuality? = null
        renderPage(SettingsPage.Playback, "Stream quality: Wi-Fi") {
            Settings(page = SettingsPage.Playback, onWifiQualityChange = { requested = it })
        }

        composeRule.onNodeWithText("Stream quality: Wi-Fi").performClick()
        composeRule.onNodeWithText("Maximum bitrate").assertDoesNotExist()
        composeRule.onNodeWithText("MP3 320 kbps").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertEquals(StreamQuality(maxBitrate = 320, format = "mp3"), requested)
    }

    @Test
    fun customRevealsAnyBitrateAndFormat() {
        var requested: StreamQuality? = null
        renderPage(SettingsPage.Playback, "Stream quality: Wi-Fi") {
            Settings(page = SettingsPage.Playback, onWifiQualityChange = { requested = it })
        }

        composeRule.onNodeWithText("Stream quality: Wi-Fi").performClick()
        composeRule.onNodeWithText("Custom").performClick()
        composeRule.onNodeWithText("256kbps").performScrollTo().performClick()
        composeRule.onNodeWithText("opus").performScrollTo().performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        assertEquals(StreamQuality(maxBitrate = 256, format = "opus"), requested)
    }

    @Test
    fun aCustomQualityOpensWithCustomChosen() {
        renderPage(SettingsPage.Playback, "Stream quality: Wi-Fi") {
            Settings(page = SettingsPage.Playback, wifiQuality = StreamQuality(maxBitrate = 96, format = "opus"))
        }

        composeRule.onNodeWithText("Stream quality: Wi-Fi").performClick()

        composeRule.onNodeWithText("Maximum bitrate").assertExists()
    }

    @Composable
    private fun Settings(
        page: SettingsPage?,
        wifiQuality: StreamQuality = StreamQuality(),
        mobileQuality: StreamQuality = StreamQuality(),
        onWifiQualityChange: (StreamQuality) -> Unit = {},
        keepScreenOn: Boolean = false,
        onKeepScreenOnChange: (Boolean) -> Unit = {},
        onSortNameModeChange: (SortNameMode) -> Unit = {},
        themeMode: ThemeMode = ThemeMode.System,
        onThemeModeChange: (ThemeMode) -> Unit = {},
        materialYouAvailable: Boolean = true,
        onMaterialYouChange: (Boolean) -> Unit = {},
        onOpenPage: (SettingsPage) -> Unit = {},
        autoplay: Boolean = false,
        onAutoplay: (Boolean) -> Unit = {},
        fresh: Boolean = false,
        onFresh: (Boolean) -> Unit = {},
        photos: Boolean = false,
        onPhotos: (Boolean) -> Unit = {},
    ) {
        SettingsScreen(
            sources = emptyList(),
            activeSourceId = null,
            wifiQuality = wifiQuality,
            mobileQuality = mobileQuality,
            syncConcurrency = 1,
            downloadQuality = StreamQuality(),
            downloadOverMetered = false,
            scrobbling = false,
            keepScreenOn = keepScreenOn,
            onSelectSource = {},
            onEditServer = {},
            onWifiQualityChange = onWifiQualityChange,
            onMobileQualityChange = {},
            onSyncConcurrencyChange = {},
            onDownloadQualityChange = {},
            onDownloadOverMeteredChange = {},
            onScrobblingChange = {},
            onKeepScreenOnChange = onKeepScreenOnChange,
            onSortNameModeChange = onSortNameModeChange,
            themeMode = themeMode,
            materialYou = false,
            materialYouAvailable = materialYouAvailable,
            onThemeModeChange = onThemeModeChange,
            onMaterialYouChange = onMaterialYouChange,
            onAddServer = {},
            onOpenDownloads = {},
            onOpenLicenses = {},
            onShareLogs = {},
            onBack = {},
            page = page,
            onOpenPage = onOpenPage,
            autoplaySimilar = autoplay,
            onAutoplayChange = onAutoplay,
            freshReleasesEnabled = fresh,
            onFreshReleasesChange = onFresh,
            externalArtistImagesEnabled = photos,
            onExternalArtistImagesChange = onPhotos,
        )
    }

    private fun renderPage(
        page: SettingsPage,
        scrollTo: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { MaeksterTheme { content() } }
        awaitText(text(page.title))
        scrollTo(scrollTo)
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
