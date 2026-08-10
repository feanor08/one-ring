package com.feanor08.onering.notification

import com.feanor08.onering.OneRingMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationVisibilityTest {

    @Test fun `idle shows the host`() {
        assertTrue(notificationsVisible(OneRingMode.Idle))
    }

    @Test fun `tracking shows the host`() {
        assertTrue(notificationsVisible(OneRingMode.Tracking))
    }

    @Test fun `transitioning hides the host`() {
        assertFalse(notificationsVisible(OneRingMode.Transitioning))
    }

    @Test fun `submenu hides the host`() {
        assertFalse(notificationsVisible(OneRingMode.Submenu))
    }

    @Test fun `seek hides the host`() {
        assertFalse(notificationsVisible(OneRingMode.Seek))
    }

    @Test fun `every OneRingMode value is covered by an explicit branch`() {
        // Exhaustiveness is enforced by the `when` in notificationsVisible not
        // compiling without an else if a mode were added without updating it --
        // this test just pins down that all five current values are handled.
        OneRingMode.entries.forEach { mode -> notificationsVisible(mode) }
    }
}
