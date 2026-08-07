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
 * [expansion] is unitless: 0 = rest, 1 = hover, 2 = fully selected (the
 * selected wedge growing into the close control, see docs/PLAN.md section 12).
 * [contentAlpha] is the wedge's own opacity, used to fade siblings out when
 * another item is committed.
 */
@Stable
internal class ItemAnimation {
    val expansion = Animatable(0f)
    val contentAlpha = Animatable(1f)
}
