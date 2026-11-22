package com.feanor08.onering.feature

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OneRingFeatureTest {
    private fun feature(colors: List<Color>) =
        OneRingFeature("f", OneRingIcon.None, "f", colors, OneRingFeatureStatus.Idle)

    @Test fun anActiveValueIsAOneBasedIndexIntoTheColours() {
        val f = feature(listOf(Color.Red, Color.Green, Color.Blue))
        assertEquals(Color.Red, f.colorFor(1))
        assertEquals(Color.Green, f.colorFor(2))
        assertEquals(Color.Blue, f.colorFor(3))
    }

    @Test fun aValueBeyondTheColoursTakesTheLastAndOneBelowTakesTheFirst() {
        val f = feature(listOf(Color.Red, Color.Green))
        assertEquals(Color.Green, f.colorFor(9))
        assertEquals(Color.Red, f.colorFor(0))
    }

    @Test fun aFeatureNeedsAColour() {
        assertThrows(IllegalArgumentException::class.java) { feature(emptyList()) }
    }

    @Test fun theMarginHoldsTheRingsFurthestReachTheGapTheWholeBadgeAndTheSidestep() {
        val style = OneRingFeatureDefaults.style(badgeSize = 40.dp, ringGap = 12.dp, sidestep = 44.dp)
        val margin = OneRingFeatureDefaults.requiredMargin(ringRadius = 140.dp, maxOuterFraction = 0.97f, style = style)
        assertEquals(140f * 0.97f + 12f + 40f + 44f - 140f, margin.value, 0.001f)
    }

    @Test fun aReleasingFeatureTakesTheColourOfItsValueAndKeepsItsDuration() {
        val r = OneRingFeatureStatus.Releasing(durationMs = 7_000, value = 2)
        val f = OneRingFeature("f", OneRingIcon.None, "f", listOf(Color.Red, Color.Green), r)
        assertEquals(Color.Green, f.colorFor((f.status as OneRingFeatureStatus.Releasing).value))
        assertEquals(7_000, (f.status as OneRingFeatureStatus.Releasing).durationMs)
        assertEquals(OneRingFeatureStatus.Releasing(7_000, 2), r)                       // equal each time it is read, so an effect keyed on it is not restarted
        assertEquals(2, OneRingFeatureStatus.Releasing(5_000).value)                    // the default value
    }
}
