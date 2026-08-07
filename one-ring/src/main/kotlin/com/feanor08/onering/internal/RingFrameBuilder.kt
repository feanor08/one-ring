package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.style.OneRingColors

/**
 * Builds the visual [RingFrame] for the main ring. [expansionOf]/[alphaOf]
 * supply each item's current animated expansion (0 = rest, 1 = hover, 2 =
 * selected) and opacity -- see [ItemAnimation]. This is a pure function of its
 * inputs, with no `DrawScope` or animation-timer dependency, so frame
 * sequences are plain-JUnit-testable for continuity.
 */
internal object RingFrameBuilder {
    fun buildIdle(items: List<OneRingItem>, resolved: ResolvedRing, colors: OneRingColors): RingFrame =
        build(items, resolved, colors, expansionOf = { 0f })

    fun build(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        colors: OneRingColors,
        expansionOf: (itemId: String) -> Float,
        alphaOf: (itemId: String) -> Float = { 1f },
    ): RingFrame {
        val arcs = items.mapIndexedNotNull { index, item ->
            val span = resolved.mainSpans.getOrNull(index) ?: return@mapIndexedNotNull null
            val expansion = expansionOf(item.id)
            val active = expansion > 0.03f
            val fill = when {
                !item.enabled -> colors.sectorDisabled
                active -> colors.sectorHighlighted
                index % 2 == 0 -> colors.sector
                else -> colors.sectorAlternate
            }
            val stroke = if (active) colors.strokeHighlighted else colors.stroke
            ArcVisual(
                itemIndex = index,
                span = span,
                band = RadialBand(resolved.mainInnerPx, resolved.mainOuterFor(expansion)),
                fill = fill,
                stroke = stroke,
                strokeWidthPx = resolved.sectorStrokeWidthPx,
                alpha = alphaOf(item.id),
            )
        }

        val centerCircle = CircleVisual(
            center = resolved.centerPx,
            radiusPx = resolved.centerRadiusPx,
            fill = colors.center,
        )

        return RingFrame(centerPx = resolved.centerPx, arcs = arcs, centerCircle = centerCircle)
    }
}
