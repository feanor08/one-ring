package com.feanor08.onering.internal

import com.feanor08.onering.internal.RingOutline.Reach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingOutlineMathTest {
    // Four wedges of 90 degrees centred at 0, 90, 180 and 270; the right one has grown.
    private val reaches = listOf(Reach(0f, 45f, 120f), Reach(90f, 45f, 135f), Reach(180f, 45f, 120f), Reach(270f, 45f, 120f))

    @Test fun insideAWedgeItsOwnReach() {
        assertEquals(135f, RingOutlineMath.radiusAt(90f, reaches, 100f), 0.001f)
        assertEquals(120f, RingOutlineMath.radiusAt(180f, reaches, 100f), 0.001f)
    }

    @Test fun acrossTheGapItEasesBetweenNeighbours() {
        val past = RingOutlineMath.radiusAt(45f + 10f, reaches, 100f)          // 10 degrees beyond the top wedge, inside the right one's fade
        assertTrue(past in 120f..135f)
        val far = RingOutlineMath.radiusAt(180f, reaches, 100f)
        assertEquals(120f, far, 0.001f)                                        // far from the grown one: the plain reach
    }

    @Test fun neverLessThanTheShortestWedge() {
        val shrunk = listOf(Reach(0f, 45f, 80f), Reach(180f, 45f, 150f))
        for (a in 0..360 step 15) assertTrue(RingOutlineMath.radiusAt(a.toFloat(), shrunk, 0f) >= 80f)
    }

    @Test fun anglesWrapAroundTwelveOClock() {
        assertEquals(10f, RingOutlineMath.angularDistance(355f, 5f), 0.001f)
        assertEquals(10f, RingOutlineMath.angularDistance(5f, 355f), 0.001f)
        assertEquals(RingOutlineMath.radiusAt(359f, reaches, 0f), RingOutlineMath.radiusAt(-1f, reaches, 0f), 0.001f)
    }

    @Test fun noWedgesFallsBack() {
        assertEquals(77f, RingOutlineMath.radiusAt(10f, emptyList(), 77f), 0f)
    }
}
