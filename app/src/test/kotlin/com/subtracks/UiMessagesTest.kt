package com.subtracks

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UiMessagesTest {
    @Test
    fun messagesSentBeforeTheShellCollectsWaitAndArriveInOrder() =
        runTest {
            val messages = UiMessages()

            messages.show(UiMessage(1))
            messages.show(UiMessage(2, listOf("detail")))

            assertEquals(
                listOf(UiMessage(1), UiMessage(2, listOf("detail"))),
                messages.messages.take(2).toList(),
            )
        }

    @Test
    fun eachMessageIsShownOnce() =
        runTest {
            val messages = UiMessages()
            messages.show(UiMessage(1))
            messages.show(UiMessage(2))

            val first = messages.messages.take(1).toList()
            val second = messages.messages.take(1).toList()

            assertEquals(listOf(UiMessage(1)), first)
            assertEquals(listOf(UiMessage(2)), second)
        }

    @Test
    fun aFloodKeepsTheNewestMessages() =
        runTest {
            val messages = UiMessages()
            repeat(40) { messages.show(UiMessage(it)) }

            val kept =
                messages.messages
                    .take(16)
                    .toList()
                    .map { it.resId }

            assertEquals((24 until 40).toList(), kept)
        }
}
