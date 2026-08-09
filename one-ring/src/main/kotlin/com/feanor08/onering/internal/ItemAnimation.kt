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
 * The three opacity channels are deliberately separate rather than one
 * "progress" the renderer derives everything from:
 *
 * - [contentAlpha] is the **wedge's** opacity -- what fades siblings out when
 *   another item is committed. The arc itself uses this.
 * - [iconAlpha] is the item's **own glyph** opacity.
 * - [backIconAlpha] is the **close (X) glyph** opacity for an item currently
 *   acting as the back control.
 *
 * Keeping the last two apart is what allows the icon -> X change to be *staged*
 * (icon out, geometry transforms, X in) instead of a crossfade: a single
 * progress driving `icon = 1 - p` and `x = p` would always show both at once
 * around the midpoint. Nothing schedules them to overlap -- see
 * [RingInteractionController]'s open/close sequences.
 */
@Stable
internal class ItemAnimation {
    val expansion = Animatable(0f)
    val contentAlpha = Animatable(1f)
    val iconAlpha = Animatable(1f)
    val backIconAlpha = Animatable(0f)
}
