package com.subtracks

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutRunnerTest {
    private val calls = mutableListOf<String>()
    private val messages = mutableListOf<UiMessage>()
    private var opened = 0

    private fun runner(
        sourceId: Long? = 1,
        resumes: Boolean = true,
        shuffles: Boolean = true,
        offline: Boolean = false,
    ) = LauncherShortcutRunner(
        activeSourceId = { sourceId },
        resume = {
            calls += "resume"
            resumes
        },
        smartShuffle = {
            calls += "smartShuffle"
            shuffles
        },
        downloadedOnly = { offline },
        openNowPlaying = { opened++ },
        showMessage = { messages += it },
    )

    @Test
    fun withoutAServerNeitherShortcutTouchesPlaybackOrQueuesAMessage() =
        runBlocking {
            val runner = runner(sourceId = null)
            runner.run(LauncherShortcut.Resume)
            runner.run(LauncherShortcut.SmartShuffle)

            assertTrue(calls.isEmpty())
            assertTrue(messages.isEmpty())
            assertEquals(0, opened)
        }

    @Test
    fun resumeThatStartsOpensNowPlaying() =
        runBlocking {
            runner().run(LauncherShortcut.Resume)

            assertEquals(listOf("resume"), calls)
            assertEquals(1, opened)
            assertTrue(messages.isEmpty())
        }

    @Test
    fun resumeWithNothingQueuedSaysSo() =
        runBlocking {
            runner(resumes = false).run(LauncherShortcut.Resume)

            assertEquals(listOf(UiMessage(R.string.shortcut_nothing_to_resume)), messages)
            assertEquals(0, opened)
        }

    @Test
    fun smartShuffleThatStartsOpensNowPlaying() =
        runBlocking {
            runner().run(LauncherShortcut.SmartShuffle)

            assertEquals(listOf("smartShuffle"), calls)
            assertEquals(1, opened)
            assertTrue(messages.isEmpty())
        }

    @Test
    fun smartShuffleExplainsWhyItCouldNotStart() =
        runBlocking {
            runner(shuffles = false, offline = true).run(LauncherShortcut.SmartShuffle)
            runner(shuffles = false, offline = false).run(LauncherShortcut.SmartShuffle)

            assertEquals(
                listOf(
                    UiMessage(R.string.shortcut_smart_shuffle_no_downloads),
                    UiMessage(R.string.shortcut_smart_shuffle_empty_library),
                ),
                messages,
            )
            assertEquals(0, opened)
        }

    @Test
    fun eachShortcutIsFoundByItsOwnIntentActionOnly() {
        for (shortcut in LauncherShortcut.entries) assertEquals(shortcut, LauncherShortcut.fromAction(shortcut.action))
        assertEquals(null, LauncherShortcut.fromAction(MainActivity.ACTION_OPEN_NOW_PLAYING))
        assertEquals(null, LauncherShortcut.fromAction(null))
    }
}
