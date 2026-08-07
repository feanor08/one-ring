package com.feanor08.onering

import androidx.compose.ui.geometry.Size
import com.feanor08.onering.internal.FakeDensity
import com.feanor08.onering.internal.resolveRing
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every preset must resolve to sane, correctly-ordered geometry -- not just compile. */
class OneRingDefaultsPresetsTest {

    private val density = FakeDensity()

    private fun assertBandOrderingHolds(style: com.feanor08.onering.style.OneRingStyle) {
        val resolved = resolveRing(Size(600f, 600f), density, style.dimensions, itemCount = 6)
        assertTrue(resolved.centerRadiusPx < resolved.submenuInnerPx)
        assertTrue(resolved.submenuInnerPx < resolved.submenuOuterPx)
        assertTrue(resolved.submenuOuterPx < resolved.mainInnerPx)
        assertTrue(resolved.mainInnerPx < resolved.mainOuterPx)
        assertTrue(resolved.mainOuterPx < resolved.mainHoverOuterPx)
        assertTrue(resolved.mainHoverOuterPx < resolved.mainSelectedOuterPx)
    }

    @Test fun `default style has correctly ordered bands`() = assertBandOrderingHolds(OneRingDefaults.style())

    @Test fun `compactStyle has correctly ordered bands`() = assertBandOrderingHolds(OneRingDefaults.compactStyle())

    @Test fun `automotiveStyle has correctly ordered bands`() = assertBandOrderingHolds(
        OneRingDefaults.automotiveStyle(),
    )

    @Test fun `gameStyle has correctly ordered bands`() = assertBandOrderingHolds(OneRingDefaults.gameStyle())

    @Test fun `automotiveStyle has a larger touch slop than default`() {
        val default = OneRingDefaults.dimensions()
        val automotive = OneRingDefaults.automotiveStyle().dimensions
        assertTrue(automotive.touchSlopRadial > default.touchSlopRadial)
    }
}
