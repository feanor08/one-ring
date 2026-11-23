package com.feanor08.onering.notification

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How [OneRingNotificationHost] lays the feed out.
 *
 * [Circular] is the original presentation, inside a [com.feanor08.onering.OneRing]'s `coreOverlay`: every size is a fraction of the core overlay's
 * diameter, the feed is clipped to a circle, cards narrow and fade toward the circle's top and bottom, and a new arrival is a circle that grows to fill it.
 * It is the default, and nothing about it changes.
 *
 * [Rectangular] is for a feed in a rectangle of its own (a shelf beside or above a ring): every size is absolute, the feed is clipped to the
 * rectangle it is given, nothing narrows, and a new arrival is a translucent slab that slides down from [Rectangular.arrivalTravel] above the
 * rectangle into it. Source grouping, newest-first order, stacking, swipe dismissal and the arrival ordering guarantees are the same. A tap on a card does
 * not expand a group in place (there is no room): it is reported through [OneRingNotificationHost]'s `onCardTap` so the caller can show the detail.
 */
public sealed interface OneRingNotificationPresentation {
    /** The original presentation: inside a ring's core overlay. */
    public data object Circular : OneRingNotificationPresentation

    @Immutable
    public data class Rectangular(
        /** A card's width. */
        public val cardWidth: Dp,
        /** A card's minimum height. */
        public val cardMinHeight: Dp,
        /** The most a collapsed card (a single thread, or a stack's front card) shows of itself: longer content is clipped, not shortened. */
        public val cardMaxCollapsedHeight: Dp,
        /** The gap between cards of one source (not visible while a stack is collapsed). */
        public val cardSpacing: Dp = 6.dp,
        /** The gap between sources. */
        public val groupSpacing: Dp = 6.dp,
        /** How much cards at the top and bottom edge of the rectangle fade (0 = not at all). */
        public val edgeAlphaReduction: Float = 0.35f,
        /** The space above and below a card's text: 8 dp in the circular feed, less here, so a 40 dp card shows its title and its source line whole. */
        public val cardVerticalPadding: Dp = 4.dp,
        /**
         * True: a card is one line, the source's icon, the sender, and at the far right how many messages the thread has so far (1, 2, 3), with no subtitle
         * and no thread count on a stack's front card. False: the source label with a "· N messages" suffix, as in the circular feed.
         */
        public val countOnlySubtitle: Boolean = false,
        /**
         * True: every notification is in one stack, newest in front, whatever its source (a chat, a fault and an update stack up together). False: one stack
         * per source, the stacks listed one under the other, as in the circular feed.
         */
        public val singleStack: Boolean = false,
        /** How far above the rectangle's top the arrival slab starts. */
        public val arrivalTravel: Dp = 56.dp,
        /** The slab's opacity while it covers the feed: translucent, but the feed under it still shows the state from before the arrival. */
        public val arrivalAlpha: Float = 0.92f,
        /**
         * True: the arrival slab slides in sideways, from beyond the feed's right edge to its place at the top (right to left), instead of down from above it
         * ([arrivalTravel] is then not used). The feed clips it to its own bounds, so it appears at the edge and never over what is beside the feed.
         */
        public val arrivalFromRight: Boolean = false,
    ) : OneRingNotificationPresentation {
        init {
            require(edgeAlphaReduction in 0f..1f) { "edgeAlphaReduction must be in 0..1, was $edgeAlphaReduction" }
            require(arrivalAlpha in 0f..1f) { "arrivalAlpha must be in 0..1, was $arrivalAlpha" }
            require(cardMinHeight <= cardMaxCollapsedHeight) { "cardMinHeight ($cardMinHeight) must not exceed cardMaxCollapsedHeight ($cardMaxCollapsedHeight)" }
        }
    }
}
