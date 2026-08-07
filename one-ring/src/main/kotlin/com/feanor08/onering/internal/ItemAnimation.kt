package com.feanor08.onering.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable

/**
 * Continuous animation state for one item, keyed by id and owned by
 * [RingAnimations] -- which is owned by [com.feanor08.onering.OneRingState],
 * so its lifetime spans every mode change. This is what makes the "selection
 * stutter" bug structurally impossible: [Animatable.animateTo] always
 * continues from the current value and velocity, so a commit mid-hover-
 * animation never snaps. See docs/PLAN.md section 10.
 *
 * [expansion] is unitless: 0 = rest, 1 = hover. (2 = fully selected is added
 * in Phase 4 once the selection transition exists.)
 */
@Stable
internal class ItemAnimation {
    val expansion = Animatable(0f)
}
