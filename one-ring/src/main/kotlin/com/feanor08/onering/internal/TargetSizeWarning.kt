package com.feanor08.onering.internal

import android.util.Log
import kotlin.math.PI

private const val TAG = "OneRing"
private var warnedForItemCount = -1
private var warnedForOverlayOverlap = false

/**
 * Logs a one-time warning (per distinct item count) when a main wedge's arc
 * length at mid-radius falls below [minTouchPx] -- e.g. a 12-item ring on a
 * small phone. Diagnostic only; never blocks rendering or interaction.
 */
internal fun warnIfTargetsTooSmall(resolved: ResolvedRing, itemCount: Int, minTouchPx: Float) {
    if (itemCount <= 0 || itemCount == warnedForItemCount) return
    val midRadius = (resolved.mainInnerPx + resolved.mainOuterPx) / 2f
    val sweepRad = resolved.mainSpans.firstOrNull()?.sweepDeg?.let { it * PI.toFloat() / 180f } ?: return
    val arcLengthPx = midRadius * sweepRad
    if (arcLengthPx < minTouchPx) {
        warnedForItemCount = itemCount
        Log.w(
            TAG,
            "Main ring wedge touch target (~${arcLengthPx.toInt()}px arc length) is smaller than the " +
                "recommended minimum (${minTouchPx.toInt()}px) for $itemCount items. Consider fewer items, " +
                "a larger ring, or OneRingDimensions.maxSubmenuItemSweepDeg-style tuning.",
        )
    }
}

/**
 * Logs a one-time warning if `coreOverlayRadius` is configured large enough to
 * overlap the main ring's inner edge -- e.g. via [OneRingDimensions] sliders
 * (see the Geometry showcase demo) rather than the built-in presets, which
 * always leave clearance. Diagnostic only; never blocks rendering.
 */
internal fun warnIfCoreOverlayOverlapsMainRing(resolved: ResolvedRing) {
    if (warnedForOverlayOverlap || resolved.coreOverlayRadiusPx < resolved.mainInnerPx) return
    warnedForOverlayOverlap = true
    Log.w(
        TAG,
        "OneRingDimensions.coreOverlayRadius (~${resolved.coreOverlayRadiusPx.toInt()}px) is not smaller than " +
            "mainInner (~${resolved.mainInnerPx.toInt()}px) -- coreOverlay content will overlap the main ring.",
    )
}
