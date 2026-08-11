package com.feanor08.onering.notification

import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [displaySubtitle] treats a caller-supplied [OneRingNotification.subtitle]
 * as a BASE label, not a full override -- the message count must always be
 * appended once a thread has more than one message, regardless of whether a
 * subtitle was ever explicitly posted.
 */
class DisplaySubtitleTest {

    private fun notification(subtitle: String?, messageCount: Int, sourceLabel: String = "WhatsApp") =
        OneRingNotification(
            threadKey = "t",
            sourceKey = "s",
            sourceLabel = sourceLabel,
            sourceIcon = OneRingIcon.None,
            icon = OneRingIcon.None,
            accentColor = Color(0xFF56C985),
            title = "Adarsh",
            subtitle = subtitle,
            messages = List(messageCount) { "msg $it" },
            timestampLabel = null,
            sequence = 1L,
        )

    @Test fun `no subtitle, one message falls back to the source label`() {
        assertEquals("WhatsApp", displaySubtitle(notification(subtitle = null, messageCount = 1)))
    }

    @Test fun `no subtitle, multiple messages appends the count to the source label`() {
        assertEquals("WhatsApp · 3 messages", displaySubtitle(notification(subtitle = null, messageCount = 3)))
    }

    @Test fun `custom subtitle, one message shows the subtitle alone`() {
        assertEquals("WhatsApp", displaySubtitle(notification(subtitle = "WhatsApp", messageCount = 1)))
    }

    @Test fun `custom subtitle, multiple messages still appends the count`() {
        // The reported bug: a supplied subtitle must not suppress the count.
        assertEquals("WhatsApp · 2 messages", displaySubtitle(notification(subtitle = "WhatsApp", messageCount = 2)))
    }

    @Test fun `an arbitrary custom subtitle also gets the count appended`() {
        assertEquals(
            "On a call · 4 messages",
            displaySubtitle(notification(subtitle = "On a call", messageCount = 4)),
        )
    }

    @Test fun `exactly two messages reads as 2 messages not 2 message`() {
        assertEquals("WhatsApp · 2 messages", displaySubtitle(notification(subtitle = null, messageCount = 2)))
    }
}
