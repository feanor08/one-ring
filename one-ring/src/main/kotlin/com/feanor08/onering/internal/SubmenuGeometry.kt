package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.SubmenuLayout
import com.feanor08.onering.style.OneRingDimensions

/**
 * The one place child wedge spans for an open submenu are computed, shared by
 * the renderer ([RingFrameBuilder]) and the interaction controller
 * ([RingInteractionController]) so drawing and hit-testing can never disagree.
 */
internal fun submenuChildSpans(
    resolved: ResolvedRing,
    dimensions: OneRingDimensions,
    parentSpan: AngularSpan,
    children: List<OneRingItem>,
): List<AngularSpan> = SubmenuLayout.weighted(
    parent = parentSpan,
    weights = children.map { it.weight },
    gapDeg = resolved.submenuGapDeg,
    maxChildSweepDeg = dimensions.maxSubmenuItemSweepDeg,
    alignment = dimensions.submenuAlignment,
)
