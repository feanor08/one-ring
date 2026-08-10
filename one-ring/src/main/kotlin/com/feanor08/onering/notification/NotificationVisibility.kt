package com.feanor08.onering.notification

import com.feanor08.onering.OneRingMode

/**
 * Whether [OneRingNotificationHost]'s feed should be visible for the ring's
 * current [OneRingMode] -- visible only while the top-level main ring is the
 * thing on screen ([OneRingMode.Idle]/[OneRingMode.Tracking]), hidden the
 * moment a submenu or seek starts opening and for as long as it's open (see
 * docs/notifications.md's "automatic hide during submenu/seek" section).
 * State is never cleared when hidden -- only the host's own visual fade
 * reacts to this.
 */
internal fun notificationsVisible(mode: OneRingMode): Boolean = when (mode) {
    OneRingMode.Idle, OneRingMode.Tracking -> true
    OneRingMode.Transitioning, OneRingMode.Submenu, OneRingMode.Seek -> false
}
