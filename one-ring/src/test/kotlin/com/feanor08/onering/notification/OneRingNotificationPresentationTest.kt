package com.feanor08.onering.notification

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRingNotificationPresentationTest {
    @Test fun `circular is the default shape of the host's presentation parameter`() {
        assertEquals(OneRingNotificationPresentation.Circular, OneRingNotificationPresentation.Circular)
        assertTrue(OneRingNotificationPresentation.Circular is OneRingNotificationPresentation)
    }

    @Test fun `rectangular defaults are the compact shelf values`() {
        val r = OneRingNotificationPresentation.Rectangular(cardWidth = 244.dp, cardMinHeight = 36.dp, cardMaxCollapsedHeight = 40.dp)
        assertEquals(6.dp, r.cardSpacing); assertEquals(6.dp, r.groupSpacing)
        assertEquals(0.35f, r.edgeAlphaReduction, 0f); assertEquals(56.dp, r.arrivalTravel); assertEquals(0.92f, r.arrivalAlpha, 0f)
    }

    @Test fun `rectangular rejects values that cannot work`() {
        assertThrows(IllegalArgumentException::class.java) { OneRingNotificationPresentation.Rectangular(244.dp, 50.dp, 40.dp) }
        assertThrows(IllegalArgumentException::class.java) { OneRingNotificationPresentation.Rectangular(244.dp, 36.dp, 40.dp, edgeAlphaReduction = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { OneRingNotificationPresentation.Rectangular(244.dp, 36.dp, 40.dp, arrivalAlpha = -0.1f) }
    }

    @Test fun `a count-only subtitle is one, two, three messages`() {
        fun n(count: Int) = OneRingNotification(
            threadKey = "t", sourceKey = "S", sourceLabel = "WhatsApp", sourceIcon = com.feanor08.onering.OneRingIcon.None, icon = com.feanor08.onering.OneRingIcon.None,
            accentColor = androidx.compose.ui.graphics.Color.Green, title = "Alice", subtitle = null, messages = List(count) { "m$it" }, timestampLabel = "", sequence = 1L, dismissible = true,
        )
        assertEquals("1 message", countSubtitle(n(1)))
        assertEquals("2 messages", countSubtitle(n(2)))
        assertEquals("3 messages", countSubtitle(n(3)))
    }
}
