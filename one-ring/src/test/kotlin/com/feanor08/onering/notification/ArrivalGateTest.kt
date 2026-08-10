package com.feanor08.onering.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalGateTest {

    @Test fun `a new post while visible plays the arrival`() {
        assertTrue(shouldPlayArrival(visible = true, latestSequence = 2L, lastAnnouncedSequence = 1L))
    }

    @Test fun `nothing new while visible does not replay`() {
        assertFalse(shouldPlayArrival(visible = true, latestSequence = 1L, lastAnnouncedSequence = 1L))
    }

    @Test fun `a new post while hidden never plays behind the submenu`() {
        assertFalse(shouldPlayArrival(visible = false, latestSequence = 2L, lastAnnouncedSequence = 1L))
    }

    @Test fun `returning to visible after a hidden post does not replay once the watermark is advanced`() {
        // Simulates the host's own LaunchedEffect: while hidden, the watermark
        // is advanced without playing (see advancedWatermark), so becoming
        // visible again afterward reads as "nothing new".
        val latestWhilePostedHidden = 2L
        val watermarkAfterHiddenPost = advancedWatermark(latestWhilePostedHidden)
        assertFalse(shouldPlayArrival(visible = true, latestSequence = latestWhilePostedHidden, lastAnnouncedSequence = watermarkAfterHiddenPost))
    }

    @Test fun `a brand new post right as the host becomes visible still plays`() {
        val watermarkAfterHiddenPost = advancedWatermark(2L)
        assertTrue(shouldPlayArrival(visible = true, latestSequence = 3L, lastAnnouncedSequence = watermarkAfterHiddenPost))
    }

    @Test fun `an empty feed (sequence 0) never plays an arrival`() {
        assertFalse(shouldPlayArrival(visible = true, latestSequence = 0L, lastAnnouncedSequence = 0L))
    }
}
