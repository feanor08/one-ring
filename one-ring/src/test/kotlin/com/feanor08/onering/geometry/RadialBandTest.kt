package com.feanor08.onering.geometry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialBandTest {

    @Test fun `contains respects inner and outer bounds`() {
        val band = RadialBand(100f, 200f)
        assertTrue(band.contains(100f))
        assertTrue(band.contains(150f))
        assertTrue(band.contains(200f))
        assertFalse(band.contains(99f))
        assertFalse(band.contains(201f))
    }

    @Test fun `contains with slop extends both edges`() {
        val band = RadialBand(100f, 200f)
        assertFalse(band.contains(90f))
        assertTrue(band.contains(90f, slopPx = 15f))
        assertTrue(band.contains(210f, slopPx = 15f))
    }

    @Test fun `thickness and mid are derived correctly`() {
        val band = RadialBand(100f, 200f)
        assertTrue(band.thicknessPx == 100f)
        assertTrue(band.midPx == 150f)
    }

    @Test fun `outer less than inner is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RadialBand(200f, 100f) }
    }
}
