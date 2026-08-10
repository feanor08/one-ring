package com.feanor08.onering.notification

import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NotificationFeed] is the pure model behind [OneRingNotificationState] --
 * this is where the same-thread-merge, source-grouping, and ordering
 * semantics documented on [OneRingNotificationPost]/[NotificationFeed] are
 * pinned down. Plain JVM, no Compose runtime needed.
 */
class NotificationFeedTest {

    private fun post(
        threadKey: String,
        sourceKey: String = "whatsapp",
        sourceLabel: String = "WhatsApp",
        title: String = threadKey,
        message: String = "hi",
        subtitle: String? = null,
        timestampLabel: String? = null,
        accentColor: Color = Color(0xFF56C985),
    ) = OneRingNotificationPost(
        threadKey = threadKey,
        sourceKey = sourceKey,
        sourceLabel = sourceLabel,
        sourceIcon = OneRingIcon.None,
        icon = OneRingIcon.None,
        accentColor = accentColor,
        title = title,
        subtitle = subtitle,
        message = message,
        timestampLabel = timestampLabel,
    )

    @Test fun `posting a new thread creates exactly one card with one message`() {
        val feed = NotificationFeed().post(post("chat:adarsh", message = "I'll reach in 10 minutes."))
        val threads = feed.threadsNewestFirst()
        assertEquals(1, threads.size)
        assertEquals(listOf("I'll reach in 10 minutes."), threads.single().messages)
    }

    @Test fun `posting to the same threadKey appends the message rather than creating a second card`() {
        val feed = NotificationFeed()
            .post(post("chat:adarsh", message = "I'll reach in 10 minutes."))
            .post(post("chat:adarsh", message = "I'm outside."))

        val threads = feed.threadsNewestFirst()
        assertEquals("exactly one card for the thread", 1, threads.size)
        assertEquals(listOf("I'll reach in 10 minutes.", "I'm outside."), threads.single().messages)
    }

    @Test fun `same-thread post does not create a duplicate card`() {
        var feed = NotificationFeed()
        repeat(5) { feed = feed.post(post("chat:adarsh", message = "msg $it")) }
        assertEquals(1, feed.threadsNewestFirst().size)
        assertEquals(5, feed.threadsNewestFirst().single().messages.size)
    }

    @Test fun `an updated thread becomes the newest`() {
        val feed = NotificationFeed()
            .post(post("chat:a", message = "first"))
            .post(post("chat:b", message = "first"))
            .post(post("chat:a", message = "second")) // bump "a" back to newest

        val order = feed.threadsNewestFirst().map { it.threadKey }
        assertEquals(listOf("chat:a", "chat:b"), order)
    }

    @Test fun `updating a thread also moves its source group to newest`() {
        val feed = NotificationFeed()
            .post(post("whatsapp:a", sourceKey = "whatsapp", sourceLabel = "WhatsApp"))
            .post(post("cloud:a", sourceKey = "cloud", sourceLabel = "Cloud"))
            .post(post("whatsapp:a", sourceKey = "whatsapp", sourceLabel = "WhatsApp", message = "again"))

        val groupOrder = feed.groupedBySource().map { it.sourceKey }
        assertEquals(listOf("whatsapp", "cloud"), groupOrder)
    }

    @Test fun `threads from different sources are all preserved`() {
        val feed = NotificationFeed()
            .post(post("whatsapp:a", sourceKey = "whatsapp"))
            .post(post("cloud:a", sourceKey = "cloud"))
            .post(post("battery:a", sourceKey = "battery"))

        assertEquals(3, feed.groupedBySource().size)
        assertEquals(3, feed.threadsNewestFirst().size)
    }

    @Test fun `within a source group, threads are newest-updated first`() {
        val feed = NotificationFeed()
            .post(post("whatsapp:adarsh", sourceKey = "whatsapp"))
            .post(post("whatsapp:varsha", sourceKey = "whatsapp"))
            .post(post("whatsapp:family", sourceKey = "whatsapp"))
            .post(post("whatsapp:adarsh", sourceKey = "whatsapp", message = "again")) // bump adarsh to newest

        val group = feed.groupedBySource().single { it.sourceKey == "whatsapp" }
        assertEquals(listOf("whatsapp:adarsh", "whatsapp:family", "whatsapp:varsha"), group.threads.map { it.threadKey })
    }

    @Test fun `dismiss removes only the requested thread`() {
        val feed = NotificationFeed()
            .post(post("chat:a"))
            .post(post("chat:b"))
            .dismiss("chat:a")

        val remaining = feed.threadsNewestFirst().map { it.threadKey }
        assertEquals(listOf("chat:b"), remaining)
    }

    @Test fun `dismissing a thread that does not exist is a no-op`() {
        val feed = NotificationFeed().post(post("chat:a"))
        val after = feed.dismiss("does-not-exist")
        assertEquals(feed.threadsNewestFirst().map { it.threadKey }, after.threadsNewestFirst().map { it.threadKey })
    }

    @Test fun `dismissing the last thread in a source removes that source's group entirely`() {
        val feed = NotificationFeed()
            .post(post("whatsapp:a", sourceKey = "whatsapp"))
            .post(post("cloud:a", sourceKey = "cloud"))
            .dismiss("whatsapp:a")

        val sourceKeys = feed.groupedBySource().map { it.sourceKey }
        assertFalse(sourceKeys.contains("whatsapp"))
        assertTrue(sourceKeys.contains("cloud"))
    }

    @Test fun `dismissing one thread in a multi-thread source keeps the group with the remaining thread`() {
        val feed = NotificationFeed()
            .post(post("whatsapp:a", sourceKey = "whatsapp"))
            .post(post("whatsapp:b", sourceKey = "whatsapp"))
            .dismiss("whatsapp:a")

        val group = feed.groupedBySource().single { it.sourceKey == "whatsapp" }
        assertEquals(listOf("whatsapp:b"), group.threads.map { it.threadKey })
    }

    @Test fun `clear empties every thread and group`() {
        val feed = NotificationFeed()
            .post(post("chat:a"))
            .post(post("cloud:a", sourceKey = "cloud"))
            .clear()

        assertTrue(feed.isEmpty)
        assertTrue(feed.threadsNewestFirst().isEmpty())
        assertTrue(feed.groupedBySource().isEmpty())
    }

    @Test fun `clear preserves ordering by not resetting the sequence counter`() {
        val before = NotificationFeed().post(post("chat:a"))
        val cleared = before.clear()
        val after = cleared.post(post("chat:b"))
        // "b" must still read as newer than anything that existed before the clear,
        // i.e. the sequence counter kept advancing rather than resetting to 1.
        assertTrue(after.nextSequence > before.nextSequence)
    }

    @Test fun `rapid posts to distinct threads never lose message data`() {
        var feed = NotificationFeed()
        val threadCount = 20
        repeat(threadCount) { i ->
            repeat(3) { j -> feed = feed.post(post("thread:$i", message = "msg-$i-$j")) }
        }
        assertEquals(threadCount, feed.threadsNewestFirst().size)
        feed.threadsNewestFirst().forEach { thread ->
            assertEquals(3, thread.messages.size)
        }
    }

    @Test fun `a later post's null subtitle does not clear an existing subtitle`() {
        val feed = NotificationFeed()
            .post(post("chat:a", subtitle = "WhatsApp"))
            .post(post("chat:a", subtitle = null, message = "second"))

        assertEquals("WhatsApp", feed.threadsNewestFirst().single().subtitle)
    }

    @Test fun `a later post's explicit subtitle overwrites the previous one`() {
        val feed = NotificationFeed()
            .post(post("chat:a", subtitle = "WhatsApp · 1 message"))
            .post(post("chat:a", subtitle = "WhatsApp · 2 messages", message = "second"))

        assertEquals("WhatsApp · 2 messages", feed.threadsNewestFirst().single().subtitle)
    }

    @Test fun `an empty feed has no threads and no groups`() {
        val feed = NotificationFeed()
        assertTrue(feed.isEmpty)
        assertTrue(feed.threadsNewestFirst().isEmpty())
        assertTrue(feed.groupedBySource().isEmpty())
        assertNull(feed.groupedBySource().firstOrNull())
    }
}
