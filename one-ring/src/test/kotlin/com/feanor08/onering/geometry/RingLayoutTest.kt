package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

class RingLayoutTest {

    @Test fun `six items produce 60 degree wedges centered on twelve o'clock`() {
        val spans = RingLayout.spans(count = 6)
        assertEquals(6, spans.size)
        spans.forEach { assertEquals(60f, it.sweepDeg, EPS) }
        // item 0 spans -30..30, matching the reference prototype's mainGeo()
        assertEquals(-30f, spans[0].startDeg, EPS)
        assertEquals(0f, spans[0].midDeg, EPS)
        assertEquals(30f, spans[1].startDeg, EPS)
    }

    @Test fun `single item spans the full requested sweep`() {
        val spans = RingLayout.spans(count = 1)
        assertEquals(1, spans.size)
        assertEquals(360f, spans[0].sweepDeg, EPS)
    }

    @Test fun `count zero is empty and does not crash`() {
        assertTrue(RingLayout.spans(count = 0).isEmpty())
        assertTrue(RingLayout.spans(count = -3).isEmpty())
    }

    @Test fun `twelve items divide evenly`() {
        val spans = RingLayout.spans(count = 12)
        assertEquals(12, spans.size)
        spans.forEach { assertEquals(30f, it.sweepDeg, EPS) }
    }

    @Test fun `one hundred items do not crash and sum to a full circle`() {
        val spans = RingLayout.spans(count = 100)
        assertEquals(100, spans.size)
        assertEquals(360f, spans.sumOf { it.sweepDeg.toDouble() }.toFloat(), 0.01f)
    }

    @Test fun `partial arc menu divides only the requested sweep`() {
        val spans = RingLayout.spans(count = 4, totalSweepDeg = 180f)
        assertEquals(4, spans.size)
        spans.forEach { assertEquals(45f, it.sweepDeg, EPS) }
        assertEquals(180f, spans.sumOf { it.sweepDeg.toDouble() }.toFloat(), EPS)
    }

    @Test fun `gapDeg reduces each item's sweep, totalling (n-1) times gap`() {
        // Gaps are painted only between adjacent items, not at the ends -- so for a
        // full 360 ring, item(n-1) and item0 meet with no gap at the wrap-around.
        val spans = RingLayout.spans(count = 6, gapDeg = 3f)
        val totalGap = 5 * 3f
        val expectedSweep = (360f - totalGap) / 6
        spans.forEach { assertEquals(expectedSweep, it.sweepDeg, EPS) }
    }

    @Test fun `startAngle rotates every span by the same amount`() {
        val base = RingLayout.spans(count = 6, startDeg = 0f)
        val rotated = RingLayout.spans(count = 6, startDeg = 45f)
        base.zip(rotated).forEach { (b, r) -> assertEquals(b.startDeg + 45f, r.startDeg, EPS) }
    }

    @Test fun `centerFirstItem false starts item 0 leading edge at startDeg`() {
        val spans = RingLayout.spans(count = 4, startDeg = 10f, centerFirstItem = false)
        assertEquals(10f, spans[0].startDeg, EPS)
    }
}
