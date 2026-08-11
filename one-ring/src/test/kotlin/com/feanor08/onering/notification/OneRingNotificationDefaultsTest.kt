package com.feanor08.onering.notification

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRingNotificationDefaultsTest {

    @Test fun `default style constructs without throwing`() {
        OneRingNotificationDefaults.style()
    }

    @Test fun `lightColors constructs without throwing and differs from the dark default`() {
        val light = OneRingNotificationDefaults.lightColors()
        val dark = OneRingNotificationDefaults.colors()
        assertTrue("expected a light card background", light.cardBackground.red > 0.9f)
        assertTrue("expected a dark title on a light card", light.title.red < 0.3f)
        assertTrue("light and dark presets must actually differ", light != dark)
    }

    @Test fun `lightColors arrival background is fully opaque`() {
        assertEquals(1f, OneRingNotificationDefaults.lightColors().arrivalBackground.alpha, 0.001f)
    }

    @Test fun `a translucent arrivalBackground is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.colors(arrivalBackground = Color(0x80000000))
        }
    }

    @Test fun `an opaque custom arrivalBackground is accepted`() {
        OneRingNotificationDefaults.colors(arrivalBackground = Color(0xFF224466))
    }

    @Test fun `default card proportions target a wide rectangle, not a square, for one message`() {
        val dimensions = OneRingNotificationDefaults.dimensions()
        // Minimum height as a fraction of width (both expressed as fractions
        // of the core overlay diameter) -- the floor a short card settles at.
        val minHeightOverWidth = dimensions.minCardHeightFraction / dimensions.maxCardWidthFraction
        assertTrue(
            "expected a wide-rectangle floor ratio (~0.35-0.45), was $minHeightOverWidth",
            minHeightOverWidth in 0.30f..0.48f,
        )
    }

    @Test fun `maxCardWidthFraction outside 0 to 1 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(maxCardWidthFraction = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(maxCardWidthFraction = -0.1f)
        }
    }

    @Test fun `minCardHeightFraction outside 0 to 1 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(minCardHeightFraction = 1.2f)
        }
    }

    @Test fun `edge reduction fractions outside 0 to 1 throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(edgeWidthReductionFraction = -0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(edgeAlphaReductionFraction = 2f)
        }
    }

    @Test fun `content padding fractions outside 0 to 1 throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(topContentPaddingFraction = 1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneRingNotificationDefaults.dimensions(bottomContentPaddingFraction = -0.01f)
        }
    }
}
