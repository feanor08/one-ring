package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldFillTest {

    private val density = FakeDensity()
    private val colors = OneRingDefaults.colors()

    private val items = listOf(
        OneRingItem.Action("tap", "Tap", OneRingIcon.None),
        OneRingItem.Action("hold", "Hold", OneRingIcon.None, holdMs = 800L),
        OneRingItem.Action("off", "Off", OneRingIcon.None, enabled = false, holdMs = 800L),
        OneRingItem.Action("other", "Other", OneRingIcon.None),
    )
    private val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), items.size)

    private fun fills(progress: Float) = RingFrameBuilder.buildHoldFills(
        items = items,
        spans = resolved.mainSpans,
        innerRadiusPx = resolved.mainInnerPx,
        colors = colors,
        expansionOf = { 1f },
        alphaOf = { 1f },
        progressOf = { id -> if (id == "hold" || id == "off") progress else 0f },
        outerRadiusFor = { expansion -> resolved.mainOuterFor(expansion) },
    )

    @Test fun `no progress draws nothing`() {
        assertTrue(fills(0f).isEmpty())
    }

    @Test fun `only an enabled hold action draws a fill`() {
        val arcs = fills(0.5f)
        assertEquals(1, arcs.size)
        assertEquals(1, arcs[0].itemIndex)
    }

    @Test fun `the fill grows from the inner edge towards the outer edge`() {
        val half = fills(0.5f)[0].band
        val full = fills(1f)[0].band
        assertEquals(resolved.mainInnerPx, half.innerPx, 0.01f)
        assertEquals(resolved.mainInnerPx, full.innerPx, 0.01f)
        assertTrue(half.outerPx > half.innerPx)
        assertTrue(full.outerPx > half.outerPx)
        assertEquals(resolved.mainOuterFor(1f), full.outerPx, 0.01f)
    }

    @Test fun `the fill covers exactly its wedge's span`() {
        assertEquals(resolved.mainSpans[1], fills(1f)[0].span)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative hold time is rejected`() {
        OneRingItem.Action("bad", "Bad", holdMs = -1L)
    }
}
