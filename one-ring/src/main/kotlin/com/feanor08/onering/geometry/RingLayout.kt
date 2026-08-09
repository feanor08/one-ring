package com.feanor08.onering.geometry

/** Allocates angular wedges for a top-level (or any single-band) ring of items. */
public object RingLayout {
    /**
     * Equal-width wedges -- identical to calling [weightedSpans] with every
     * weight the same, kept as the obvious entry point for the common case.
     *
     * @param count number of items; arbitrary (0..N).
     * @param startDeg the angle item 0 is centered on when [centerFirstItem] is true,
     *   or the angle item 0's leading edge starts at otherwise. 0 = twelve o'clock.
     * @param totalSweepDeg the full angular range to divide; 360 for a complete
     *   ring, less for a partial-arc menu.
     * @param gapDeg angular gap painted between adjacent items (not at the outer edges).
     * @param centerFirstItem if true (default, matches the reference prototype),
     *   item 0's midpoint lands exactly on [startDeg].
     */
    public fun spans(
        count: Int,
        startDeg: Float = 0f,
        totalSweepDeg: Float = FULL_CIRCLE_DEGREES,
        gapDeg: Float = 0f,
        centerFirstItem: Boolean = true,
    ): List<AngularSpan> {
        if (count <= 0) return emptyList()
        return weighted(List(count) { 1f }, startDeg, totalSweepDeg, gapDeg, centerFirstItem)
    }

    /**
     * Wedges sized in proportion to [weights], which are *relative* to each
     * other rather than absolute angles or percentages: `[2, 1, 1]` and
     * `[50, 25, 25]` both give the first item half the available sweep, and an
     * all-equal list reproduces [spans] exactly.
     *
     * [gapDeg] is subtracted before proportioning, so gaps never skew the
     * ratios. With [centerFirstItem] the *first item's own* midpoint lands on
     * [startDeg] -- which for unequal weights means the ring is anchored by
     * item 0, not by an average wedge that no longer exists.
     */
    public fun weighted(
        weights: List<Float>,
        startDeg: Float = 0f,
        totalSweepDeg: Float = FULL_CIRCLE_DEGREES,
        gapDeg: Float = 0f,
        centerFirstItem: Boolean = true,
    ): List<AngularSpan> {
        if (weights.isEmpty()) return emptyList()
        val totalGap = if (weights.size > 1) gapDeg * (weights.size - 1) else 0f
        val firstSweep = proportionalSweeps(weights, (totalSweepDeg - totalGap).coerceAtLeast(0f)).first()
        val base = if (centerFirstItem) startDeg - firstSweep / 2f else startDeg
        return weightedSpans(weights, base, totalSweepDeg, gapDeg)
    }
}
