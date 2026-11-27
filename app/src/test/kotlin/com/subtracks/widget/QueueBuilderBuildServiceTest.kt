package com.subtracks.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.subtracks.R
import com.subtracks.data.mix.QueueBuilderIndex
import com.subtracks.data.mix.QueueBuilderPicks
import com.subtracks.playback.QueueBuilderOutcome
import com.subtracks.widget.QueueBuilderWidgetTap.Kind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class QueueBuilderBuildServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var builds = 0
    private var outcome = CompletableDeferred(QueueBuilderOutcome(true))

    private val commands =
        QueueBuilderWidgetCommands(
            activeSourceId = { 1 },
            picks = { QueueBuilderPicks() },
            savePicks = { _, _ -> },
            index = { QueueBuilderIndex() },
            moodsAvailable = { false },
            play = { _, _ ->
                builds++
                outcome.await()
            },
            showBuilding = {},
            showMessage = {},
        )

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { commands }
                    single(named("playback")) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }
                },
            )
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun aPlayTapBuildsInTheShortForegroundService() {
        QueueBuilderWidget().onReceive(context, QueueBuilderWidget.tapIntent(context, QueueBuilderWidgetTap(1, Kind.Play)))

        val started = shadowOf(context as Application).nextStartedService
        assertEquals(QueueBuilderBuildService::class.java.name, started.component?.className)
        assertEquals(QueueBuilderWidgetTap(1, Kind.Play), QueueBuilderWidget.tapOf(started))
    }

    @Test
    fun theServiceRunsInTheForegroundUntilTheQueuePlays() {
        outcome = CompletableDeferred()
        val intent = QueueBuilderWidget.tapIntent(context, QueueBuilderWidgetTap(1, Kind.Play))
        val controller = Robolectric.buildService(QueueBuilderBuildService::class.java, intent).create().startCommand(0, 1)
        val service = shadowOf(controller.get())

        assertEquals(1, builds)
        assertEquals(
            context.getString(R.string.queue_builder_building),
            service.lastForegroundNotification?.extras?.getString("android.text"),
        )
        assertFalse(service.isStoppedBySelf)

        controller.startCommand(0, 2)
        assertEquals(1, builds)

        outcome.complete(QueueBuilderOutcome(true))
        assertTrue(service.isStoppedBySelf)
    }

    @Test
    fun withoutATapTheServiceStopsAtOnce() {
        val controller = Robolectric.buildService(QueueBuilderBuildService::class.java).create().startCommand(0, 1)

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertEquals(0, builds)
    }
}
