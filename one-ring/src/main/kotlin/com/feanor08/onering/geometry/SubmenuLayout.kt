package com.feanor08.onering.geometry

import kotlin.math.min

/** How leftover angular space is distributed when children are clamped by [SubmenuLayout]'s maxChildSweepDeg. */
public enum class SubmenuAlignment {
    /** No leftover: children fill the whole available complement arc (the default, matches the prototype). */
    Fill,

    /** Leftover is split evenly on both ends, centering the child block opposite the parent's midpoint. */
    CenterOpposite,

    /** All leftover trails at the end; children hug the parent's clockwise edge. */
    StartAtParentEnd,
}

/**
 * Allocates child wedges across the angular complement of a parent wedge -- the
 * generalisation of the prototype's "submenu occupies 360 minus the parent" rule
 * to arbitrary parent sweep and child count. See docs/interaction-spec.md.
 */
public object SubmenuLayout {
    public fun spans(
        parent: AngularSpan,
        childCount: Int,
        edgePaddingDeg: Float = 0f,
        gapDeg: Float = 0f,
        maxChildSweepDeg: Float = Float.MAX_VALUE,
        alignment: SubmenuAlignment = SubmenuAlignment.Fill,
    ): List<AngularSpan> {
        if (childCount <= 0) return emptyList()

        val origin = parent.endDeg + edgePaddingDeg
        val available = (FULL_CIRCLE_DEGREES - parent.sweepDeg - 2f * edgePaddingDeg).coerceAtLeast(0f)

        val totalGap = if (childCount > 1) gapDeg * (childCount - 1) else 0f
        val naturalSweep = ((available - totalGap) / childCount).coerceAtLeast(0f)
        val sweep = min(naturalSweep, maxChildSweepDeg)

        val used = sweep * childCount + totalGap
        val leftover = (available - used).coerceAtLeast(0f)

        val leadingLeftover = if (alignment == SubmenuAlignment.CenterOpposite) leftover / 2f else 0f
        val start = origin + leadingLeftover

        return List(childCount) { i -> AngularSpan(start + i * (sweep + gapDeg), sweep) }
    }
}
