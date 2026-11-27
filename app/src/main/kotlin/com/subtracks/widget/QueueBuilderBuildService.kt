package com.subtracks.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.subtracks.R
import com.subtracks.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * Keeps the app running while a Play tap on the Queue Builder widget builds its queue. A build with moods fetches
 * scores from AudioMuse and can take longer than a widget tap may hold its broadcast; once the broadcast ends Android
 * freezes the idle app, so the build would never finish. A short foreground service (a quiet "Building…" notification)
 * keeps it running and stops as soon as the queue plays or nothing could start. Android lets a widget tap start one.
 * Like the widget itself (D20), Android 12+ only.
 */
@RequiresApi(Build.VERSION_CODES.S)
class QueueBuilderBuildService : Service() {
    private var build: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startInForeground()
        val tap = intent?.let(QueueBuilderWidget::tapOf)
        val koin = GlobalContext.getOrNull()
        val commands = koin?.getOrNull<QueueBuilderWidgetCommands>()
        // A second Play while one builds is ignored, as the widget's commands ignore it too.
        if (build?.isActive == true) return START_NOT_STICKY
        if (tap == null || commands == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        build =
            koin.get<CoroutineScope>(named("playback")).launch {
                try {
                    commands.handle(tap)
                } finally {
                    stopSelf()
                }
            }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) = timedOut()

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) = timedOut()

    override fun onDestroy() {
        build?.cancel()
        super.onDestroy()
    }

    // A short service may run about three minutes; a build that slow is given up rather than stopping the app.
    private fun timedOut() {
        Log.w("widget", "queue builder build timed out")
        stopSelf()
    }

    private fun startInForeground() {
        createChannel()
        val type =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.queue_builder_title))
            .setContentText(getString(R.string.queue_builder_building))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.queue_builder_title), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    internal companion object {
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "queue_builder"

        /**
         * Starts the build for [tap] in this service. False when Android does not allow it right now, so the caller
         * builds within the broadcast instead.
         */
        fun start(
            context: Context,
            tap: QueueBuilderWidgetTap,
        ): Boolean =
            try {
                context.startForegroundService(
                    QueueBuilderWidget.tapIntent(context, tap).setClass(context, QueueBuilderBuildService::class.java),
                )
                true
            } catch (e: IllegalStateException) {
                Log.w("widget", "queue builder build service not allowed", e)
                false
            } catch (e: SecurityException) {
                Log.w("widget", "queue builder build service not allowed", e)
                false
            }
    }
}
