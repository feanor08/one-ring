package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOL = 0.01f

/**
 * The core promise of weighted sectors: weights are *relative*, they divide
 * whatever sweep is actually available (never a hardcoded 360), and equal
 * weights reproduce the old equal-division behaviour exactly.
 */
class WeightedRingLayoutTest {

    @Test
    fun `equal weights match the plain equal-count layout exactly`() {
        val weighted = RingLayout.weighted(listOf(1f, 1f, 1f, 1f))
        val equal = RingLayout.spans(count = 4)

        assertEquals(equal.size, weighted.size)
        equal.indices.forEach { i ->
            assertEquals(equal[i].startDeg, weighted[i].startDeg, TOL)
            assertEquals(equal[i].sweepDeg, weighted[i].sweepDeg, TOL)
        }
    }

    @Test
    fun `weights 2-1-1 over a full circle give 180-90-90`() {
        val spans = RingLayout.weighted(listOf(2f, 1f, 1f))
        assertEquals(180f, spans[0].sweepDeg, TOL)
        assertEquals(90f, spans[1].sweepDeg, TOL)
        assertEquals(90f, spans[2].sweepDeg, TOL)
    }

    @Test
    fun `weights are relative so 2-1-1 and 50-25-25 are identical`() {
        val ratio = RingLayout.weighted(listOf(2f, 1f, 1f))
        val percent = RingLayout.weighted(listOf(50f, 25f, 25f))

        ratio.indices.forEach { i ->
            assertEquals(ratio[i].startDeg, percent[i].startDeg, TOL)
            assertEquals(ratio[i].sweepDeg, percent[i].sweepDeg, TOL)
        }
    }

    @Test
    fun `weights 2-1-1 over a 300 degree partial sweep give 150-75-75`() {
        val spans = RingLayout.weighted(listOf(2f, 1f, 1f), totalSweepDeg = 300f)
        assertEquals(150f, spans[0].sweepDeg, TOL)
        assertEquals(75f, spans[1].sweepDeg, TOL)
        assertEquals(75f, spans[2].sweepDeg, TOL)
    }

    @Test
    fun `weights 3-1 over a 240 degree partial sweep give 180-60`() {
        val spans = RingLayout.weighted(listOf(3f, 1f), totalSweepDeg = 240f)
        assertEquals(180f, spans[0].sweepDeg, TOL)
        assertEquals(60f, spans[1].sweepDeg, TOL)
    }

    @Test
    fun `weights 4-3-2-1 over a full circle give 144-108-72-36`() {
        val spans = RingLayout.weighted(listOf(4f, 3f, 2f, 1f))
        assertEquals(144f, spans[0].sweepDeg, TOL)
        assertEquals(108f, spans[1].sweepDeg, TOL)
        assertEquals(72f, spans[2].sweepDeg, TOL)
        assertEquals(36f, spans[3].sweepDeg, TOL)
    }

    @Test
    fun `weights 3-2-1 give 50, 33_3 and 16_7 percent`() {
        val spans = RingLayout.weighted(listOf(3f, 2f, 1f))
        assertEquals(180f, spans[0].sweepDeg, TOL)
        assertEquals(120f, spans[1].sweepDeg, TOL)
        assertEquals(60f, spans[2].sweepDeg, TOL)
    }

    @Test
    fun `sweeps always sum to the available sweep`() {
        listOf(
            listOf(1f, 1f, 1f) to 360f,
            listOf(2f, 1f, 1f) to 360f,
            listOf(5f, 1f, 1f, 1f, 2f) to 300f,
            listOf(4f, 3f, 2f, 1f) to 240f,
            listOf(0.5f, 3.25f, 1.75f) to 190f,
        ).forEach { (weights, sweep) ->
            val total = RingLayout.weighted(weights, totalSweepDeg = sweep).sumOf { it.sweepDeg.toDouble() }
            assertEquals("weights=$weights sweep=$sweep", sweep.toDouble(), total, TOL.toDouble())
        }
    }

    @Test
    fun `last boundary lands exactly on the intended end angle`() {
        // Twelve unequal weights is where naive per-item rounding drifts enough
        // to leave a visible seam at the closing edge.
        val weights = List(12) { (it + 1).toFloat() }
        val spans = RingLayout.weighted(weights, startDeg = 0f, totalSweepDeg = 360f, centerFirstItem = false)
        assertEquals(360f, spans.last().endDeg - spans.first().startDeg, TOL)
    }

    @Test
    fun `gaps are removed before proportioning so ratios are unaffected`() {
        val spans = RingLayout.weighted(listOf(2f, 1f, 1f), gapDeg = 6f)
        // 360 - 3 gaps' worth between 3 items (2 gaps) = 348 to divide 2:1:1.
        assertEquals(174f, spans[0].sweepDeg, TOL)
        assertEquals(87f, spans[1].sweepDeg, TOL)
        assertEquals(87f, spans[2].sweepDeg, TOL)
        assertEquals(2f, spans[0].sweepDeg / spans[1].sweepDeg, TOL)
    }

    @Test
    fun `centerFirstItem centers the first item's own weighted wedge on the start angle`() {
        val spans = RingLayout.weighted(listOf(2f, 1f, 1f), startDeg = 0f)
        assertEquals(0f, normalizeDegrees(spans[0].midDeg), TOL)
    }

    @Test
    fun `a rotated start angle shifts every span without changing sweeps`() {
        val base = RingLayout.weighted(listOf(2f, 1f, 1f), startDeg = 0f)
        listOf(30f, 45f, 90f, 180f).forEach { rotation ->
            val rotated = RingLayout.weighted(listOf(2f, 1f, 1f), startDeg = rotation)
            base.indices.forEach { i ->
                assertEquals(base[i].sweepDeg, rotated[i].sweepDeg, TOL)
                assertEquals(rotation, rotated[i].startDeg - base[i].startDeg, TOL)
            }
        }
    }

    @Test
    fun `spans never overlap and are contiguous when there is no gap`() {
        val spans = RingLayout.weighted(listOf(5f, 1f, 1f, 1f, 2f))
        for (i in 0 until spans.lastIndex) {
            assertEquals(spans[i].endDeg, spans[i + 1].startDeg, TOL)
        }
    }

    @Test
    fun `a single item takes the whole available sweep`() {
        val spans = RingLayout.weighted(listOf(7f), totalSweepDeg = 270f)
        assertEquals(270f, spans.single().sweepDeg, TOL)
    }

    @Test
    fun `an empty weight list produces no spans`() {
        assertTrue(RingLayout.weighted(emptyList()).isEmpty())
    }
}
