package com.feanor08.onering.style

import androidx.compose.runtime.Immutable

/** Interaction policy switches. */
@ConsistentCopyVisibility
@Immutable
public data class OneRingBehavior internal constructor(
    /** Whether pressing the open parent wedge (now showing X) closes the submenu, on down. */
    public val closeOnParentPress: Boolean,
    /** Whether moving the pointer outside the active band clears the highlight. */
    public val cancelOnExitBand: Boolean,
    /** Whether [OneRing] performs haptic feedback on highlight change, commit, open, and close. */
    public val hapticsEnabled: Boolean,
    /** Whether entering a seek control plays the `0 -> value+overshoot -> value` affordance hint. */
    public val seekHintEnabled: Boolean,
    /** How far past the real value the hint overshoots, as a fraction of the value range. */
    public val seekHintOvershootFraction: Float,
)
