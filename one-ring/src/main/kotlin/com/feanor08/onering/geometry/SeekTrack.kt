package com.feanor08.onering.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * A radial seek track, possibly built from several physical arc segments that
 * together represent one logical 0..1 progress range -- e.g. a submenu seek
 * track that must exclude both the parent's close wedge and its own anchor
 * wedge, leaving two disjoint arcs either side of a gap.
 *
 * The mapping between an absolute pointer angle and a 0..1 fraction is
 * continuous across a segment boundary by construction: see [build] and
 * docs/geometry.md for the "relative-from-origin space" trick this relies on.
 */
public class SeekTrack private constructor(public val originDeg: Float, public val segments: List<AngularSpan>) {
    /** Total angular length of all segments combined -- the denominator of every fraction. */
    public val totalSweepDeg: Float = segments.sumOf { it.sweepDeg.toDouble() }.toFloat()

    public val isEmpty: Boolean get() = segments.isEmpty() || totalSweepDeg <= 0f

    /** Cumulative logical (fraction-space) offset of each segment's start, parallel to [segments]. */
    private val cumulativeStartDeg: List<Float> = run {
        var acc = 0f
        segments.map { seg -> acc.also { acc += seg.sweepDeg } }
    }

    /** Maps an absolute pointer angle to a 0..1 fraction, or null if it falls in an excluded gap. */
    public fun fractionAtOrNull(degrees: Float): Float? {
        if (isEmpty) return null
        val rel = relativeDegrees(originDeg, degrees)
        for (i in segments.indices) {
            val seg = segments[i]
            val segRelStart = relativeDegrees(originDeg, seg.startDeg)
            val segRelEnd = segRelStart + seg.sweepDeg
            if (rel in segRelStart..segRelEnd) {
                return ((cumulativeStartDeg[i] + (rel - segRelStart)) / totalSweepDeg).coerceIn(0f, 1f)
            }
        }
        return null
    }

    /**
     * Same as [fractionAtOrNull], but an angle inside an excluded gap snaps to
     * whichever end of the whole track (0.0 or 1.0) is angularly nearer, instead
     * of returning null.
     */
    public fun fractionAt(degrees: Float): Float {
        fractionAtOrNull(degrees)?.let { return it }
        if (isEmpty) return 0f

        val rel = relativeDegrees(originDeg, degrees)
        val first = segments.first()
        val last = segments.last()
        val firstStartRel = relativeDegrees(originDeg, first.startDeg)
        val lastEndRel = relativeDegrees(originDeg, last.startDeg) + last.sweepDeg

        val distToStart = circularDistanceDegrees(rel, firstStartRel)
        val distToEnd = circularDistanceDegrees(rel, lastEndRel)
        return if (distToStart <= distToEnd) 0f else 1f
    }

    /** Maps a 0..1 fraction to the absolute angle of that position along the track. */
    public fun angleAt(fraction: Float): Float {
        if (isEmpty) return originDeg
        val target = fraction.coerceIn(0f, 1f) * totalSweepDeg
        for (i in segments.indices) {
            val seg = segments[i]
            val segStart = cumulativeStartDeg[i]
            val segEnd = segStart + seg.sweepDeg
            if (target <= segEnd || i == segments.lastIndex) {
                val local = (target - segStart).coerceIn(0f, seg.sweepDeg)
                return seg.startDeg + local
            }
        }
        return originDeg
    }

    /** Writes the physical arc(s) that should render as "filled" up to [fraction] into [out]. */
    public fun fillSpans(fraction: Float, out: MutableList<AngularSpan>) {
        out.clear()
        if (isEmpty) return
        var remaining = fraction.coerceIn(0f, 1f) * totalSweepDeg
        for (seg in segments) {
            if (remaining <= 0f) break
            val used = min(seg.sweepDeg, remaining)
            if (used > 0f) out.add(AngularSpan(seg.startDeg, used))
            remaining -= used
        }
    }

    public companion object {
        /**
         * Builds a track starting at [anchor]'s clockwise edge, spanning the rest
         * of the circle minus [anchor] itself and any [excluded] spans (e.g. a
         * parent's close wedge). Segments are produced by *complement*: an
         * excluded angle is structurally incapable of ever mapping to a progress
         * fraction, there is no runtime guard to forget.
         *
         * Free intervals shorter than [minSegmentDeg] are dropped as degenerate.
         */
        public fun build(
            anchor: AngularSpan,
            excluded: List<AngularSpan> = emptyList(),
            minSegmentDeg: Float = 0.25f,
        ): SeekTrack {
            val origin = anchor.endDeg
            val trackLength = (FULL_CIRCLE_DEGREES - anchor.sweepDeg).coerceAtLeast(0f)
            if (trackLength <= 0f) return SeekTrack(origin, emptyList())

            val blocked = mutableListOf<Pair<Float, Float>>()
            fun addBlocked(start: Float, end: Float) {
                val clampedStart = start.coerceIn(0f, trackLength)
                val clampedEnd = end.coerceIn(0f, trackLength)
                if (clampedEnd > clampedStart) blocked += clampedStart to clampedEnd
            }

            for (ex in excluded) {
                val relStart = relativeDegrees(origin, ex.startDeg)
                val relEnd = relStart + ex.sweepDeg
                if (relEnd <= FULL_CIRCLE_DEGREES) {
                    addBlocked(relStart, relEnd)
                } else {
                    // The exclusion wraps past the origin; split it into the two arcs it covers.
                    addBlocked(relStart, FULL_CIRCLE_DEGREES)
                    addBlocked(0f, relEnd - FULL_CIRCLE_DEGREES)
                }
            }

            val merged = mutableListOf<Pair<Float, Float>>()
            for (pair in blocked.sortedBy { it.first }) {
                val lastIndex = merged.lastIndex
                if (lastIndex >= 0 && pair.first <= merged[lastIndex].second) {
                    merged[lastIndex] = merged[lastIndex].first to max(merged[lastIndex].second, pair.second)
                } else {
                    merged += pair
                }
            }

            val free = mutableListOf<Pair<Float, Float>>()
            var cursor = 0f
            for ((blockedStart, blockedEnd) in merged) {
                if (blockedStart > cursor) free += cursor to blockedStart
                cursor = max(cursor, blockedEnd)
            }
            if (cursor < trackLength) free += cursor to trackLength

            val segments = free
                .filter { (start, end) -> end - start >= minSegmentDeg }
                .map { (start, end) -> AngularSpan(origin + start, end - start) }

            return SeekTrack(origin, segments)
        }
    }
}
