package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

class SubmenuLayoutTest {

    @Test fun `60 degree parent with 4 children yields 75 degrees each starting at the parent's clockwise edge`() {
        val parent = AngularSpan(-30f, 60f) // matches RingLayout's item 0 in a 6-item wheel
        val children = SubmenuLayout.spans(parent, childCount = 4)
        assertEquals(4, children.size)
        children.forEach { assertEquals(75f, it.sweepDeg, EPS) }
        assertEquals(parent.endDeg, children[0].startDeg, EPS)
    }

    @Test fun `60 degree parent with 3 children yields 100 degrees each`() {
        val parent = AngularSpan(-30f, 60f)
        val children = SubmenuLayout.spans(parent, childCount = 3)
        children.forEach { assertEquals(100f, it.sweepDeg, EPS) }
    }

    @Test fun `90 degree parent (4 main items) with 5 children yields 54 degrees each`() {
        val parent = AngularSpan(-45f, 90f)
        val children = SubmenuLayout.spans(parent, childCount = 5)
        children.forEach { assertEquals(54f, it.sweepDeg, EPS) }
    }

    @Test fun `children never overlap the parent span across a grid of configurations`() {
        for (parentSweep in 10..170 step 10) {
            for (childCount in 1..12) {
                val parent = AngularSpan(0f, parentSweep.toFloat())
                val children = SubmenuLayout.spans(parent, childCount)
                children.forEach { child ->
                    // child must lie fully within [parent.endDeg, parent.startDeg + 360)
                    val relStart = relativeDegrees(parent.endDeg, child.startDeg)
                    val relEnd = relStart + child.sweepDeg
                    val available = 360f - parentSweep
                    assertTrue(
                        "parentSweep=$parentSweep childCount=$childCount child=$child relStart=$relStart",
                        relStart >= -EPS && relEnd <= available + EPS,
                    )
                }
            }
        }
    }

    @Test fun `children are contiguous and clockwise ordered when filling`() {
        val parent = AngularSpan(0f, 60f)
        val children = SubmenuLayout.spans(parent, childCount = 5)
        for (i in 0 until children.size - 1) {
            assertEquals(children[i].endDeg, children[i + 1].startDeg, EPS)
        }
    }

    @Test fun `two children with max sweep and CenterOpposite are clamped and symmetric`() {
        val parent = AngularSpan(-30f, 60f) // mid at 0
        val children = SubmenuLayout.spans(
            parent,
            childCount = 2,
            maxChildSweepDeg = 90f,
            alignment = SubmenuAlignment.CenterOpposite,
        )
        assertEquals(2, children.size)
        children.forEach { assertEquals(90f, it.sweepDeg, EPS) }

        // symmetric leftover: leading gap (parent end -> first child) equals
        // trailing gap (last child end -> parent start + 360)
        val leadingGap = relativeDegrees(parent.endDeg, children[0].startDeg)
        val trailingGap = relativeDegrees(children[1].endDeg, parent.startDeg + 360f)
        assertEquals(leadingGap, trailingGap, EPS)

        // the combined block is centered on the point opposite the parent's midpoint
        val blockMid = (children[0].startDeg + children[1].endDeg) / 2f
        assertEquals(normalizeDegrees(parent.midDeg + 180f), normalizeDegrees(blockMid), EPS)
    }

    @Test fun `childCount zero is empty`() {
        val parent = AngularSpan(0f, 60f)
        assertTrue(SubmenuLayout.spans(parent, childCount = 0).isEmpty())
    }

    @Test fun `edgePadding shrinks the available arc on both sides`() {
        val parent = AngularSpan(-30f, 60f)
        val withoutPadding = SubmenuLayout.spans(parent, childCount = 4)
        val withPadding = SubmenuLayout.spans(parent, childCount = 4, edgePaddingDeg = 10f)
        assertTrue(withPadding[0].sweepDeg < withoutPadding[0].sweepDeg)
        assertEquals(parent.endDeg + 10f, withPadding[0].startDeg, EPS)
    }
}
