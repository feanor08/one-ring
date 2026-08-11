package com.feanor08.onering.notification

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Every color the notification feed draws with. None of these are business/
 * source-specific (that's entirely [OneRingNotificationPost.accentColor]'s
 * job) -- these are the neutral surface/text colors a host theme supplies.
 * See [OneRingNotificationDefaults.colors] (dark) and
 * [OneRingNotificationDefaults.lightColors] (light); a caller with its own
 * light/dark convention can supply any combination of these directly.
 */
@Immutable
public data class OneRingNotificationColors internal constructor(
    public val cardBackground: Color,
    public val title: Color,
    public val body: Color,
    public val secondaryText: Color,
    /**
     * The arrival overlay's fill. Must be fully opaque (alpha == 1) -- the
     * whole point of the arrival splash is that nothing behind it can show
     * through (see [OneRingNotificationHost]'s "Arrival ordering" doc); a
     * translucent value would silently reintroduce that bug. Enforced below
     * rather than left as a documentation-only convention.
     */
    public val arrivalBackground: Color,
) {
    init {
        require(arrivalBackground.alpha >= 0.999f) {
            "arrivalBackground must be fully opaque (alpha ~1), was alpha=${arrivalBackground.alpha}. " +
                "A translucent arrival background would let notification cards show through the arrival " +
                "splash, which it must never do -- see OneRingNotificationHost's class doc."
        }
    }
}

/**
 * Every size/fraction the notification feed lays out with. Fraction fields
 * are relative to the `coreOverlay` diameter (see
 * [com.feanor08.onering.style.OneRingDimensions.coreOverlayRadius]), not the
 * whole ring, so the feed scales sensibly independent of ring size.
 */
@Immutable
public data class OneRingNotificationDimensions internal constructor(
    /** A card's width at the viewport's vertical center, as a fraction of the core overlay's diameter. */
    public val maxCardWidthFraction: Float,
    /** How much further a card's width shrinks (via `scaleX`) at the circular viewport's top/bottom edge. */
    public val edgeWidthReductionFraction: Float,
    /** How much a card's alpha drops at the circular viewport's top/bottom edge. */
    public val edgeAlphaReductionFraction: Float,
    /** A card's minimum height as a fraction of the core overlay's diameter -- a floor, not a fixed height. */
    public val minCardHeightFraction: Float,
    public val cornerRadius: Dp,
    public val borderWidth: Dp,
    /** Vertical gap between cards within a source group. */
    public val cardSpacing: Dp,
    /** Vertical gap between source groups. */
    public val groupSpacing: Dp,
    public val horizontalContentPadding: Dp,
    /** Extra top content padding, as a fraction of the viewport height, so the first card can reach center. */
    public val topContentPaddingFraction: Float,
    /** Extra bottom content padding, as a fraction of the viewport height, so the last card can reach center. */
    public val bottomContentPaddingFraction: Float,
) {
    init {
        require(maxCardWidthFraction in 0f..1f) { "maxCardWidthFraction must be in 0..1, was $maxCardWidthFraction" }
        require(edgeWidthReductionFraction in 0f..1f) {
            "edgeWidthReductionFraction must be in 0..1, was $edgeWidthReductionFraction"
        }
        require(edgeAlphaReductionFraction in 0f..1f) {
            "edgeAlphaReductionFraction must be in 0..1, was $edgeAlphaReductionFraction"
        }
        require(minCardHeightFraction in 0f..1f) {
            "minCardHeightFraction must be in 0..1, was $minCardHeightFraction"
        }
        require(topContentPaddingFraction in 0f..1f) {
            "topContentPaddingFraction must be in 0..1, was $topContentPaddingFraction"
        }
        require(bottomContentPaddingFraction in 0f..1f) {
            "bottomContentPaddingFraction must be in 0..1, was $bottomContentPaddingFraction"
        }
    }
}

/**
 * Every animation the notification feed runs. The arrival sequence is
 * time-scheduled (not purely spec-driven), so its phases are expressed as
 * explicit millisecond marks alongside their specs -- see
 * [OneRingNotificationHost]'s arrival overlay implementation.
 */
@Immutable
public data class OneRingNotificationMotion internal constructor(
    /** How the whole feed fades in/out as [com.feanor08.onering.OneRingMode] changes -- see docs/notifications.md. */
    public val visibilityFade: AnimationSpec<Float>,
    /** The arrival circle growing from a point to fill the core overlay. */
    public val arrivalGrow: AnimationSpec<Float>,
    public val arrivalGrowDurationMillis: Long,
    /** How long after the arrival starts its icon begins fading/scaling in. */
    public val arrivalIconDelayMillis: Long,
    /** The icon's own fade/scale-in, starting after [arrivalIconDelayMillis]. */
    public val arrivalIconFade: AnimationSpec<Float>,
    /** How long after the arrival starts the icon-only state is held before exiting. */
    public val arrivalHoldUntilMillis: Long,
    public val arrivalExit: AnimationSpec<Float>,
    public val arrivalExitDurationMillis: Long,
    /** A swipe that didn't cross the dismiss threshold, animating back to rest. */
    public val swipeSettle: AnimationSpec<Float>,
    /** A swipe that crossed the dismiss threshold, animating the rest of the way off-screen. */
    public val swipeDismiss: AnimationSpec<Float>,
)

/** The complete visual + motion configuration for [OneRingNotificationHost]. */
@Immutable
public data class OneRingNotificationStyle internal constructor(
    public val colors: OneRingNotificationColors,
    public val dimensions: OneRingNotificationDimensions,
    public val motion: OneRingNotificationMotion,
)
