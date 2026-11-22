package com.feanor08.onering.feature

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colours the badges use when a feature is not on (an active badge takes its colour from the feature itself). */
@Immutable
public data class OneRingFeatureColors(
    public val idleFill: Color,
    public val idleBorder: Color,
    /** The icon of a feature that is idle or turned off, and the colour of the slash on a turned-off one. */
    public val idleIcon: Color,
    /** The icon of a feature that is on, over its colour. */
    public val activeIcon: Color,
)

@Immutable
public data class OneRingFeatureStyle(
    /** The badge's diameter. A whole number of pixels on the target screen keeps its edge clean. */
    public val badgeSize: Dp,
    /** The icon's size inside the badge: the icon's own grid in pixels (a 40 unit icon at 40 px) draws every edge on a pixel. */
    public val iconSize: Dp,
    /** The clear space between the ring and a badge, kept as the ring grows and shrinks. */
    public val ringGap: Dp,
    /** The clear space between neighbouring badges in a row. */
    public val spacing: Dp,
    public val borderWidth: Dp,
    /** The turned-off slash: its width, and the clear gap cut into the icon beside it. */
    public val slashWidth: Dp,
    public val slashGap: Dp,
    /** An active feature gives out [pulseCount] pulses together, staggered by [pulseStaggerMs], then again every [pulsePeriodMs]. */
    public val pulseCount: Int,
    public val pulsePeriodMs: Int,
    public val pulseDurationMs: Int,
    public val pulseStaggerMs: Int,
    public val pulseSpread: Dp,
    public val pulseWidth: Dp,
    /** The ride between the rows (scaled down for a short shift within a row), and the colour fade. */
    public val travelMs: Int,
    public val colorFadeMs: Int,
    /**
     * How far a resting badge moves out from the ring to let a badge ride across it: enough for the rider to pass without touching (a badge's diameter and a
     * little more). It moves out as the rider comes, and back as it goes on.
     */
    public val sidestep: Dp,
    public val colors: OneRingFeatureColors,
)

public object OneRingFeatureDefaults {
    public fun colors(
        idleFill: Color = Color(0xFF1C1C21),
        idleBorder: Color = Color(0x33FFFFFF),
        idleIcon: Color = Color(0xFF8A8A94),
        activeIcon: Color = Color.White,
    ): OneRingFeatureColors = OneRingFeatureColors(idleFill, idleBorder, idleIcon, activeIcon)

    public fun style(
        badgeSize: Dp = 40.dp,
        iconSize: Dp = 40.dp,
        ringGap: Dp = 12.dp,
        spacing: Dp = 8.dp,
        borderWidth: Dp = 2.dp,
        slashWidth: Dp = 3.dp,
        slashGap: Dp = 2.dp,
        pulseCount: Int = 2,
        pulsePeriodMs: Int = 5_000,
        pulseDurationMs: Int = 900,
        pulseStaggerMs: Int = 350,
        pulseSpread: Dp = 20.dp,
        pulseWidth: Dp = 2.dp,
        travelMs: Int = 1_400,
        colorFadeMs: Int = 300,
        sidestep: Dp = 44.dp,
        colors: OneRingFeatureColors = colors(),
    ): OneRingFeatureStyle = OneRingFeatureStyle(
        badgeSize, iconSize, ringGap, spacing, borderWidth, slashWidth, slashGap,
        pulseCount, pulsePeriodMs, pulseDurationMs, pulseStaggerMs, pulseSpread, pulseWidth, travelMs, colorFadeMs, sidestep, colors,
    )

    /**
     * The margin a host must leave round the ring (inside the bounds it gives [OneRingFeatures]) so that badges clear the ring wherever it reaches and
     * a resting badge has room to move aside for one riding across it: the ring's furthest reach ([maxOuterFraction] of [ringRadius], the selected wedge),
     * the gap, the whole badge, and the sidestep.
     */
    public fun requiredMargin(ringRadius: Dp, maxOuterFraction: Float, style: OneRingFeatureStyle): Dp =
        ringRadius * maxOuterFraction + style.ringGap + style.badgeSize + style.sidestep - ringRadius
}
