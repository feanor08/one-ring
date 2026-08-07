package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HitTestTest {

    private val band = RadialBand(100f, 200f)

    @Test fun `boundary angle belongs to exactly one wedge`() {
        val spans = RingLayout.spans(count = 6) // 60deg each, boundaries at ...,-30,30,90,...
        val hit = HitTest.hitWedge(30f, 150f, spans, band)
        // 30 is the shared boundary between item0(-30..30) and item1(30..90);
        // half-open ownership means item1 owns it.
        assertEquals(1, hit)
    }

    @Test fun `radius just inside and outside bounds`() {
        val spans = RingLayout.spans(count = 6)
        assertEquals(0, HitTest.hitWedge(0f, 100f, spans, band))
        assertEquals(0, HitTest.hitWedge(0f, 200f, spans, band))
        assertNull(HitTest.hitWedge(0f, 99f, spans, band))
        assertNull(HitTest.hitWedge(0f, 201f, spans, band))
    }

    @Test fun `radial slop extends both inner and outer edges`() {
        val spans = RingLayout.spans(count = 6)
        assertNull(HitTest.hitWedge(0f, 90f, spans, band))
        assertEquals(0, HitTest.hitWedge(0f, 90f, spans, band, radialSlopPx = 15f))
        assertEquals(0, HitTest.hitWedge(0f, 210f, spans, band, radialSlopPx = 15f))
    }

    @Test fun `center point radius zero hits nothing`() {
        val spans = RingLayout.spans(count = 6)
        assertNull(HitTest.hitWedge(0f, 0f, spans, band))
    }

    @Test fun `wraparound wedge is hit correctly at both sides of zero`() {
        // item 0 always spans -30..30 (i.e. 330..390 normalized), straddling 0.
        val spans = RingLayout.spans(count = 6)
        assertEquals(0, HitTest.hitWedge(359f, 150f, spans, band))
        assertEquals(0, HitTest.hitWedge(1f, 150f, spans, band))
        assertEquals(0, HitTest.hitWedge(0f, 150f, spans, band))
    }

    @Test fun `every tenth of a degree maps to exactly one wedge for n from 1 to 16`() {
        for (n in 1..16) {
            val spans = RingLayout.spans(count = n)
            var angle = 0f
            while (angle < 360f) {
                val hit = HitTest.hitWedge(angle, 150f, spans, band)
                assert(hit != null) { "n=$n angle=$angle hit nothing" }
                assert(hit!! in spans.indices) { "n=$n angle=$angle hit out of range: $hit" }
                angle += 0.1f
            }
        }
    }

    @Test fun `angular slop extends the forward edge for touch tolerance`() {
        // a real gap is needed: a fully contiguous ring has no angle that misses
        // every wedge, so slop would have nothing to bridge.
        val spans = RingLayout.spans(count = 6, gapDeg = 10f) // item0 ends at 25 (30 - half the 10deg gap)
        val gapAngle = spans[0].endDeg + 3f
        assertNull(HitTest.hitWedge(gapAngle, 150f, spans, band))
        assertEquals(0, HitTest.hitWedge(gapAngle, 150f, spans, band, angularSlopDeg = 5f))
    }
}
