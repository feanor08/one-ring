package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem
import com.feanor08.onering.style.OneRingColors

/**
 * Builds the visual [RingFrame] for the ring's resting (idle) state: every main
 * wedge at rest radius, alternating fill, no highlight. Pointer-driven
 * highlight/selection frames are added in later phases as this function grows
 * additional parameters (highlighted item, per-item expansion) -- deliberately
 * kept to the idle case only for now to match what Phase 2 actually renders.
 */
internal object RingFrameBuilder {
    fun buildIdle(items: List<OneRingItem>, resolved: ResolvedRing, colors: OneRingColors): RingFrame {
        val arcs = items.mapIndexed { index, item ->
            val span = resolved.mainSpans.getOrElse(index) { return@mapIndexed null }
            val fill = when {
                !item.enabled -> colors.sectorDisabled
                index % 2 == 0 -> colors.sector
                else -> colors.sectorAlternate
            }
            ArcVisual(
                itemIndex = index,
                span = span,
                band = resolved.mainBand,
                fill = fill,
                stroke = colors.stroke,
                strokeWidthPx = resolved.sectorStrokeWidthPx,
                alpha = 1f,
            )
        }.filterNotNull()

        val centerCircle = CircleVisual(
            center = resolved.centerPx,
            radiusPx = resolved.centerRadiusPx,
            fill = colors.center,
        )

        return RingFrame(centerPx = resolved.centerPx, arcs = arcs, centerCircle = centerCircle)
    }
}
