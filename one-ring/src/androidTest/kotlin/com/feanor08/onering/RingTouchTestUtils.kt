package com.feanor08.onering

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.center
import androidx.compose.ui.test.height
import androidx.compose.ui.test.width
import com.feanor08.onering.geometry.polarOffset

/**
 * Shared, density-correct touch-coordinate helpers for [TouchInjectionScope]-based
 * interaction tests across this module.
 *
 * These are deliberately extension functions on [TouchInjectionScope] --
 * called from *inside* a `performTouchInput { ... }` block -- rather than
 * plain functions taking a caller-supplied `sizeDp: Int`. [TouchInjectionScope]'s
 * own [width]/[height]/[center] reflect the *actual measured node* in real
 * pixels (see `InjectionScope.getVisibleSize`), which already accounts for
 * device density; a `sizeDp` value treated as if it were pixels is only
 * correct by coincidence, on a density-1 device. That mismatch (e.g. `300.dp`
 * measuring ~788px at a real device's 2.625 density) was a real, previously
 * undiagnosed defect in this module's interaction tests -- every touch point
 * computed from a raw `sizeDp` landed nowhere near its intended wedge on any
 * real/scaled-density screen. There must be exactly one correct version of
 * this helper, shared by every test that needs a point on a ring wedge.
 */

/** A point at [radiusFraction] of this node's own half-size, at [degrees] from its center. */
internal fun TouchInjectionScope.pointAtPolar(radiusFraction: Float, degrees: Float): Offset {
    val radiusPx = minOf(width, height) / 2f * radiusFraction
    val polar = polarOffset(radiusPx, degrees)
    return Offset(center.x + polar.x, center.y + polar.y)
}

/**
 * A point inside item index [index]'s wedge (of [count] equal-sweep items),
 * at [radiusFraction] of the node's half-size. Matches
 * [com.feanor08.onering.geometry.RingLayout.weighted]'s actual
 * `centerFirstItem = true` placement (the library's default, and what
 * [com.feanor08.onering.OneRing] always uses): item 0's midpoint lands
 * exactly on `startDeg` (0 by default), so item `i`'s midpoint is
 * `i * sweep`, *not* `-sweep/2 + i * sweep` -- an earlier version of this
 * helper used that wrong offset and, combined with the density bug above,
 * silently landed on the wrong wedge entirely (a real, second, independent
 * defect: fixing only the density math still left every angle off by half a
 * wedge).
 */
internal fun TouchInjectionScope.pointInWedge(index: Int, count: Int, radiusFraction: Float = 0.7f): Offset {
    val sweep = 360f / count
    val midDeg = index * sweep
    return pointAtPolar(radiusFraction, midDeg)
}
