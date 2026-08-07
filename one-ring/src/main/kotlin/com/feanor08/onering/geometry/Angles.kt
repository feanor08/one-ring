package com.feanor08.onering.geometry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Degrees in a full turn. All geometry in this package uses degrees, clockwise,
 * with 0 = twelve o'clock (screen up) -- see docs/geometry.md.
 */
public const val FULL_CIRCLE_DEGREES: Float = 360f

/** Normalizes any angle (including negative or >360) into [0, 360). */
public fun normalizeDegrees(degrees: Float): Float {
    val wrapped = degrees % FULL_CIRCLE_DEGREES
    return if (wrapped < 0f) wrapped + FULL_CIRCLE_DEGREES else wrapped
}

/**
 * The clockwise angular distance travelled going from [from] to [to], normalized
 * into [0, 360). Always non-negative; direction-aware (relativeDegrees(a, b) is
 * generally not equal to relativeDegrees(b, a)).
 */
public fun relativeDegrees(from: Float, to: Float): Float = normalizeDegrees(to - from)

/**
 * The shortest angular distance between two angles, direction-agnostic, in [0, 180].
 */
public fun circularDistanceDegrees(a: Float, b: Float): Float {
    val diff = relativeDegrees(a, b)
    return min(diff, FULL_CIRCLE_DEGREES - diff)
}

/** A 2D point, used instead of a platform/Compose offset type to keep this package dependency-free. */
public data class Point2D(val x: Float, val y: Float)

/**
 * The angle (degrees, clockwise, 0 = up) of the vector (dx, dy) in a y-down
 * coordinate system, e.g. a pointer position relative to a ring's center.
 */
public fun angleOfOffset(dx: Float, dy: Float): Float {
    val radians = atan2(dy.toDouble(), dx.toDouble())
    return normalizeDegrees(Math.toDegrees(radians).toFloat() + 90f)
}

/** The point at [radius] from the origin, at clockwise angle [degrees] (0 = up). */
public fun polarOffset(radius: Float, degrees: Float): Point2D {
    val radians = Math.toRadians((degrees - 90f).toDouble())
    return Point2D(
        x = radius * cos(radians).toFloat(),
        y = radius * sin(radians).toFloat(),
    )
}
