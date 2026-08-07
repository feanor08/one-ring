package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RingFrameBuilderTest {

    private val density = FakeDensity()
    private val colors = OneRingDefaults.colors()

    private fun items(count: Int, disabledIndex: Int? = null) = List(count) { i ->
        OneRingItem.Action(
            id = "item$i",
            label = "Item $i",
            icon = OneRingIcon.None,
            enabled = i != disabledIndex,
        )
    }

    @Test fun `idle frame has one arc per item`() {
        val list = items(6)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.buildIdle(list, resolved, colors)
        assertEquals(6, frame.arcs.size)
    }

    @Test fun `idle frame alternates sector colors by index`() {
        val list = items(6)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.buildIdle(list, resolved, colors)
        assertEquals(colors.sector, frame.arcs[0].fill)
        assertEquals(colors.sectorAlternate, frame.arcs[1].fill)
        assertEquals(colors.sector, frame.arcs[2].fill)
    }

    @Test fun `disabled item uses the disabled color regardless of index parity`() {
        val list = items(6, disabledIndex = 3)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.buildIdle(list, resolved, colors)
        assertEquals(colors.sectorDisabled, frame.arcs[3].fill)
        assertNotEquals(colors.sectorDisabled, frame.arcs[2].fill)
    }

    @Test fun `every arc uses the main band and every wedge is fully opaque at rest`() {
        val list = items(4)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.buildIdle(list, resolved, colors)
        frame.arcs.forEach { arc ->
            assertEquals(resolved.mainBand, arc.band)
            assertEquals(1f, arc.alpha, 0f)
        }
    }

    @Test fun `center circle is present and uses the center color`() {
        val list = items(6)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.buildIdle(list, resolved, colors)
        assertEquals(colors.center, frame.centerCircle?.fill)
        assertEquals(resolved.centerRadiusPx, frame.centerCircle?.radiusPx)
    }
}
