package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.style.RadialSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-2f

class ResolvedRingTest {

    private val density = FakeDensity(density = 2f) // 1dp = 2px

    @Test fun `fraction sizes scale with canvas size`() {
        val dimensions = OneRingDefaults.dimensions()
        val small = resolveRing(Size(200f, 200f), density, dimensions, itemCount = 6)
        val large = resolveRing(Size(400f, 400f), density, dimensions, itemCount = 6)
        assertEquals(2f, large.mainOuterPx / small.mainOuterPx, EPS)
        assertEquals(small.mainInnerPx / 100f, dimensions.mainInner.let { (it as RadialSize.Fraction).value }, EPS)
    }

    @Test fun `absolute sizes do not scale with canvas size`() {
        val dimensions = OneRingDefaults.dimensions(centerRadius = RadialSize.Absolute(Dp(20f)))
        val small = resolveRing(Size(200f, 200f), density, dimensions, itemCount = 6)
        val large = resolveRing(Size(800f, 800f), density, dimensions, itemCount = 6)
        // 20dp at density 2 = 40px, regardless of canvas size
        assertEquals(40f, small.centerRadiusPx, EPS)
        assertEquals(40f, large.centerRadiusPx, EPS)
    }

    @Test fun `clamped sizes respect min and max bounds`() {
        val dimensions = OneRingDefaults.dimensions(
            centerRadius = RadialSize.Clamped(fraction = 0.216f, min = Dp(10f), max = Dp(30f)),
        )
        val tiny = resolveRing(Size(10f, 10f), density, dimensions, itemCount = 6) // fraction result would be ~1px
        val huge = resolveRing(Size(4000f, 4000f), density, dimensions, itemCount = 6) // fraction result would be huge
        assertEquals(20f, tiny.centerRadiusPx, EPS) // clamped up to min (10dp * density2 = 20px)
        assertEquals(60f, huge.centerRadiusPx, EPS) // clamped down to max (30dp * density2 = 60px)
    }

    @Test fun `band ordering invariant holds for the default style`() {
        val dimensions = OneRingDefaults.dimensions()
        val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 6)
        assertTrue(resolved.centerRadiusPx < resolved.submenuInnerPx)
        assertTrue(resolved.submenuInnerPx < resolved.submenuOuterPx)
        assertTrue(resolved.submenuOuterPx < resolved.mainInnerPx)
        assertTrue(resolved.mainInnerPx < resolved.mainOuterPx)
        assertTrue(resolved.mainOuterPx < resolved.mainHoverOuterPx)
        assertTrue(resolved.mainHoverOuterPx < resolved.mainSelectedOuterPx)
    }

    @Test fun `extreme aspect ratio uses min of width and height as the base radius`() {
        val dimensions = OneRingDefaults.dimensions()
        val resolved = resolveRing(Size(2000f, 200f), density, dimensions, itemCount = 6)
        assertEquals(100f, resolved.baseRadiusPx, EPS)
    }

    @Test fun `main spans count matches item count and covers the full sweep`() {
        val dimensions = OneRingDefaults.dimensions()
        val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 4)
        assertEquals(4, resolved.mainSpans.size)
        val totalSweep = resolved.mainSpans.sumOf { it.sweepDeg.toDouble() }.toFloat()
        assertTrue(totalSweep <= 360f && totalSweep > 350f) // slightly under 360 due to sectorGap
    }

    @Test fun `mainOuterFor interpolates across rest, hover, and selected stops`() {
        val dimensions = OneRingDefaults.dimensions()
        val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 6)
        assertEquals(resolved.mainOuterPx, resolved.mainOuterFor(0f), EPS)
        assertEquals(resolved.mainHoverOuterPx, resolved.mainOuterFor(1f), EPS)
        assertEquals(resolved.mainSelectedOuterPx, resolved.mainOuterFor(2f), EPS)
        val midHover = resolved.mainOuterFor(0.5f)
        assertTrue(midHover > resolved.mainOuterPx && midHover < resolved.mainHoverOuterPx)
    }
}
