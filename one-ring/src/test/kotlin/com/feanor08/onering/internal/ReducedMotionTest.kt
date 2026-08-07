package com.feanor08.onering.internal

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.tween
import com.feanor08.onering.OneRingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test fun `animations enabled returns the motion unchanged`() {
        val motion = OneRingDefaults.motion()
        assertEquals(motion, effectiveMotion(motion, animationsEnabled = true))
    }

    @Test fun `animations disabled replaces every spec with an instant snap`() {
        val motion = OneRingDefaults.motion(select = tween(9999))
        val reduced = effectiveMotion(motion, animationsEnabled = false)

        assertTrue(reduced.hover is SnapSpec)
        assertTrue(reduced.select is SnapSpec)
        assertTrue(reduced.close is SnapSpec)
        assertTrue(reduced.submenuReveal is SnapSpec)
        assertTrue(reduced.iconMorph is SnapSpec)
        assertTrue(reduced.siblingExit is SnapSpec)
        assertNotEquals(motion, reduced)
    }
}
