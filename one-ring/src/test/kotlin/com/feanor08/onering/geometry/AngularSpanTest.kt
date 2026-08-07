package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

class AngularSpanTest {

    @Test fun `endDeg and midDeg are not normalized`() {
        val span = AngularSpan(330f, 60f)
        assertEquals(390f, span.endDeg, EPS)
        assertEquals(360f, span.midDeg, EPS)
    }

    @Test fun `contains is half-open at the forward boundary`() {
        val span = AngularSpan(0f, 60f)
        assertTrue(span.contains(0f))
        assertTrue(span.contains(59.999f))
        assertFalse(span.contains(60f))
    }

    @Test fun `adjacent spans never both claim the shared boundary`() {
        val a = AngularSpan(0f, 60f)
        val b = AngularSpan(60f, 60f)
        assertTrue(a.contains(60f) || b.contains(60f))
        assertFalse(a.contains(60f) && b.contains(60f))
    }

    @Test fun `contains tolerance extends the forward edge`() {
        val span = AngularSpan(0f, 60f)
        assertFalse(span.contains(62f))
        assertTrue(span.contains(62f, toleranceDeg = 5f))
    }

    @Test fun `zero sweep span contains nothing`() {
        val span = AngularSpan(10f, 0f)
        assertFalse(span.contains(10f))
    }

    @Test fun `shrunkBy insets symmetrically`() {
        val span = AngularSpan(0f, 60f)
        val shrunk = span.shrunkBy(10f)
        assertEquals(AngularSpan(10f, 40f), shrunk)
    }

    @Test fun `shrunkBy returns null when it collapses`() {
        val span = AngularSpan(0f, 20f)
        assertNull(span.shrunkBy(15f))
    }

    @Test fun `split divides evenly with no gap`() {
        val span = AngularSpan(-30f, 60f)
        val parts = span.split(3)
        assertEquals(3, parts.size)
        assertEquals(20f, parts[0].sweepDeg, EPS)
        assertEquals(-30f, parts[0].startDeg, EPS)
        assertEquals(-10f, parts[1].startDeg, EPS)
        assertEquals(10f, parts[2].startDeg, EPS)
    }

    @Test fun `split accounts for gaps between items only`() {
        val span = AngularSpan(0f, 100f)
        val parts = span.split(4, gapDeg = 5f)
        // total gap = 3*5 = 15, remaining 85 / 4 = 21.25 each
        assertEquals(4, parts.size)
        parts.forEach { assertEquals(21.25f, it.sweepDeg, EPS) }
        assertEquals(0f, parts[0].startDeg, EPS)
        assertEquals(21.25f + 5f, parts[1].startDeg, EPS)
    }

    @Test fun `split with count zero or negative is empty`() {
        val span = AngularSpan(0f, 60f)
        assertTrue(span.split(0).isEmpty())
        assertTrue(span.split(-1).isEmpty())
    }

    @Test fun `split with count one returns the original span unchanged`() {
        val span = AngularSpan(15f, 60f)
        assertEquals(listOf(span), span.split(1, gapDeg = 99f))
    }
}
