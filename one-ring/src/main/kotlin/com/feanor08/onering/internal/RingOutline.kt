package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/**
 * How far out the ring reaches at each angle, read live from the wedges' animated radii, so anything drawn outside the ring (the feature badges) can keep a
 * constant clear space from it as wedges grow, shrink, open a submenu or sit out and in.
 *
 * [reaches] is read each time it is asked (it reads the animation states, so a composable that calls [radiusAt] recomposes as they move).
 */
internal class RingOutline(
    val centerPx: Offset,
    /** The main ring's resting outer radius: the reference the feature layout spaces its badges from. */
    val restOuterPx: Float,
    private val reaches: () -> List<Reach>,
) {
    /** One wedge: centred at [midDeg] (clockwise from 12 o'clock), [halfSweepDeg] either side, reaching [outerPx] from the centre. */
    data class Reach(val midDeg: Float, val halfSweepDeg: Float, val outerPx: Float)

    fun radiusAt(angleDeg: Float): Float = RingOutlineMath.radiusAt(angleDeg, reaches(), restOuterPx)
}

internal object RingOutlineMath {
    /** Beyond a wedge's edge its influence fades out over this many degrees, so a badge following the outline moves smoothly across the gaps. */
    const val FALLOFF_DEG = 25f

    /** Smallest angular distance between two angles, 0..180. */
    fun angularDistance(a: Float, b: Float): Float = abs(((a - b) % 360f + 540f) % 360f - 180f)

    /**
     * The reach at [angleDeg]: the wedge there, easing into its neighbours across the gaps, never less than the shortest wedge. [fallback] when there are none.
     */
    fun radiusAt(angleDeg: Float, reaches: List<RingOutline.Reach>, fallback: Float): Float {
        if (reaches.isEmpty()) return fallback
        val floor = reaches.minOf { it.outerPx }
        var best = floor
        for (r in reaches) {
            val away = angularDistance(angleDeg, r.midDeg)
            val influence = if (away <= r.halfSweepDeg) 1f else (1f - (away - r.halfSweepDeg) / FALLOFF_DEG).coerceAtLeast(0f)
            val v = floor + (r.outerPx - floor) * influence
            if (v > best) best = v
        }
        return best
    }
}
