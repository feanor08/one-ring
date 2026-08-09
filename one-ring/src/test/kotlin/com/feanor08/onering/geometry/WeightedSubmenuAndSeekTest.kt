package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOL = 0.01f

/**
 * Weighted children inside a submenu, and the seek track built around a
 * weighted anchor. The seek assertions matter most: the track is built by
 * angular *complement*, so if the anchor's real weighted span weren't used,
 * part of the selected wedge would silently become draggable progress.
 */
class WeightedSubmenuAndSeekTest {

    /** A weight-2 parent among 1,1,1 siblings: 360 * 2/5 = 144 degrees. */
    private val heavyParent = RingLayout.weighted(
        weights = listOf(2f, 1f, 1f, 1f),
        startDeg = 0f,
        centerFirstItem = false,
    ).first()

    @Test
    fun `the parent under test occupies its weighted share of the main ring`() {
        assertEquals(144f, heavyParent.sweepDeg, TOL)
    }

    @Test
    fun `children divide only the sweep left over by the parent`() {
        val children = SubmenuLayout.weighted(heavyParent, listOf(2f, 1f, 1f))
        val available = 360f - heavyParent.sweepDeg // 216

        assertEquals(available / 2f, children[0].sweepDeg, TOL)
        assertEquals(available / 4f, children[1].sweepDeg, TOL)
        assertEquals(available / 4f, children[2].sweepDeg, TOL)
        assertEquals(available.toDouble(), children.sumOf { it.sweepDeg.toDouble() }, TOL.toDouble())
    }

    @Test
    fun `a 60 degree parent leaves 300 degrees split 150-75-75 by weights 2-1-1`() {
        val parent = AngularSpan(startDeg = 0f, sweepDeg = 60f)
        val children = SubmenuLayout.weighted(parent, listOf(2f, 1f, 1f))

        assertEquals(150f, children[0].sweepDeg, TOL)
        assertEquals(75f, children[1].sweepDeg, TOL)
        assertEquals(75f, children[2].sweepDeg, TOL)
    }

    @Test
    fun `children start at the parent's clockwise edge and never enter its gap`() {
        val children = SubmenuLayout.weighted(heavyParent, listOf(2f, 1f, 1f, 2f))

        assertEquals(heavyParent.endDeg, children.first().startDeg, TOL)
        assertEquals(heavyParent.startDeg + 360f, children.last().endDeg, TOL)
        children.forEach { child ->
            // No child may overlap the parent's own reserved wedge.
            assertNull(
                "child $child overlaps the parent gap",
                HitTest.hitWedge(
                    angleDeg = normalizeDegrees(heavyParent.midDeg),
                    radiusPx = 150f,
                    spans = listOf(child),
                    band = RadialBand(100f, 200f),
                ),
            )
        }
    }

    @Test
    fun `equal child weights match the plain equal-count submenu layout`() {
        val weighted = SubmenuLayout.weighted(heavyParent, listOf(1f, 1f, 1f))
        val equal = SubmenuLayout.spans(heavyParent, childCount = 3)

        equal.indices.forEach { i ->
            assertEquals(equal[i].startDeg, weighted[i].startDeg, TOL)
            assertEquals(equal[i].sweepDeg, weighted[i].sweepDeg, TOL)
        }
    }

    @Test
    fun `the max-child-sweep cap scales all children together instead of flattening ratios`() {
        val parent = AngularSpan(0f, 60f) // 300 degrees available
        val capped = SubmenuLayout.weighted(parent, listOf(2f, 1f, 1f), maxChildSweepDeg = 100f)

        assertEquals(100f, capped[0].sweepDeg, TOL)
        // Ratio 2:1 must survive the clamp -- capping each child independently
        // would have produced 100/100/100.
        assertEquals(2f, capped[0].sweepDeg / capped[1].sweepDeg, TOL)
        assertEquals(capped[1].sweepDeg, capped[2].sweepDeg, TOL)
    }

    // ---- Seek tracks around a weighted anchor ---------------------------

    @Test
    fun `a main-ring seek excludes its own weighted wedge, not an average one`() {
        // Action 1, Seek 2, Action 1 -> the seek owns 180 of 360 degrees.
        val spans = RingLayout.weighted(listOf(1f, 2f, 1f), startDeg = 0f, centerFirstItem = false)
        val seekSpan = spans[1]
        assertEquals(180f, seekSpan.sweepDeg, TOL)

        val track = SeekTrack.build(anchor = seekSpan)

        assertEquals(360f - 180f, track.totalSweepDeg, TOL)
        assertEquals(seekSpan.endDeg, track.originDeg, TOL)
    }

    @Test
    fun `progress starts at the clockwise edge of the weighted seek wedge`() {
        val spans = RingLayout.weighted(listOf(1f, 2f, 1f), startDeg = 0f, centerFirstItem = false)
        val seekSpan = spans[1]
        val track = SeekTrack.build(anchor = seekSpan)

        assertEquals(seekSpan.endDeg, track.angleAt(0f), TOL)
        assertEquals(seekSpan.startDeg + 360f, track.angleAt(1f), TOL)
    }

    @Test
    fun `no angle inside the weighted seek wedge maps to a progress fraction`() {
        val spans = RingLayout.weighted(listOf(1f, 2f, 1f), startDeg = 0f, centerFirstItem = false)
        val seekSpan = spans[1]
        val track = SeekTrack.build(anchor = seekSpan)

        listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f).forEach { t ->
            val inside = seekSpan.startDeg + seekSpan.sweepDeg * t
            assertNull(
                "angle $inside inside the seek wedge must not be draggable progress",
                track.fractionAtOrNull(normalizeDegrees(inside)),
            )
        }
    }

    @Test
    fun `a submenu seek excludes both the parent gap and its own weighted wedge`() {
        // Media parent weight 2 of 2:1:1 -> 144 degrees; children 2:1:1:2.
        val parentSpan = heavyParent
        val childWeights = listOf(2f, 1f, 1f, 2f)
        val children = SubmenuLayout.weighted(parentSpan, childWeights)
        val volume = children.last()

        val track = SeekTrack.build(anchor = volume, excluded = listOf(parentSpan))

        val expected = 360f - parentSpan.sweepDeg - volume.sweepDeg
        assertEquals(expected, track.totalSweepDeg, TOL)

        // Neither excluded region may map to progress.
        assertNull(track.fractionAtOrNull(normalizeDegrees(parentSpan.midDeg)))
        assertNull(track.fractionAtOrNull(normalizeDegrees(volume.midDeg)))
        // A sibling child in between still does.
        assertNotNull(track.fractionAtOrNull(normalizeDegrees(children[1].midDeg)))
    }

    @Test
    fun `a weighted submenu seek track stays continuous across its two segments`() {
        val parentSpan = heavyParent
        val children = SubmenuLayout.weighted(parentSpan, listOf(2f, 1f, 1f, 2f))
        val volume = children.last()
        val track = SeekTrack.build(anchor = volume, excluded = listOf(parentSpan))

        // Volume is the last child, so the parent gap immediately follows it:
        // the remaining free arc is a single segment here.
        assertTrue(track.segments.isNotEmpty())

        // Fraction must increase monotonically along the track.
        var previous = -1f
        for (i in 0..20) {
            val fraction = i / 20f
            val angle = track.angleAt(fraction)
            val roundTrip = track.fractionAt(normalizeDegrees(angle))
            assertTrue("fraction went backwards at $fraction", roundTrip >= previous - TOL)
            previous = roundTrip
        }
    }

    @Test
    fun `a middle weighted seek child splits the track into two real segments`() {
        val parentSpan = heavyParent
        val children = SubmenuLayout.weighted(parentSpan, listOf(1f, 2f, 1f))
        val middle = children[1]
        val track = SeekTrack.build(anchor = middle, excluded = listOf(parentSpan))

        assertEquals(2, track.segments.size)
        assertEquals(360f - parentSpan.sweepDeg - middle.sweepDeg, track.totalSweepDeg, TOL)
        assertNull(track.fractionAtOrNull(normalizeDegrees(middle.midDeg)))
        assertNull(track.fractionAtOrNull(normalizeDegrees(parentSpan.midDeg)))
    }
}
