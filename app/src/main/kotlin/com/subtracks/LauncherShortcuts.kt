package com.subtracks

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** The app icon's long-press shortcuts. Each one opens [MainActivity] with its [action]. */
enum class LauncherShortcut(
    val id: String,
    val action: String,
    @param:StringRes val shortLabel: Int,
    @param:StringRes val longLabel: Int,
    @param:DrawableRes val icon: Int,
) {
    Resume(
        "resume",
        "io.github.marcus_gt.maekster.action.RESUME",
        R.string.shortcut_resume,
        R.string.shortcut_resume_long,
        R.drawable.ic_shortcut_resume,
    ),
    SmartShuffle(
        "smart_shuffle",
        "io.github.marcus_gt.maekster.action.SMART_SHUFFLE",
        R.string.smart_shuffle_title,
        R.string.shortcut_smart_shuffle_long,
        R.drawable.ic_shortcut_smart_shuffle,
    ),
    ;

    companion object {
        fun fromAction(action: String?): LauncherShortcut? = entries.firstOrNull { it.action == action }
    }
}

/**
 * Publishes the shortcuts as dynamic shortcuts, so their intents target this build's own package (static XML
 * shortcuts need a fixed `targetPackage`, which differs between the nightly and release app IDs). Refreshing
 * them on every launch keeps the labels in the current language.
 */
fun publishLauncherShortcuts(context: Context) {
    val shortcuts =
        LauncherShortcut.entries.map { shortcut ->
            ShortcutInfoCompat
                .Builder(context, shortcut.id)
                .setShortLabel(context.getString(shortcut.shortLabel))
                .setLongLabel(context.getString(shortcut.longLabel))
                .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                .setIntent(Intent(shortcut.action, null, context, MainActivity::class.java))
                .setRank(shortcut.ordinal)
                .build()
        }
    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
}

/**
 * Runs a launcher shortcut through the existing playback entry points. Without a server it does nothing,
 * because the welcome screen is what the user sees. A started session opens Now Playing; otherwise a
 * message says why nothing could start.
 */
class LauncherShortcutRunner(
    private val activeSourceId: suspend () -> Long?,
    private val resume: suspend () -> Boolean,
    private val smartShuffle: suspend () -> Boolean,
    private val downloadedOnly: () -> Boolean,
    private val openNowPlaying: () -> Unit,
    private val showMessage: (UiMessage) -> Unit,
) {
    suspend fun run(shortcut: LauncherShortcut) {
        if (activeSourceId() == null) return
        val started =
            when (shortcut) {
                LauncherShortcut.Resume -> resume()
                LauncherShortcut.SmartShuffle -> smartShuffle()
            }
        if (started) {
            openNowPlaying()
        } else {
            showMessage(UiMessage(unavailableReason(shortcut)))
        }
    }

    @StringRes
    private fun unavailableReason(shortcut: LauncherShortcut): Int =
        when (shortcut) {
            LauncherShortcut.Resume -> {
                R.string.shortcut_nothing_to_resume
            }

            LauncherShortcut.SmartShuffle -> {
                if (downloadedOnly()) R.string.shortcut_smart_shuffle_no_downloads else R.string.shortcut_smart_shuffle_empty_library
            }
        }
}
