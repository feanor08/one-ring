package com.feanor08.onering.notification

import org.junit.Assert.assertThrows
import org.junit.Test

class OneRingNotificationDefaultsTest {

    @Test fun `default style constructs without throwing`() {
        OneRingNotificationDefaults.style()
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
