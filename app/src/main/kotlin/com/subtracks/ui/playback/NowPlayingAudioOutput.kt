package com.subtracks.ui.playback

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@RequiresApi(33)
@Composable
internal fun rememberMediaAudioOutput(): AudioDeviceInfo? {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    var output by remember(audioManager) { mutableStateOf<AudioDeviceInfo?>(null) }
    DisposableEffect(audioManager, view) {
        val attributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        fun refresh() {
            output =
                audioManager
                    ?.getAudioDevicesForAttributes(attributes)
                    ?.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && it.type != AudioDeviceInfo.TYPE_UNKNOWN }
        }
        val devices =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
            }
        // The system dialog can switch between devices that were already connected.
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) refresh() }
        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focus)
        audioManager?.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        refresh()
        onDispose {
            audioManager?.unregisterAudioDeviceCallback(devices)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
        }
    }
    return output
}
