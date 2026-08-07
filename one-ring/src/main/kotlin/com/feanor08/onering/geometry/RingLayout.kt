package com.feanor08.onering.geometry

/** Allocates equal angular wedges for a top-level (or any single-band) ring of items. */
public object RingLayout {
    /**
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
        val totalGap = if (count > 1) gapDeg * (count - 1) else 0f
        val itemSweep = ((totalSweepDeg - totalGap) / count).coerceAtLeast(0f)
        val base = if (centerFirstItem) startDeg - itemSweep / 2f else startDeg
        return List(count) { i -> AngularSpan(base + i * (itemSweep + gapDeg), itemSweep) }
    }
}
