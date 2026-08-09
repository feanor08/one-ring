package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hit testing must follow the *actual* weighted boundaries. These would all
 * still pass under an `angle / equalSectorSize` implementation for equal
 * weights, and all fail for unequal ones -- which is the point.
 */
class WeightedHitTestTest {

    private val band = RadialBand(100f, 200f)
    private val radius = 150f

    /** weights 2:1:1 laid out from 0 with item 0's leading edge at 0: [0,180) [180,270) [270,360). */
    private val spans = RingLayout.weighted(
        weights = listOf(2f, 1f, 1f),
        startDeg = 0f,
        centerFirstItem = false,
    )

    private fun hit(angleDeg: Float): Int? = HitTest.hitWedge(angleDeg, radius, spans, band)

    @Test
    fun `the layout under test really is 180-90-90 starting at zero`() {
        assertEquals(0f, spans[0].startDeg, 0.01f)
        assertEquals(180f, spans[0].sweepDeg, 0.01f)
        assertEquals(180f, spans[1].startDeg, 0.01f)
        assertEquals(270f, spans[2].startDeg, 0.01f)
    }

    @Test
    fun `angles across the large first sector all map to index 0`() {
        listOf(0f, 1f, 45f, 90f, 135f, 179f).forEach { angle ->
            assertEquals("angle=$angle", 0, hit(angle))
        }
    }

    @Test
    fun `angles in the second sector map to index 1`() {
        listOf(180f, 181f, 225f, 269f).forEach { angle ->
            assertEquals("angle=$angle", 1, hit(angle))
        }
    }

    @Test
    fun `angles in the third sector map to index 2`() {
        listOf(270f, 271f, 315f, 359f).forEach { angle ->
            assertEquals("angle=$angle", 2, hit(angle))
        }
    }

    @Test
    fun `boundaries are half-open so exactly one sector claims each edge`() {
        assertEquals(0, hit(179.9f))
        assertEquals(1, hit(180f))
        assertEquals(1, hit(269.9f))
        assertEquals(2, hit(270f))
    }

    @Test
    fun `the wraparound boundary at zero belongs to the first sector`() {
        assertEquals(2, hit(359.9f))
        assertEquals(0, hit(0f))
        assertEquals(0, hit(360f))
    }

    @Test
    fun `a rotated weighted ring hit-tests at the rotated boundaries`() {
        val rotated = RingLayout.weighted(listOf(2f, 1f, 1f), startDeg = 90f, centerFirstItem = false)
        fun hitRotated(angle: Float) = HitTest.hitWedge(angle, radius, rotated, band)

        // Sectors are now [90,270) [270,360) [360,450) == [0,90).
        assertEquals(0, hitRotated(91f))
        assertEquals(0, hitRotated(269f))
        assertEquals(1, hitRotated(271f))
        assertEquals(2, hitRotated(10f))
        assertEquals(2, hitRotated(89f))
    }

    @Test
    fun `a partial weighted sweep leaves a real dead zone`() {
        // 2:1:1 over 240 degrees -> [0,120) [120,180) [180,240); 240..360 is empty.
        val partial = RingLayout.weighted(
            weights = listOf(2f, 1f, 1f),
            startDeg = 0f,
            totalSweepDeg = 240f,
            centerFirstItem = false,
        )
        fun hitPartial(angle: Float) = HitTest.hitWedge(angle, radius, partial, band)

        assertEquals(0, hitPartial(60f))
        assertEquals(1, hitPartial(150f))
        assertEquals(2, hitPartial(200f))
        assertNull(hitPartial(300f))
    }

    @Test
    fun `a radius outside the band never hits regardless of angle`() {
        assertNull(HitTest.hitWedge(90f, 50f, spans, band))
        assertNull(HitTest.hitWedge(90f, 400f, spans, band))
    }
}
