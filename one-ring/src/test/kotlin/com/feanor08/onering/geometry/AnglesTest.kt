package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

private const val EPS = 1e-3f

class AnglesTest {

    @Test fun `normalizeDegrees wraps into 0 to 360`() {
        assertEquals(0f, normalizeDegrees(0f), EPS)
        assertEquals(359.9f, normalizeDegrees(359.9f), EPS)
        assertEquals(0f, normalizeDegrees(360f), EPS)
        assertEquals(1f, normalizeDegrees(361f), EPS)
        assertEquals(359f, normalizeDegrees(-1f), EPS)
        assertEquals(0f, normalizeDegrees(-360f), EPS)
        assertEquals(359f, normalizeDegrees(-721f), EPS)
        assertEquals(0f, normalizeDegrees(1080f), EPS)
    }

    @Test fun `exact 0 and exact 360 are indistinguishable`() {
        assertEquals(normalizeDegrees(0f), normalizeDegrees(360f), EPS)
        assertEquals(normalizeDegrees(720f), normalizeDegrees(0f), EPS)
    }

    @Test fun `relativeDegrees is the clockwise distance from-to`() {
        assertEquals(20f, relativeDegrees(350f, 10f), EPS)
        assertEquals(340f, relativeDegrees(10f, 350f), EPS)
        assertEquals(0f, relativeDegrees(45f, 45f), EPS)
        assertEquals(0f, relativeDegrees(45f, 405f), EPS)
    }

    @Test fun `circularDistanceDegrees is direction agnostic and shortest path`() {
        assertEquals(20f, circularDistanceDegrees(350f, 10f), EPS)
        assertEquals(20f, circularDistanceDegrees(10f, 350f), EPS)
        assertEquals(180f, circularDistanceDegrees(0f, 180f), EPS)
        assertEquals(0f, circularDistanceDegrees(5f, 365f), EPS)
    }

    @Test fun `angleOfOffset matches the twelve-o'clock clockwise convention`() {
        // up (dx=0, dy=-1) -> 0 deg
        assertEquals(0f, angleOfOffset(0f, -1f), EPS)
        // right (dx=1, dy=0) -> 90 deg
        assertEquals(90f, angleOfOffset(1f, 0f), EPS)
        // down (dx=0, dy=1) -> 180 deg
        assertEquals(180f, angleOfOffset(0f, 1f), EPS)
        // left (dx=-1, dy=0) -> 270 deg
        assertEquals(270f, angleOfOffset(-1f, 0f), EPS)
    }

    @Test fun `polarOffset and angleOfOffset round-trip`() {
        val random = Random(42)
        repeat(1000) {
            val angle = random.nextFloat() * 360f
            val radius = 10f + random.nextFloat() * 500f
            val offset = polarOffset(radius, angle)
            val recovered = angleOfOffset(offset.x, offset.y)
            val diff = circularDistanceDegrees(angle, recovered)
            assertTrue("angle=$angle recovered=$recovered diff=$diff", diff < 0.05f)
        }
    }

    @Test fun `polarOffset radius matches hypot`() {
        val offset = polarOffset(100f, 37f)
        val radius = kotlin.math.hypot(offset.x.toDouble(), offset.y.toDouble()).toFloat()
        assertTrue(abs(radius - 100f) < EPS)
    }
}
