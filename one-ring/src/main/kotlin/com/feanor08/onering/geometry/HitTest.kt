package com.feanor08.onering.geometry

/** Maps a pointer position (angle + radius) to the index of the wedge it falls in, if any. */
public object HitTest {
    /**
     * @param angleDeg pointer angle, clockwise, 0 = up (as returned by [angleOfOffset]).
     * @param radiusPx pointer distance from the ring center.
     * @param spans candidate wedges, in any order; ownership is half-open per [AngularSpan.contains].
     * @param band the radial band the wedges live in; the radial gate is checked once for all spans.
     * @param angularSlopDeg extra generosity on a wedge's forward (clockwise) edge, for touch tolerance.
     * @param radialSlopPx extra generosity on the band's inner/outer edges.
     * @return the index into [spans] of the hit wedge, or null if nothing was hit.
     */
    public fun hitWedge(
        angleDeg: Float,
        radiusPx: Float,
        spans: List<AngularSpan>,
        band: RadialBand,
        angularSlopDeg: Float = 0f,
        radialSlopPx: Float = 0f,
    ): Int? {
        if (!band.contains(radiusPx, radialSlopPx)) return null
        val angle = normalizeDegrees(angleDeg)
        for (i in spans.indices) {
            if (spans[i].contains(angle, angularSlopDeg)) return i
        }
        return null
    }
}
