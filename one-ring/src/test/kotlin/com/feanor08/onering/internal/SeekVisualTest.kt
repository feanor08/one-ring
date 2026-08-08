package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.SeekTrack
import com.feanor08.onering.geometry.polarOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** RingFrameBuilder.buildSeekVisual: the rendering half of Phase 6's seek controls. */
class SeekVisualTest {

    private val density = FakeDensity()
    private val colors = OneRingDefaults.colors()
    private val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), itemCount = 6)
    private val band = resolved.mainBand

    @Test fun `empty track produces no seek visual`() {
        val anchor = AngularSpan(0f, 60f)
        val excluded = listOf(AngularSpan(60f, 300f)) // consumes the entire remaining track
        val track = SeekTrack.build(anchor, excluded)
        assertNull(RingFrameBuilder.buildSeekVisual(track, fraction = 0.5f, band, resolved, colors))
    }

    @Test fun `fraction 0 has an empty fill and fraction 1 fills every segment`() {
        val track = SeekTrack.build(AngularSpan(-30f, 60f))

        val atZero = RingFrameBuilder.buildSeekVisual(track, fraction = 0f, band, resolved, colors)!!
        assertTrue(atZero.fillArcs.isEmpty())

        val atOne = RingFrameBuilder.buildSeekVisual(track, fraction = 1f, band, resolved, colors)!!
        assertEquals(1, atOne.fillArcs.size)
        assertEquals(300f, atOne.fillArcs[0].span.sweepDeg, 0.01f)
    }

    @Test fun `track arcs use the track color and fill arcs use the progress color`() {
        val track = SeekTrack.build(AngularSpan(-30f, 60f))
        val visual = RingFrameBuilder.buildSeekVisual(track, fraction = 0.4f, band, resolved, colors)!!
        visual.trackArcs.forEach { assertEquals(colors.seekTrack, it.fill) }
        visual.fillArcs.forEach { assertEquals(colors.seekProgress, it.fill) }
    }

    @Test fun `thumb sits at the track's mid-band radius on the arc for the given fraction`() {
        val anchor = AngularSpan(-30f, 60f) // origin = 30
        val track = SeekTrack.build(anchor)
        val fraction = 0.5f
        val visual = RingFrameBuilder.buildSeekVisual(track, fraction, band, resolved, colors)!!

        val expectedAngle = track.angleAt(fraction)
        val expectedPoint = polarOffset(band.midPx, expectedAngle)
        val expectedCenter = Offset(resolved.centerPx.x + expectedPoint.x, resolved.centerPx.y + expectedPoint.y)

        assertEquals(expectedCenter.x, visual.thumbCenter.x, 0.5f)
        assertEquals(expectedCenter.y, visual.thumbCenter.y, 0.5f)

        val radiusFromCenter = hypot(
            (visual.thumbCenter.x - resolved.centerPx.x).toDouble(),
            (visual.thumbCenter.y - resolved.centerPx.y).toDouble(),
        ).toFloat()
        assertEquals(band.midPx, radiusFromCenter, 0.5f)
    }

    @Test fun `thumb radius comes from the resolved seek thumb size`() {
        val track = SeekTrack.build(AngularSpan(-30f, 60f))
        val visual = RingFrameBuilder.buildSeekVisual(track, fraction = 0.2f, band, resolved, colors)!!
        assertEquals(resolved.seekThumbRadiusPx, visual.thumbRadiusPx, 0.01f)
    }

    @Test fun `multi-segment track fills segments in order, partial on the boundary segment`() {
        // Same two-segment worked example as SeekTrackTest: 120deg then 90deg.
        val anchor = AngularSpan(0f, 30f)
        val excluded = listOf(AngularSpan(150f, 120f))
        val track = SeekTrack.build(anchor, excluded)
        val bandHere = RadialBand(100f, 200f)

        val midSegment2 = (120f + 45f) / 210f
        val visual = RingFrameBuilder.buildSeekVisual(track, midSegment2, bandHere, resolved, colors)!!

        assertEquals(2, visual.fillArcs.size)
        assertEquals(track.segments[0].sweepDeg, visual.fillArcs[0].span.sweepDeg, 0.01f) // segment 1 whole
        assertEquals(45f, visual.fillArcs[1].span.sweepDeg, 0.01f) // segment 2 partial
    }
}
