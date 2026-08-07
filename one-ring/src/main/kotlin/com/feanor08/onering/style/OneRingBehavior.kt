package com.feanor08.onering.style

import androidx.compose.runtime.Immutable

/**
 * Interaction policy switches. Grows as later phases add seek and haptics
 * behavior; kept minimal for now to match what's actually wired up.
 */
@ConsistentCopyVisibility
@Immutable
public data class OneRingBehavior internal constructor(
    /** Whether pressing the open parent wedge (now showing X) closes the submenu, on down. */
    public val closeOnParentPress: Boolean,
    /** Whether moving the pointer outside the active band clears the highlight. */
    public val cancelOnExitBand: Boolean,
)
