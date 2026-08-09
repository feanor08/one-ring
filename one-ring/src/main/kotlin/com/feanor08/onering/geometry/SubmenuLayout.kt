package com.feanor08.onering.geometry

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
 * to arbitrary parent sweep, child count, and child weights. See
 * docs/interaction-spec.md.
 */
public object SubmenuLayout {
    /** Equal-width children; see [weighted] for the general form. */
    public fun spans(
        parent: AngularSpan,
        childCount: Int,
        edgePaddingDeg: Float = 0f,
        gapDeg: Float = 0f,
        maxChildSweepDeg: Float = Float.MAX_VALUE,
        alignment: SubmenuAlignment = SubmenuAlignment.Fill,
    ): List<AngularSpan> {
        if (childCount <= 0) return emptyList()
        return weighted(parent, List(childCount) { 1f }, edgePaddingDeg, gapDeg, maxChildSweepDeg, alignment)
    }

    /**
     * Children sized in proportion to [weights] across the sweep left over
     * once [parent]'s own wedge is reserved. Child weights are independent of
     * the parent's: the parent's weight decides its share of the main ring,
     * these decide how the submenu's available sweep is divided.
     *
     * [maxChildSweepDeg] caps the *widest* child and scales every child by the
     * same factor, so clamping shrinks the block without ever changing the
     * ratios between children -- capping each child individually would quietly
     * turn `2:1:1` into `1:1:1` as soon as the largest one hit the ceiling.
     * Whatever sweep that frees is then distributed per [alignment].
     */
    public fun weighted(
        parent: AngularSpan,
        weights: List<Float>,
        edgePaddingDeg: Float = 0f,
        gapDeg: Float = 0f,
        maxChildSweepDeg: Float = Float.MAX_VALUE,
        alignment: SubmenuAlignment = SubmenuAlignment.Fill,
    ): List<AngularSpan> {
        if (weights.isEmpty()) return emptyList()

        val origin = parent.endDeg + edgePaddingDeg
        val available = (FULL_CIRCLE_DEGREES - parent.sweepDeg - 2f * edgePaddingDeg).coerceAtLeast(0f)
        val totalGap = if (weights.size > 1) gapDeg * (weights.size - 1) else 0f
        val forItems = (available - totalGap).coerceAtLeast(0f)

        val natural = proportionalSweeps(weights, forItems)
        val widest = natural.max()
        val scale = if (widest > maxChildSweepDeg && widest > 0f) maxChildSweepDeg / widest else 1f
        val sweeps = if (scale < 1f) natural.map { it * scale } else natural

        val used = sweeps.sum() + totalGap
        val leftover = (available - used).coerceAtLeast(0f)
        val leadingLeftover = if (alignment == SubmenuAlignment.CenterOpposite) leftover / 2f else 0f

        var cursor = origin + leadingLeftover
        return List(weights.size) { i ->
            AngularSpan(cursor, sweeps[i]).also { cursor += sweeps[i] + gapDeg }
        }
    }
}
