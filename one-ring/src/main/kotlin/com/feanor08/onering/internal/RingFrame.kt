package com.feanor08.onering.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand

/**
 * An immutable description of every arc and circle a ring should paint for one
 * frame. [RingFrameBuilder] produces this as a pure function of (items, logical
 * state, animation values, resolved geometry, style) with no `DrawScope` or
 * Compose UI dependency beyond value types -- so the "does the selected wedge
 * ever visually jump" invariant is a plain JUnit assertion, not a screenshot
 * test. See docs/PLAN.md sections 8.2 and 18.
 */
@Immutable
internal data class RingFrame(
    val centerPx: Offset,
    val arcs: List<ArcVisual>,
    val centerCircle: CircleVisual?,
    val seek: SeekVisual? = null,
)

@Immutable
internal data class ArcVisual(
    val itemIndex: Int,
    val span: AngularSpan,
    val band: RadialBand,
    val fill: Color,
    val stroke: Color,
    val strokeWidthPx: Float,
    val alpha: Float,
    /**
     * True while the pointer is currently over this wedge. [drawRing] redraws
     * every such arc's stroke in a final foreground pass after every wedge's
     * own fill+stroke has been painted, so a neighboring wedge drawn later
     * (or one whose radius happens to overlap) can never cover one side of
     * the highlight outline -- see that function's doc.
     */
    val isHighlighted: Boolean = false,
)

@Immutable
internal data class CircleVisual(val center: Offset, val radiusPx: Float, val fill: Color)

/** The active seek control's track background, filled progress, and thumb. */
@Immutable
internal data class SeekVisual(
    val trackArcs: List<ArcVisual>,
    val fillArcs: List<ArcVisual>,
    val thumbCenter: Offset,
    val thumbRadiusPx: Float,
    val thumbColor: Color,
    val thumbBorderColor: Color,
)
