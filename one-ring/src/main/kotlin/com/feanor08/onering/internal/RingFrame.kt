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
internal data class RingFrame(val centerPx: Offset, val arcs: List<ArcVisual>, val centerCircle: CircleVisual?)

@Immutable
internal data class ArcVisual(
    val itemIndex: Int,
    val span: AngularSpan,
    val band: RadialBand,
    val fill: Color,
    val stroke: Color,
    val strokeWidthPx: Float,
    val alpha: Float,
)

@Immutable
internal data class CircleVisual(val center: Offset, val radiusPx: Float, val fill: Color)
