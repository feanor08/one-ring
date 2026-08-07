package com.feanor08.onering.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

class SeekTrackTest {

    @Test fun `single segment main seek spans the full complement starting at the anchor's clockwise edge`() {
        val anchor = AngularSpan(-30f, 60f)
        val track = SeekTrack.build(anchor)
        assertEquals(1, track.segments.size)
        assertEquals(anchor.endDeg, track.segments[0].startDeg, EPS)
        assertEquals(300f, track.totalSweepDeg, EPS)
    }

    @Test fun `fractionAt exact quarters on a single segment track`() {
        val anchor = AngularSpan(0f, 60f)
        val track = SeekTrack.build(anchor)
        val origin = anchor.endDeg
        assertEquals(0f, track.fractionAtOrNull(origin)!!, EPS)
        assertEquals(0.25f, track.fractionAtOrNull(origin + 75f)!!, EPS)
        assertEquals(0.5f, track.fractionAtOrNull(origin + 150f)!!, EPS)
        assertEquals(0.75f, track.fractionAtOrNull(origin + 225f)!!, EPS)
        assertEquals(1f, track.fractionAtOrNull(origin + 300f)!!, EPS)
    }

    @Test fun `two segment track maps continuously across the boundary (spec example A=120 B=90)`() {
        // anchor sweep 30 leaves 330 of raw track space; a 120deg excluded gap
        // positioned mid-track splits the remainder into segment A = 120deg
        // (before the gap) and segment B = 90deg (after it), exactly the worked
        // example from docs/geometry.md and the design discussion.
        val anchor = AngularSpan(0f, 30f) // endDeg = 30 = origin, raw track = 330
        val excludedGap = AngularSpan(150f, 120f) // rel to origin(30): 120..240, splits A(120) / B(90)
        val track = SeekTrack.build(anchor, excluded = listOf(excludedGap))

        assertEquals(2, track.segments.size)
        assertEquals(120f, track.segments[0].sweepDeg, EPS)
        assertEquals(90f, track.segments[1].sweepDeg, EPS)
        assertEquals(210f, track.totalSweepDeg, EPS)

        val origin = anchor.endDeg
        assertEquals(0f, track.fractionAtOrNull(origin)!!, EPS)
        assertEquals(60f / 210f, track.fractionAtOrNull(origin + 60f)!!, EPS)

        val endOfA = 120f / 210f
        val startOfB = endOfA // must be identical: no jump at the physical boundary
        assertEquals(endOfA, track.fractionAtOrNull(track.segments[0].endDeg)!!, EPS)
        assertEquals(startOfB, track.fractionAtOrNull(track.segments[1].startDeg)!!, EPS)

        assertEquals(165f / 210f, track.fractionAtOrNull(track.segments[1].startDeg + 45f)!!, EPS)
        assertEquals(1f, track.fractionAtOrNull(track.segments[1].endDeg)!!, EPS)
    }

    @Test fun `three or more exclusions produce multiple segments`() {
        val anchor = AngularSpan(0f, 40f) // track = 320
        val excluded = listOf(
            AngularSpan(60f, 20f), // 60..80
            AngularSpan(150f, 20f), // 150..170
            AngularSpan(300f, 20f), // 300..320
        )
        val track = SeekTrack.build(anchor, excluded)
        // origin = 40, trackLength = 320. Blocked rel intervals: (20,40),(110,130),(260,280)
        // -> four free intervals: (0,20) (40,110) (130,260) (260 handled, 280,320)
        assertEquals(4, track.segments.size)
        assertEquals(320f - 60f, track.totalSweepDeg, EPS) // 320 minus the three 20deg exclusions
    }

    @Test fun `anchor adjacent to an excluded span drops the degenerate zero-length segment`() {
        val anchor = AngularSpan(0f, 60f) // origin = 60
        val excluded = listOf(AngularSpan(60f, 30f)) // starts exactly at origin
        val track = SeekTrack.build(anchor, excluded)
        // free region should be a single segment from 90 to 360 (relative 30..360), no
        // zero-length sliver at the origin.
        assertEquals(1, track.segments.size)
        assertEquals(90f, track.segments[0].startDeg, EPS)
        assertEquals(270f, track.segments[0].sweepDeg, EPS)
    }

    @Test fun `anchor spanning zero degrees still maps correctly`() {
        val anchor = AngularSpan(340f, 60f) // 340..400, i.e. wraps through 0
        val track = SeekTrack.build(anchor)
        assertEquals(1, track.segments.size)
        assertEquals(300f, track.totalSweepDeg, EPS)
        val origin = anchor.endDeg // 400
        assertEquals(0.5f, track.fractionAtOrNull(origin + 150f)!!, EPS)
    }

    @Test fun `excluded span crossing 0 degrees absolute is handled`() {
        val anchor = AngularSpan(90f, 40f) // origin = 130
        val excluded = listOf(AngularSpan(350f, 30f)) // 350..380, crosses absolute 0/360
        val track = SeekTrack.build(anchor, excluded)
        // relative to origin 130: relStart = relativeDegrees(130, 350) = 220, relEnd = 250
        // trackLength = 320, so this sits fully inside, splitting into two free segments.
        assertEquals(2, track.segments.size)
        assertEquals(220f, track.segments[0].sweepDeg, EPS)
    }

    @Test fun `excluded span partially overlapping the anchor is clipped not double counted`() {
        val anchor = AngularSpan(0f, 60f) // origin = 60, trackLength = 300
        // exclusion straddling the anchor's own region (before origin)
        val excluded = listOf(AngularSpan(20f, 60f))
        val track = SeekTrack.build(anchor, excluded)
        // relStart = relativeDegrees(60,20) = 320, relEnd = 380 -> wraps: addBlocked(320,360) [clipped to (300,300)=nothing]
        // and addBlocked(0, 20). So only [0,20) is blocked.
        assertEquals(1, track.segments.size)
        assertEquals(20f, track.segments[0].startDeg - anchor.endDeg, EPS)
        assertEquals(280f, track.segments[0].sweepDeg, EPS)
    }

    @Test fun `overlapping excluded spans are merged and total sweep counted once`() {
        val anchor = AngularSpan(0f, 40f) // origin=40, trackLength=320
        val excluded = listOf(
            AngularSpan(60f, 40f), // rel 20..60
            AngularSpan(80f, 40f), // rel 40..80 (overlaps the first)
        )
        val track = SeekTrack.build(anchor, excluded)
        // merged blocked = rel [20,80) -> single 60deg gap
        assertEquals(2, track.segments.size)
        assertEquals(320f - 60f, track.totalSweepDeg, EPS)
    }

    @Test fun `track fully consumed by exclusions is empty and does not divide by zero`() {
        val anchor = AngularSpan(0f, 60f)
        val excluded = listOf(AngularSpan(60f, 300f)) // covers the entire remaining track
        val track = SeekTrack.build(anchor, excluded)
        assertTrue(track.isEmpty)
        assertNull(track.fractionAtOrNull(100f))
        assertEquals(0f, track.fractionAt(100f), EPS) // does not throw / NaN
    }

    @Test fun `fractionAtOrNull returns null inside every excluded gap`() {
        val anchor = AngularSpan(0f, 60f)
        val excluded = listOf(AngularSpan(150f, 30f))
        val track = SeekTrack.build(anchor, excluded)
        assertNull(track.fractionAtOrNull(160f))
    }

    @Test fun `fractionAt snaps a gap angle to the angularly nearer end`() {
        val anchor = AngularSpan(0f, 60f) // origin = 60, track spans 60..360 (300 deg);
        // the only gap is the anchor's own wedge, absolute 0..60.
        val track = SeekTrack.build(anchor)

        // angle=50: just inside the anchor, 10deg before the track's start (60) but
        // 310deg the other way around to the track's end (360) -> snaps to the start.
        assertEquals(0f, track.fractionAt(50f), EPS)

        // angle=10: just inside the anchor the other way, 10deg from the track's
        // end (360, wrapping) but 50deg from the track's start -> snaps to the end.
        assertEquals(1f, track.fractionAt(10f), EPS)
    }

    @Test fun `fractionAt tie at the exact midpoint of the gap resolves deterministically`() {
        val anchor = AngularSpan(0f, 60f) // gap (anchor) is 300..360 in absolute terms
        val track = SeekTrack.build(anchor)
        val midOfGap = 30f // exact middle of the 0..60 anchor gap
        // distance to track start (60) is 30, distance to track end (360) is also 30 -> tie
        val result = track.fractionAt(midOfGap)
        assertEquals(0f, result, EPS) // ties resolve to the start (distToStart <= distToEnd)
    }

    @Test fun `round trip fractionAt of angleAt across 1, 2 and 3 segment tracks`() {
        val configs = listOf(
            AngularSpan(0f, 60f) to emptyList(),
            AngularSpan(0f, 150f) to listOf(AngularSpan(270f, 30f)),
            AngularSpan(0f, 40f) to listOf(AngularSpan(60f, 20f), AngularSpan(150f, 20f)),
        )
        for ((anchor, excluded) in configs) {
            val track = SeekTrack.build(anchor, excluded)
            var f = 0f
            while (f <= 1f) {
                val angle = track.angleAt(f)
                val recovered = track.fractionAtOrNull(angle)
                assertTrue("anchor=$anchor f=$f recovered=$recovered", recovered != null)
                assertEquals("anchor=$anchor f=$f", f, recovered!!, 0.01f)
                f += 0.001f
            }
        }
    }

    @Test fun `monotonicity- sweeping clockwise never decreases fraction with no large jump`() {
        val anchor = AngularSpan(0f, 150f)
        val excluded = listOf(AngularSpan(270f, 30f))
        val track = SeekTrack.build(anchor, excluded)
        val origin = anchor.endDeg
        val maxStepFraction = 1.5f / track.totalSweepDeg // generous bound for a 0.1deg step

        var previous: Float? = null
        var angle = origin
        val end = origin + track.totalSweepDeg + track.segments.let { segs ->
            // account for the excluded gap length so we still sweep past the boundary
            excluded.sumOf { it.sweepDeg.toDouble() }.toFloat()
        }
        while (angle <= end) {
            val f = track.fractionAtOrNull(angle)
            if (f != null) {
                if (previous != null) {
                    assertTrue("prev=$previous f=$f at angle=$angle", f >= previous!! - EPS)
                    assertTrue("jump too large: prev=$previous f=$f", f - previous!! <= maxStepFraction)
                }
                previous = f
            }
            angle += 0.1f
        }
    }

    @Test fun `fillSpans at 0 is empty, at 1 is every segment fully, and mid-fraction is partial`() {
        // same 120/90 worked example as the boundary-continuity test above
        val anchor = AngularSpan(0f, 30f)
        val excluded = listOf(AngularSpan(150f, 120f))
        val track = SeekTrack.build(anchor, excluded)
        val out = mutableListOf<AngularSpan>()

        track.fillSpans(0f, out)
        assertTrue(out.isEmpty())

        track.fillSpans(1f, out)
        assertEquals(2, out.size)
        assertEquals(track.segments[0].sweepDeg, out[0].sweepDeg, EPS)
        assertEquals(track.segments[1].sweepDeg, out[1].sweepDeg, EPS)

        // fraction landing mid-way through segment 2 (120/210 + half of the remaining 90)
        val midSegment2 = (120f + 45f) / 210f
        track.fillSpans(midSegment2, out)
        assertEquals(2, out.size)
        assertEquals(track.segments[0].sweepDeg, out[0].sweepDeg, EPS) // segment 1 whole
        assertEquals(45f, out[1].sweepDeg, EPS) // segment 2 partial
    }
}
