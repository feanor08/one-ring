package com.feanor08.onering.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOL = 0.5f

/**
 * The submenu must *grow* into existence -- zero-thickness arcs pinned at the
 * outer radius expanding inward -- rather than fading in at full size. And it
 * must do that without its angular boundaries sliding, which matters far more
 * now that children can be different widths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmenuGrowthTest {

    private val density = FakeDensity(2f)
    private val dimensions = OneRingDefaults.dimensions()
    private val colors = OneRingDefaults.colors()
    private val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 4)

    private val parentSpan = AngularSpan(startDeg = 0f, sweepDeg = 144f)
    private val children = listOf(
        OneRingItem.Action(id = "wide", label = "Wide", weight = 2f),
        OneRingItem.Action(id = "narrow1", label = "Narrow 1"),
        OneRingItem.Action(id = "narrow2", label = "Narrow 2"),
    )

    private fun arcsAt(reveal: Float) = RingFrameBuilder.buildSubmenuArcs(
        children = children,
        parentSpan = parentSpan,
        resolved = resolved,
        dimensions = dimensions,
        colors = colors,
        reveal = reveal,
        expansionOf = { 0f },
    )

    @Test
    fun `at reveal zero the wedges have effectively no radial thickness`() {
        arcsAt(0f).forEach { arc ->
            assertEquals(
                "inner should sit on the outer radius at reveal=0",
                resolved.submenuOuterPx,
                arc.band.innerPx,
                TOL,
            )
            assertTrue("thickness should be ~0, was ${arc.band.thicknessPx}", arc.band.thicknessPx <= TOL)
        }
    }

    @Test
    fun `mid-reveal the inner radius is strictly between the outer and its final value`() {
        val inner = arcsAt(0.5f).first().band.innerPx
        assertTrue("inner=$inner not below the outer radius", inner < resolved.submenuOuterPx - TOL)
        assertTrue("inner=$inner not above the final inner radius", inner > resolved.submenuInnerPx + TOL)
    }

    @Test
    fun `at reveal one the wedges reach the configured submenu band exactly`() {
        arcsAt(1f).forEach { arc ->
            assertEquals(resolved.submenuInnerPx, arc.band.innerPx, TOL)
            assertEquals(resolved.submenuOuterPx, arc.band.outerPx, TOL)
        }
    }

    @Test
    fun `growth is monotonic inward across the whole reveal`() {
        var previous = Float.MAX_VALUE
        for (i in 0..10) {
            val inner = arcsAt(i / 10f).first().band.innerPx
            assertTrue("inner radius moved back outward at reveal=${i / 10f}", inner <= previous + TOL)
            previous = inner
        }
    }

    @Test
    fun `weighted angular boundaries stay perfectly stationary while the wedges grow`() {
        val atStart = arcsAt(0f).map { it.span }
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { reveal ->
            val spans = arcsAt(reveal).map { it.span }
            spans.indices.forEach { i ->
                assertEquals("start moved at reveal=$reveal", atStart[i].startDeg, spans[i].startDeg, 0.001f)
                assertEquals("sweep changed at reveal=$reveal", atStart[i].sweepDeg, spans[i].sweepDeg, 0.001f)
                assertEquals("mid moved at reveal=$reveal", atStart[i].midDeg, spans[i].midDeg, 0.001f)
            }
        }
    }

    @Test
    fun `the wide child stays exactly twice its siblings throughout the growth`() {
        listOf(0f, 0.3f, 0.7f, 1f).forEach { reveal ->
            val spans = arcsAt(reveal).map { it.span }
            assertEquals("ratio drifted at reveal=$reveal", 2f, spans[0].sweepDeg / spans[1].sweepDeg, 0.001f)
        }
    }

    @Test
    fun `content fades in as the wedge grows rather than appearing at full opacity`() {
        assertEquals(0f, arcsAt(0f).first().alpha, 0.001f)
        assertTrue(arcsAt(0.5f).first().alpha in 0.1f..0.9f)
        assertEquals(1f, arcsAt(1f).first().alpha, 0.001f)
    }

    @Test
    fun `an animated reveal passes through real intermediate thicknesses`() = runTest {
        withContext(VirtualFrameClock()) {
            val reveal = Animatable(0f)
            val job = launch { reveal.animateTo(1f, tween(300, easing = LinearEasing)) }

            advanceTimeBy(150)
            runCurrent()
            val midInner = arcsAt(reveal.value).first().band.innerPx
            assertTrue(
                "submenu popped straight to full size instead of growing: inner=$midInner",
                midInner < resolved.submenuOuterPx - TOL && midInner > resolved.submenuInnerPx + TOL,
            )

            advanceUntilIdle()
            job.join()
            assertEquals(resolved.submenuInnerPx, arcsAt(reveal.value).first().band.innerPx, TOL)
        }
    }

    @Test
    fun `closing reverses the growth back to zero thickness`() = runTest {
        withContext(VirtualFrameClock()) {
            val reveal = Animatable(1f)
            val job = launch { reveal.animateTo(0f, tween(300, easing = LinearEasing)) }

            advanceTimeBy(150)
            runCurrent()
            val midInner = arcsAt(reveal.value).first().band.innerPx
            assertTrue(
                "submenu vanished instead of shrinking: inner=$midInner",
                midInner > resolved.submenuInnerPx + TOL && midInner < resolved.submenuOuterPx - TOL,
            )

            advanceUntilIdle()
            job.join()
            assertEquals(resolved.submenuOuterPx, arcsAt(reveal.value).first().band.innerPx, TOL)
        }
    }

    @Test
    fun `selecting a partially hovered wedge continues from its current radius`() = runTest {
        withContext(VirtualFrameClock()) {
            val animation = ItemAnimation()
            val hover = launch { animation.expansion.animateTo(1f, tween(300, easing = LinearEasing)) }

            advanceTimeBy(120)
            runCurrent()
            val midHover = animation.expansion.value
            val radiusBefore = resolved.mainOuterFor(midHover)
            hover.cancel()

            // Commit mid-hover, exactly as beginOpen() does.
            launch { animation.expansion.animateTo(2f, tween(300, easing = LinearEasing)) }
            advanceTimeBy(16)
            runCurrent()
            val radiusAfter = resolved.mainOuterFor(animation.expansion.value)

            assertTrue("radius snapped inward on commit", radiusAfter >= radiusBefore - 1f)
            assertTrue(
                "radius jumped to the hover/selected radius in a single frame",
                radiusAfter - radiusBefore < (resolved.mainSelectedOuterPx - resolved.mainOuterPx) * 0.3f,
            )
        }
    }
}
