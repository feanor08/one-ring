package com.feanor08.onering.notification

import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision/geometry math behind the notification stack -- see
 * [NotificationStackPresentation.kt]'s class doc. Plain JVM, matching
 * [NotificationFeedTest]'s style; no Compose runtime needed.
 */
class NotificationStackPresentationTest {

    private fun post(threadKey: String, sourceKey: String = "whatsapp", message: String = "hi") =
        OneRingNotificationPost(
            threadKey = threadKey,
            sourceKey = sourceKey,
            sourceLabel = "WhatsApp",
            sourceIcon = OneRingIcon.None,
            icon = OneRingIcon.None,
            accentColor = Color(0xFF56C985),
            title = threadKey,
            message = message,
        )

    private fun groupOf(vararg threadKeys: String, sourceKey: String = "whatsapp"): OneRingNotificationGroup {
        var feed = NotificationFeed()
        threadKeys.forEach { feed = feed.post(post(it, sourceKey = sourceKey)) }
        return feed.groupedBySource().single { it.sourceKey == sourceKey }
    }

    // ---- stackDecision / visibleRearDepth ----

    @Test fun `zero or one thread decides Single`() {
        assertEquals(StackDecision.Single, stackDecision(0))
        assertEquals(StackDecision.Single, stackDecision(1))
    }

    @Test fun `two or more threads decides Group`() {
        assertEquals(StackDecision.Group, stackDecision(2))
        assertEquals(StackDecision.Group, stackDecision(3))
        assertEquals(StackDecision.Group, stackDecision(8))
    }

    @Test fun `visible rear depth for 1,2,3,8 threads is 0,1,2,2`() {
        assertEquals(0, visibleRearDepth(1))
        assertEquals(1, visibleRearDepth(2))
        assertEquals(2, visibleRearDepth(3))
        assertEquals(2, visibleRearDepth(8))
    }

    @Test fun `visible rear depth respects a custom cap`() {
        assertEquals(1, visibleRearDepth(8, maxVisibleRearDepth = 1))
        assertEquals(0, visibleRearDepth(8, maxVisibleRearDepth = 0))
    }

    // ---- source thread count vs per-thread message count ----

    @Test fun `source thread count and per-thread message count are independent`() {
        var feed = NotificationFeed()
        feed = feed.post(post("adarsh", message = "1"))
        feed = feed.post(post("adarsh", message = "2"))
        feed = feed.post(post("adarsh", message = "3"))
        feed = feed.post(post("adarsh", message = "4"))
        feed = feed.post(post("adarsh", message = "5"))
        feed = feed.post(post("varsha"))
        feed = feed.post(post("family"))

        val group = feed.groupedBySource().single()
        assertEquals(3, group.threads.size) // source thread count
        val front = group.threads.first { it.threadKey == "adarsh" }
        assertEquals(5, front.messages.size) // per-thread message count, unrelated
        assertEquals("WhatsApp · 5 messages", displaySubtitle(front))
    }

    // ---- front / promotion ----

    @Test fun `front is always threads-first, newest-updated`() {
        val group = groupOf("adarsh", "varsha", "family")
        assertEquals("family", group.threads.first().threadKey)
    }

    @Test fun `dismissing front promotes the next-newest thread`() {
        var feed = NotificationFeed()
        feed = feed.post(post("adarsh"))
        feed = feed.post(post("varsha"))
        feed = feed.post(post("family"))
        val before = feed.groupedBySource().single()
        assertEquals("family", before.threads.first().threadKey)

        feed = feed.dismiss(before.threads.first().threadKey)
        val after = feed.groupedBySource().single()
        assertEquals("varsha", after.threads.first().threadKey)
        assertEquals(2, after.threads.size)
        assertEquals(StackDecision.Group, stackDecision(after.threads.size))
    }

    @Test fun `dropping to one thread flips the decision to Single`() {
        var feed = NotificationFeed()
        feed = feed.post(post("adarsh"))
        feed = feed.post(post("varsha"))
        feed = feed.dismiss("varsha")
        val group = feed.groupedBySource().single()
        assertEquals(StackDecision.Single, stackDecision(group.threads.size))
    }

    @Test fun `same-thread update does not change thread count`() {
        var feed = NotificationFeed()
        feed = feed.post(post("adarsh"))
        feed = feed.post(post("varsha"))
        val before = feed.groupedBySource().single().threads.size

        feed = feed.post(post("adarsh", message = "another message"))
        val after = feed.groupedBySource().single().threads.size

        assertEquals(before, after)
        assertEquals(2, after)
    }

    @Test fun `a new threadKey increases thread count`() {
        var feed = NotificationFeed()
        feed = feed.post(post("adarsh"))
        val before = feed.groupedBySource().single().threads.size

        feed = feed.post(post("varsha"))
        val after = feed.groupedBySource().single().threads.size

        assertEquals(1, before)
        assertEquals(2, after)
    }

    // ---- toggleExpanded ----

    @Test fun `toggling nothing expanded opens the tapped source`() {
        assertEquals("whatsapp", toggleExpanded(current = null, tapped = "whatsapp"))
    }

    @Test fun `toggling the already-expanded source collapses it`() {
        assertNull(toggleExpanded(current = "whatsapp", tapped = "whatsapp"))
    }

    @Test fun `toggling a different source switches to it`() {
        assertEquals("software", toggleExpanded(current = "whatsapp", tapped = "software"))
    }

    // ---- effectiveExpandedSourceKey ----

    @Test fun `effective expansion is null when nothing is stored`() {
        val groups = listOf(groupOf("adarsh", "varsha"))
        assertNull(effectiveExpandedSourceKey(stored = null, groups = groups))
    }

    @Test fun `effective expansion is null when the stored source no longer exists`() {
        val groups = listOf(groupOf("adarsh", "varsha", sourceKey = "whatsapp"))
        assertNull(effectiveExpandedSourceKey(stored = "software", groups = groups))
    }

    @Test fun `effective expansion is null when the stored source has dropped to one thread`() {
        val groups = listOf(groupOf("adarsh", sourceKey = "whatsapp"))
        assertNull(effectiveExpandedSourceKey(stored = "whatsapp", groups = groups))
    }

    @Test fun `effective expansion equals stored when the source still has 2+ threads`() {
        val groups = listOf(groupOf("adarsh", "varsha", sourceKey = "whatsapp"))
        assertEquals("whatsapp", effectiveExpandedSourceKey(stored = "whatsapp", groups = groups))
    }

    // ---- geometry: expanded ----

    @Test fun `expanded offsets are cumulative over preceding heights plus spacing`() {
        val offsets = expandedOffsetsPx(naturalHeightsPx = listOf(100f, 60f, 80f), cardSpacingPx = 10f)
        assertEquals(listOf(0f, 110f, 180f), offsets)
    }

    @Test fun `expanded total height sums heights and gaps between them`() {
        val total = expandedTotalHeightPx(naturalHeightsPx = listOf(100f, 60f, 80f), cardSpacingPx = 10f)
        assertEquals(260f, total) // 100+60+80 + 2*10
    }

    @Test fun `expanded total height of an empty list is zero`() {
        assertEquals(0f, expandedTotalHeightPx(emptyList(), cardSpacingPx = 10f))
    }

    // ---- geometry: collapsed, unequal heights ----

    @Test fun `collapsed offset bottom-aligns each depth regardless of its own natural height`() {
        val frontHeight = 120f
        val backLayerOffset = 8f
        // A rear card taller than the front still bottom-aligns to the same strip position.
        val tallRear = collapsedOffsetPx(depth = 1, frontHeightPx = frontHeight, backLayerOffsetPx = backLayerOffset, childNaturalHeightPx = 200f)
        val shortRear = collapsedOffsetPx(depth = 1, frontHeightPx = frontHeight, backLayerOffsetPx = backLayerOffset, childNaturalHeightPx = 40f)
        val expectedBottom = frontHeight + 1 * backLayerOffset
        assertEquals(expectedBottom, tallRear + 200f, 0.001f)
        assertEquals(expectedBottom, shortRear + 40f, 0.001f)
    }

    @Test fun `collapsed total height is front height plus one offset per visible rear depth`() {
        assertEquals(120f, collapsedTotalHeightPx(frontHeightPx = 120f, visibleRearDepth = 0, backLayerOffsetPx = 8f))
        assertEquals(128f, collapsedTotalHeightPx(frontHeightPx = 120f, visibleRearDepth = 1, backLayerOffsetPx = 8f))
        assertEquals(136f, collapsedTotalHeightPx(frontHeightPx = 120f, visibleRearDepth = 2, backLayerOffsetPx = 8f))
    }

    @Test fun `collapsed clip height is full for the front, a fixed strip for visible rear depths, zero beyond`() {
        val visibleRearDepth = 2
        assertEquals(
            150f,
            collapsedVisibleClipHeightPx(depth = 0, visibleRearDepth = visibleRearDepth, frontHeightPx = 150f, backLayerVisibleHeightPx = 10f),
        )
        assertEquals(
            10f,
            collapsedVisibleClipHeightPx(depth = 1, visibleRearDepth = visibleRearDepth, frontHeightPx = 150f, backLayerVisibleHeightPx = 10f),
        )
        assertEquals(
            10f,
            collapsedVisibleClipHeightPx(depth = 2, visibleRearDepth = visibleRearDepth, frontHeightPx = 150f, backLayerVisibleHeightPx = 10f),
        )
        // "child 3+" for an 8-thread group: composed, but zero visible clip height.
        assertEquals(
            0f,
            collapsedVisibleClipHeightPx(depth = 3, visibleRearDepth = visibleRearDepth, frontHeightPx = 150f, backLayerVisibleHeightPx = 10f),
        )
    }

    @Test fun `collapsed width scale shrinks per depth and never goes negative`() {
        assertEquals(1f, collapsedWidthScale(depth = 0), 0.0001f)
        assertEquals(0.94f, collapsedWidthScale(depth = 1), 0.0001f)
        assertEquals(0.88f, collapsedWidthScale(depth = 2), 0.0001f)
        assertTrue(collapsedWidthScale(depth = 1000) >= 0f)
    }

    // ---- content reveal alpha ----

    @Test fun `content is fully hidden at and below the reveal threshold`() {
        assertEquals(0f, contentRevealAlpha(0f))
        assertEquals(0f, contentRevealAlpha(STACK_CONTENT_REVEAL_THRESHOLD))
    }

    @Test fun `content is fully visible at progress 1`() {
        assertEquals(1f, contentRevealAlpha(1f))
    }

    @Test fun `content ramps between the threshold and 1`() {
        val mid = STACK_CONTENT_REVEAL_THRESHOLD + (1f - STACK_CONTENT_REVEAL_THRESHOLD) / 2f
        val alpha = contentRevealAlpha(mid)
        assertTrue(alpha > 0f && alpha < 1f)
    }

    @Test fun `collapsing content starts fading immediately below progress 1, not only past the threshold`() {
        val justBelowFull = contentRevealAlpha(0.99f)
        assertTrue("expected content to already be fading just below full expansion", justBelowFull < 1f)
    }

    // ---- lerp / progress helpers ----

    @Test fun `lerp interpolates and clamps progress to the 0 to 1 range`() {
        assertEquals(50f, lerp(0f, 100f, 0.5f), 0.0001f)
        assertEquals(0f, lerp(0f, 100f, -1f), 0.0001f)
        assertEquals(100f, lerp(0f, 100f, 2f), 0.0001f)
    }

    @Test fun `edge transform center is the group center at progress 0 and the child center at progress 1`() {
        assertEquals(200f, edgeTransformCenterPx(groupCenterInViewportPx = 200f, childOwnCenterInViewportPx = 500f, progress = 0f), 0.0001f)
        assertEquals(500f, edgeTransformCenterPx(groupCenterInViewportPx = 200f, childOwnCenterInViewportPx = 500f, progress = 1f), 0.0001f)
    }

    @Test fun `edge transform center interpolates at midpoint`() {
        val mid = edgeTransformCenterPx(groupCenterInViewportPx = 200f, childOwnCenterInViewportPx = 500f, progress = 0.5f)
        assertEquals(350f, mid, 0.0001f)
    }
}
