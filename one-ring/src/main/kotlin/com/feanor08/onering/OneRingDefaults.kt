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
        stroke: Color = Color(0xFF1B1B1B),
        strokeHighlighted: Color = Color(0xFF5AA9FF),
        content: Color = Color.White,
        contentHighlighted: Color = Color.White,
        contentDisabled: Color = Color(0xFF9DA3AD),
        center: Color = Color(0xFF1B1B1B),
    ): OneRingColors = OneRingColors(
        sector = sector,
        sectorAlternate = sectorAlternate,
        sectorHighlighted = sectorHighlighted,
        sectorSelected = sectorSelected,
        sectorDisabled = sectorDisabled,
        stroke = stroke,
        strokeHighlighted = strokeHighlighted,
        content = content,
        contentHighlighted = contentHighlighted,
        contentDisabled = contentDisabled,
        center = center,
    )

    public fun dimensions(
        mainInner: RadialSize = RadialSize.Fraction(0.552f),
        mainOuter: RadialSize = RadialSize.Fraction(0.808f),
        mainHoverOuter: RadialSize = RadialSize.Fraction(0.880f),
        mainSelectedOuter: RadialSize = RadialSize.Fraction(0.952f),
        submenuInner: RadialSize = RadialSize.Fraction(0.280f),
        submenuOuter: RadialSize = RadialSize.Fraction(0.512f),
        submenuHoverOuter: RadialSize = RadialSize.Fraction(0.560f),
        centerRadius: RadialSize = RadialSize.Fraction(0.216f),
        startAngleDeg: Float = 0f,
        totalSweepDeg: Float = 360f,
        sectorGap: Dp = 2.dp,
        sectorStrokeWidth: Dp = 2.dp,
        touchSlopRadial: Dp = 18.dp,
        maxSubmenuItemSweepDeg: Float = 110f,
        submenuAlignment: SubmenuAlignment = SubmenuAlignment.Fill,
    ): OneRingDimensions = OneRingDimensions(
        mainInner = mainInner,
        mainOuter = mainOuter,
        mainHoverOuter = mainHoverOuter,
        mainSelectedOuter = mainSelectedOuter,
        submenuInner = submenuInner,
        submenuOuter = submenuOuter,
        submenuHoverOuter = submenuHoverOuter,
        centerRadius = centerRadius,
        startAngleDeg = startAngleDeg,
        totalSweepDeg = totalSweepDeg,
        sectorGap = sectorGap,
        sectorStrokeWidth = sectorStrokeWidth,
        touchSlopRadial = touchSlopRadial,
        maxSubmenuItemSweepDeg = maxSubmenuItemSweepDeg,
        submenuAlignment = submenuAlignment,
    )

    public fun motion(
        hover: AnimationSpec<Float> = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        select: AnimationSpec<Float> = tween(280, easing = EaseOutCubic),
        close: AnimationSpec<Float> = tween(250, easing = EaseOutCubic),
        submenuReveal: AnimationSpec<Float> = tween(280, easing = EaseOutCubic),
        iconMorph: AnimationSpec<Float> = tween(200, easing = LinearEasing),
        siblingExit: AnimationSpec<Float> = tween(180, easing = EaseOutCubic),
    ): OneRingMotion = OneRingMotion(
        hover = hover,
        select = select,
        close = close,
        submenuReveal = submenuReveal,
        iconMorph = iconMorph,
        siblingExit = siblingExit,
    )

    public fun behavior(
        closeOnParentPress: Boolean = true,
        cancelOnExitBand: Boolean = true,
        hapticsEnabled: Boolean = true,
    ): OneRingBehavior = OneRingBehavior(
        closeOnParentPress = closeOnParentPress,
        cancelOnExitBand = cancelOnExitBand,
        hapticsEnabled = hapticsEnabled,
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
        ),
    )
}
