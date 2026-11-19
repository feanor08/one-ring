package com.feanor08.onering.internal

import com.feanor08.onering.geometry.AngularSpan

/**
 * Where each submenu child starts from when the submenu morphs out of the main ring: the span of the sibling wedge it replaces, taken clockwise
 * from the parent (the parent stays put and becomes the close control). With as many children as siblings each child takes its own sibling's span;
 * with a different count the children are matched to the siblings by position along the same arc. The start is shifted by whole turns to sit
 * next to the child's own, so the slide never goes the long way round.
 */
internal fun submenuMorphSources(mainSpans: List<AngularSpan>, parentIndex: Int, childSpans: List<AngularSpan>): List<AngularSpan> {
    val n = mainSpans.size
    if (parentIndex !in 0 until n || n < 2) return childSpans
    val siblings = List(n - 1) { mainSpans[(parentIndex + 1 + it) % n] }
    return childSpans.mapIndexed { j, target ->
        val i = if (siblings.size == childSpans.size) j
        else (((j + 0.5f) * siblings.size / childSpans.size) - 0.5f).let { Math.round(it) }.coerceIn(0, siblings.size - 1)
        val s = siblings[i]
        val turns = Math.round((target.startDeg - s.startDeg) / 360f)
        AngularSpan(s.startDeg + 360f * turns, s.sweepDeg)
    }
}

/** [source] to [target] by [t] (0..1), start and sweep each moved in a straight line. */
internal fun lerpSpan(source: AngularSpan, target: AngularSpan, t: Float): AngularSpan =
    AngularSpan(
        source.startDeg + (target.startDeg - source.startDeg) * t,
        (source.sweepDeg + (target.sweepDeg - source.sweepDeg) * t).coerceAtLeast(0f),
    )
