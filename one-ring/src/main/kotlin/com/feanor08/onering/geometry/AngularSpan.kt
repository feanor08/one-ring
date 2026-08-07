package com.feanor08.onering.geometry

/**
 * An angular sector: [startDeg, startDeg + sweepDeg). [startDeg] and [endDeg] are
 * deliberately NOT normalized into [0, 360) -- see docs/geometry.md. This keeps
 * every span operation (containment, splitting) ordinary 1-D interval arithmetic
 * with no wraparound branch; normalization happens only when an absolute pointer
 * angle is converted with [normalizeDegrees] / [relativeDegrees].
 */
public data class AngularSpan(val startDeg: Float, val sweepDeg: Float) {
    init {
        require(sweepDeg >= 0f) { "sweepDeg must be >= 0, was $sweepDeg" }
    }

    public val endDeg: Float get() = startDeg + sweepDeg
    public val midDeg: Float get() = startDeg + sweepDeg / 2f

    /**
     * Whether [degrees] falls within this span. Ownership is half-open:
     * [startDeg, endDeg) -- so adjacent, non-overlapping spans never both claim a
     * boundary angle. [toleranceDeg] extends the forward edge only (generous hit
     * testing); pass 0 for the strict partition-exclusive rule.
     */
    public fun contains(degrees: Float, toleranceDeg: Float = 0f): Boolean {
        if (sweepDeg <= 0f) return false
        val rel = relativeDegrees(startDeg, degrees)
        return rel < sweepDeg + toleranceDeg
    }

    /** Shrinks this span symmetrically by [paddingDeg] on each side, or null if it collapses. */
    public fun shrunkBy(paddingDeg: Float): AngularSpan? {
        val newSweep = sweepDeg - 2f * paddingDeg
        if (newSweep <= 0f) return null
        return AngularSpan(startDeg + paddingDeg, newSweep)
    }

    /**
     * Divides this span into [count] equal sub-spans separated by [gapDeg], in
     * clockwise order. Gaps sit only between items, never at the outer edges.
     */
    public fun split(count: Int, gapDeg: Float = 0f): List<AngularSpan> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(this)
        val totalGap = gapDeg * (count - 1)
        val itemSweep = ((sweepDeg - totalGap) / count).coerceAtLeast(0f)
        return List(count) { i -> AngularSpan(startDeg + i * (itemSweep + gapDeg), itemSweep) }
    }
}
