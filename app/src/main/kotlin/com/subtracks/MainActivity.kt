package com.subtracks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subtracks.data.prefs.ThemeMode
import com.subtracks.data.prefs.UserPreferences
import com.subtracks.data.sync.AutoSync
import com.subtracks.ui.NowPlayingLauncher
import com.subtracks.ui.SubtracksRoot
import com.subtracks.ui.theme.MaeksterTheme
import com.subtracks.ui.theme.StatusBarIcons
import com.subtracks.ui.theme.isDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

private data class ThemeSettings(
    val mode: ThemeMode,
    val materialYou: Boolean,
)

// The platform's own edge-to-edge scrims, so forcing a theme only changes the icon colours.
private val TRANSPARENT_SCRIM = android.graphics.Color.TRANSPARENT
private val LIGHT_NAVIGATION_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAVIGATION_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    private val launchViewModel: NowPlayingLaunchViewModel by viewModels()
    private val nowPlayingLauncher: NowPlayingLauncher by lazy { GlobalContext.get().get() }
    private val autoSync: AutoSync by lazy { GlobalContext.get().get() }
    private val userPreferences: UserPreferences by lazy { GlobalContext.get().get() }
    private val shortcutRunner: LauncherShortcutRunner by lazy { GlobalContext.get().get() }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onStart() {
        super.onStart()
        autoSync.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        autoSync.setForeground(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        enableEdgeToEdge()
        if (launchViewModel.claimLaunch(intent?.action)) nowPlayingLauncher.request()
        GlobalContext.get().get<CoroutineScope>(named("io")).launch { publishLauncherShortcuts(applicationContext) }
        if (savedInstanceState == null) runLauncherShortcut(intent)
        setContent {
            val settings by remember {
                combine(userPreferences.themeMode(), userPreferences.materialYou(), ::ThemeSettings)
            }.collectAsStateWithLifecycle(initialValue = null)
            // Draw nothing until the stored theme is known, so a forced theme never flashes the other one.
            settings?.let { theme ->
                val dark = theme.mode.isDark(isSystemInDarkTheme())
                // Always-dark screens (album, artist, Now Playing) need white status bar icons even in the light theme.
                val whiteStatusIcons = dark || StatusBarIcons.white
                DisposableEffect(dark, whiteStatusIcons) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(TRANSPARENT_SCRIM, TRANSPARENT_SCRIM) { whiteStatusIcons },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM) { dark },
                    )
                    onDispose {}
                }
                MaeksterTheme(themeMode = theme.mode, materialYou = theme.materialYou) {
                    SubtracksRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_NOW_PLAYING) {
            launchViewModel.markHandled()
            nowPlayingLauncher.request()
        }
        runLauncherShortcut(intent)
    }

    /** Runs a shortcut once per tap: not again when the activity is recreated or reopened from recents. */
    private fun runLauncherShortcut(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val shortcut = LauncherShortcut.fromAction(intent.action) ?: return
        ShortcutManagerCompat.reportShortcutUsed(this, shortcut.id)
        GlobalContext.get().get<CoroutineScope>(named("playback")).launch { shortcutRunner.run(shortcut) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "com.subtracks.action.OPEN_NOW_PLAYING"
    }
}
