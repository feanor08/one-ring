package com.feanor08.onering.style

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Immutable

/**
 * Every [AnimationSpec] the ring uses. Hover uses a spring (interrupted
 * constantly by wedge crossings, so it needs velocity continuity); mode
 * transitions use fixed-duration tweens for choreographed, predictable timing.
 * See docs/PLAN.md section 10 for why these are never `animateFloatAsState` or
 * `updateTransition` -- both would reintroduce the "selection stutter" bug.
 */
@ConsistentCopyVisibility
@Immutable
public data class OneRingMotion internal constructor(
    public val hover: AnimationSpec<Float>,
    public val select: AnimationSpec<Float>,
    public val close: AnimationSpec<Float>,
    public val submenuReveal: AnimationSpec<Float>,
    public val iconMorph: AnimationSpec<Float>,
    public val siblingExit: AnimationSpec<Float>,
    public val seekHintRise: AnimationSpec<Float>,
    public val seekHintSettle: AnimationSpec<Float>,
    /**
     * A [com.feanor08.onering.OneRingItem.Toggle]'s committed value change --
     * the single continuous transition driving its resting-radius grow/shrink
     * and its fill-tint blend together, deliberately soft and restrained
     * (smooth velocity at both ends, not a spring) rather than choreographed
     * in stages like [select]/[close].
     */
    public val toggleFlip: AnimationSpec<Float>,
)
