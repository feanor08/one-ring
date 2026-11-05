package com.feanor08.onering

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feanor08.onering.geometry.SubmenuAlignment
import com.feanor08.onering.style.OneRingBehavior
import com.feanor08.onering.style.OneRingColors
import com.feanor08.onering.style.OneRingDimensions
import com.feanor08.onering.style.OneRingMotion
import com.feanor08.onering.style.OneRingStyle
import com.feanor08.onering.style.RadialSize

/**
 * Every default value for [OneRing]'s configuration objects, and the only place
 * design constants live. The dimension fractions reproduce the reference
 * prototype's 500x500-viewBox proportions exactly -- see docs/interaction-spec.md.
 */
public object OneRingDefaults {

    /** Cubic-bezier matching the prototype's `1 - (1-t)^3` ease-out-cubic transitions. */
    private val EaseOutCubic: CubicBezierEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    /**
     * A soft, symmetric ease-in-out: zero velocity at both the start and the
     * end, unlike [EaseOutCubic] (fast start). Used for
     * [OneRingMotion.toggleFlip], where the motion should read as one
     * deliberate, restrained state change rather than a snappy commit --
     * never springy/bouncy, smooth at both endpoints.
     */
    private val EaseInOutCubic: CubicBezierEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

    public fun style(
        colors: OneRingColors = colors(),
        dimensions: OneRingDimensions = dimensions(),
        motion: OneRingMotion = motion(),
        behavior: OneRingBehavior = behavior(),
    ): OneRingStyle = OneRingStyle(colors, dimensions, motion, behavior)

    public fun colors(
        sector: Color = Color(0xFF3A3A3A),
        sectorAlternate: Color = Color(0xFF454545),
        sectorHighlighted: Color = Color(0xFF356FA2),
        sectorSelected: Color = sectorHighlighted,
        sectorDisabled: Color = Color(0xFF2A2A2A),
        // Muted, desaturated red/green sitting at the same darkness as
        // `sector`/`sectorAlternate` -- deliberately not alarm-red or
        // success-toast green, so a toggle wedge still reads as part of the
        // same neutral ring rather than an error/status indicator.
        sectorToggleOff: Color = Color(0xFF4A3236),
        sectorToggleOffHighlighted: Color = Color(0xFF5C3E43),
        sectorToggleOn: Color = Color(0xFF2E4A3B),
        sectorToggleOnHighlighted: Color = Color(0xFF3A5C49),
        stroke: Color = Color(0xFF1B1B1B),
        strokeHighlighted: Color = Color(0xFF5AA9FF),
        content: Color = Color.White,
        contentHighlighted: Color = Color.White,
        contentDisabled: Color = Color(0xFF9DA3AD),
        center: Color = Color(0xFF1B1B1B),
        seekTrack: Color = Color(0xFF3A3A3A),
        seekProgress: Color = Color(0xFF5AA9FF),
        seekThumb: Color = Color.White,
        seekThumbBorder: Color = Color(0xFF5AA9FF),
    ): OneRingColors = OneRingColors(
        sector = sector,
        sectorAlternate = sectorAlternate,
        sectorHighlighted = sectorHighlighted,
        sectorSelected = sectorSelected,
        sectorDisabled = sectorDisabled,
        sectorToggleOff = sectorToggleOff,
        sectorToggleOffHighlighted = sectorToggleOffHighlighted,
        sectorToggleOn = sectorToggleOn,
        sectorToggleOnHighlighted = sectorToggleOnHighlighted,
        stroke = stroke,
        strokeHighlighted = strokeHighlighted,
        content = content,
        contentHighlighted = contentHighlighted,
        contentDisabled = contentDisabled,
        center = center,
        seekTrack = seekTrack,
        seekProgress = seekProgress,
        seekThumb = seekThumb,
        seekThumbBorder = seekThumbBorder,
    )

    public fun dimensions(
        mainInner: RadialSize = RadialSize.Fraction(0.552f),
        mainOuter: RadialSize = RadialSize.Fraction(0.808f),
        mainHoverOuter: RadialSize = RadialSize.Fraction(0.880f),
        mainSelectedOuter: RadialSize = RadialSize.Fraction(0.952f),
        submenuInner: RadialSize = RadialSize.Fraction(0.280f),
        submenuOuter: RadialSize = RadialSize.Fraction(0.512f),
        submenuHoverOuter: RadialSize = RadialSize.Fraction(0.560f),
        centerRadius: RadialSize = RadialSize.Fraction(0.270f),
        coreOverlayRadius: RadialSize = RadialSize.Fraction(0.54f),
        startAngleDeg: Float = 0f,
        totalSweepDeg: Float = 360f,
        sectorGap: Dp = 2.dp,
        sectorStrokeWidth: Dp = 2.dp,
        touchSlopRadial: Dp = 18.dp,
        maxSubmenuItemSweepDeg: Float = 110f,
        submenuAlignment: SubmenuAlignment = SubmenuAlignment.Fill,
        seekThumbRadius: Dp = 7.dp,
        toggleRestOffsetFraction: Float = 0.45f,
        toggleHighlightGrowthFraction: Float = 0.22f,
    ): OneRingDimensions = OneRingDimensions(
        mainInner = mainInner,
        mainOuter = mainOuter,
        mainHoverOuter = mainHoverOuter,
        mainSelectedOuter = mainSelectedOuter,
        submenuInner = submenuInner,
        submenuOuter = submenuOuter,
        submenuHoverOuter = submenuHoverOuter,
        centerRadius = centerRadius,
        coreOverlayRadius = coreOverlayRadius,
        startAngleDeg = startAngleDeg,
        totalSweepDeg = totalSweepDeg,
        sectorGap = sectorGap,
        sectorStrokeWidth = sectorStrokeWidth,
        touchSlopRadial = touchSlopRadial,
        maxSubmenuItemSweepDeg = maxSubmenuItemSweepDeg,
        submenuAlignment = submenuAlignment,
        seekThumbRadius = seekThumbRadius,
        toggleRestOffsetFraction = toggleRestOffsetFraction,
        toggleHighlightGrowthFraction = toggleHighlightGrowthFraction,
    )

    public fun motion(
        hover: AnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        select: AnimationSpec<Float> = tween(280, easing = EaseOutCubic),
        close: AnimationSpec<Float> = tween(250, easing = EaseOutCubic),
        submenuReveal: AnimationSpec<Float> = tween(280, easing = EaseOutCubic),
        // Short on purpose: an item's glyph and its close (X) glyph are faded in
        // separate, non-overlapping stages, so this runs twice per transition
        // (out before the geometry moves, in after it settles) and a long spec
        // would make the whole open/close feel sluggish.
        iconMorph: AnimationSpec<Float> = tween(110, easing = LinearEasing),
        siblingExit: AnimationSpec<Float> = tween(180, easing = EaseOutCubic),
        seekHintRise: AnimationSpec<Float> = tween(480, easing = EaseOutCubic),
        seekHintSettle: AnimationSpec<Float> = tween(190, easing = EaseOutCubic),
        toggleFlip: AnimationSpec<Float> = tween(320, easing = EaseInOutCubic),
    ): OneRingMotion = OneRingMotion(
        hover = hover,
        select = select,
        close = close,
        submenuReveal = submenuReveal,
        iconMorph = iconMorph,
        siblingExit = siblingExit,
        seekHintRise = seekHintRise,
        seekHintSettle = seekHintSettle,
        toggleFlip = toggleFlip,
    )

    public fun behavior(
        closeOnParentPress: Boolean = true,
        cancelOnExitBand: Boolean = true,
        hapticsEnabled: Boolean = true,
        seekHintEnabled: Boolean = true,
        seekHintOvershootFraction: Float = 0.05f,
    ): OneRingBehavior = OneRingBehavior(
        closeOnParentPress = closeOnParentPress,
        cancelOnExitBand = cancelOnExitBand,
        hapticsEnabled = hapticsEnabled,
        seekHintEnabled = seekHintEnabled,
        seekHintOvershootFraction = seekHintOvershootFraction,
    )

    /** A denser layout for smaller displays: tighter radii, thinner touch slop. */
    public fun compactStyle(): OneRingStyle = style(
        dimensions = dimensions(
            mainInner = RadialSize.Fraction(0.60f),
            mainOuter = RadialSize.Fraction(0.85f),
            mainHoverOuter = RadialSize.Fraction(0.90f),
            mainSelectedOuter = RadialSize.Fraction(0.97f),
            submenuInner = RadialSize.Fraction(0.35f),
            submenuOuter = RadialSize.Fraction(0.58f),
            submenuHoverOuter = RadialSize.Fraction(0.62f),
            centerRadius = RadialSize.Fraction(0.28f),
            touchSlopRadial = 10.dp,
        ),
    )

    /** Larger touch targets, higher-contrast highlight, and a bolder stroke for in-vehicle displays. */
    public fun automotiveStyle(): OneRingStyle = style(
        colors = colors(
            sectorHighlighted = Color(0xFF3D8BFF),
            strokeHighlighted = Color(0xFF7FB8FF),
        ),
        dimensions = dimensions(
            touchSlopRadial = 28.dp,
            sectorStrokeWidth = 3.dp,
            seekThumbRadius = 10.dp,
        ),
        behavior = behavior(hapticsEnabled = true),
    )

    /** Snappier, bouncier motion for a game radial menu / weapon wheel; no seek-hint-style hesitation. */
    public fun gameStyle(): OneRingStyle = style(
        motion = motion(
            hover = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
            select = tween(160, easing = EaseOutCubic),
            close = tween(140, easing = EaseOutCubic),
            submenuReveal = tween(160, easing = EaseOutCubic),
            siblingExit = tween(100, easing = EaseOutCubic),
            seekHintRise = tween(260, easing = EaseOutCubic),
            seekHintSettle = tween(120, easing = EaseOutCubic),
        ),
    )
}
