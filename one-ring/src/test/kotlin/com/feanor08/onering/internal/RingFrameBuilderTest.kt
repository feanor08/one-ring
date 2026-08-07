package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test fun `expansion above the hover threshold uses the highlighted color and a larger outer radius`() {
        val list = items(6)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.build(list, resolved, colors, expansionOf = { id ->
            if (id ==
                "item2"
            ) {
                2f
            } else {
                0f
            }
        })
        val selected = frame.arcs.first { it.itemIndex == 2 }
        assertEquals(colors.sectorHighlighted, selected.fill)
        assertEquals(resolved.mainSelectedOuterPx, selected.band.outerPx, 0.01f)
        val rest = frame.arcs.first { it.itemIndex == 0 }
        assertEquals(resolved.mainOuterPx, rest.band.outerPx, 0.01f)
    }

    @Test fun `alphaOf drives per-arc opacity independently of expansion`() {
        val list = items(6)
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), list.size)
        val frame = RingFrameBuilder.build(
            list,
            resolved,
            colors,
            expansionOf = { 0f },
            alphaOf = { id -> if (id == "item1") 0f else 1f },
        )
        assertEquals(0f, frame.arcs.first { it.itemIndex == 1 }.alpha, 0f)
        assertEquals(1f, frame.arcs.first { it.itemIndex == 0 }.alpha, 0f)
    }

    @Test fun `submenu arcs are pinned at the outer edge and expand inward as reveal rises`() {
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), itemCount = 6)
        val parentSpan = resolved.mainSpans[0]
        val children = items(3)

        val start = RingFrameBuilder.buildSubmenuArcs(
            children,
            parentSpan,
            resolved,
            OneRingDefaults.dimensions(),
            colors,
            reveal = 0f,
            expansionOf = { 0f },
        )
        val mid = RingFrameBuilder.buildSubmenuArcs(
            children,
            parentSpan,
            resolved,
            OneRingDefaults.dimensions(),
            colors,
            reveal = 0.5f,
            expansionOf = { 0f },
        )
        val settled = RingFrameBuilder.buildSubmenuArcs(
            children,
            parentSpan,
            resolved,
            OneRingDefaults.dimensions(),
            colors,
            reveal = 1f,
            expansionOf = { 0f },
        )

        // outer edge is pinned at the submenu's outer radius throughout
        listOf(start, mid, settled).forEach { arcs ->
            arcs.forEach { assertEquals(resolved.submenuOuterPx, it.band.outerPx, 0.01f) }
        }
        // inner radius animates inward: thin strip (inner == outer) at reveal=0,
        // full thickness (inner == submenuInnerPx) once settled
        assertEquals(resolved.submenuOuterPx, start[0].band.innerPx, 0.01f)
        assertEquals(resolved.submenuInnerPx, settled[0].band.innerPx, 0.01f)
        assertTrue(mid[0].band.innerPx in resolved.submenuInnerPx..resolved.submenuOuterPx)

        // alpha fades in together with reveal
        assertEquals(0f, start[0].alpha, 0.01f)
        assertEquals(1f, settled[0].alpha, 0.01f)
    }

    @Test fun `submenu children divide the parent's angular complement`() {
        val resolved = resolveRing(Size(600f, 600f), density, OneRingDefaults.dimensions(), itemCount = 6)
        val parentSpan = resolved.mainSpans[0] // 60deg wide
        val children = items(4)

        val arcs = RingFrameBuilder.buildSubmenuArcs(
            children,
            parentSpan,
            resolved,
            OneRingDefaults.dimensions(),
            colors,
            reveal = 1f,
            expansionOf = { 0f },
        )

        assertEquals(4, arcs.size)
        val totalSweep = arcs.sumOf { it.span.sweepDeg.toDouble() }.toFloat()
        assertTrue("expected close to 300deg (360 - 60 parent), was $totalSweep", totalSweep in 295f..300f)
    }
}
