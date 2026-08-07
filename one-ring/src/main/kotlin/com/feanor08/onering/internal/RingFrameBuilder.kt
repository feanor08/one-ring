package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.style.OneRingColors
import com.feanor08.onering.style.OneRingDimensions

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
        val arcs = buildArcs(
            items = items,
            spans = resolved.mainSpans,
            innerRadiusPx = resolved.mainInnerPx,
            resolved = resolved,
            colors = colors,
            expansionOf = expansionOf,
            alphaOf = alphaOf,
            outerRadiusFor = { expansion -> resolved.mainOuterFor(expansion) },
        )

        val centerCircle = CircleVisual(
            center = resolved.centerPx,
            radiusPx = resolved.centerRadiusPx,
            fill = colors.center,
        )

        return RingFrame(centerPx = resolved.centerPx, arcs = arcs, centerCircle = centerCircle)
    }

    /**
     * The open submenu's child wedges. [reveal] (0..1) is the shared open/close
     * choreography progress: the outer edge stays pinned at the submenu's outer
     * radius while the inner radius animates inward from it, and every child
     * fades in together -- matching the reference prototype exactly (see
     * docs/interaction-spec.md). Per-child [expansionOf] only becomes visually
     * meaningful once [reveal] settles at 1 (children aren't interactive mid-
     * transition, since input is gated during Opening/Closing).
     */
    fun buildSubmenuArcs(
        children: List<OneRingItem>,
        parentSpan: AngularSpan,
        resolved: ResolvedRing,
        dimensions: OneRingDimensions,
        colors: OneRingColors,
        reveal: Float,
        expansionOf: (itemId: String) -> Float,
    ): List<ArcVisual> {
        val childSpans = submenuChildSpans(resolved, dimensions, parentSpan, children.size)
        val clampedReveal = reveal.coerceIn(0f, 1f)
        val innerPx = lerp(resolved.submenuOuterPx, resolved.submenuInnerPx, clampedReveal)

        return buildArcs(
            items = children,
            spans = childSpans,
            innerRadiusPx = innerPx,
            resolved = resolved,
            colors = colors,
            expansionOf = expansionOf,
            alphaOf = { clampedReveal },
            outerRadiusFor = { expansion -> resolved.submenuOuterFor(expansion) },
        )
    }

    private inline fun buildArcs(
        items: List<OneRingItem>,
        spans: List<AngularSpan>,
        innerRadiusPx: Float,
        resolved: ResolvedRing,
        colors: OneRingColors,
        expansionOf: (itemId: String) -> Float,
        alphaOf: (itemId: String) -> Float,
        outerRadiusFor: (expansion: Float) -> Float,
    ): List<ArcVisual> = items.mapIndexedNotNull { index, item ->
        val span = spans.getOrNull(index) ?: return@mapIndexedNotNull null
        val expansion = expansionOf(item.id)
        val active = expansion > 0.03f
        val fill = when {
            !item.enabled -> colors.sectorDisabled
            active -> colors.sectorHighlighted
            index % 2 == 0 -> colors.sector
            else -> colors.sectorAlternate
        }
        val stroke = if (active) colors.strokeHighlighted else colors.stroke
        val outerPx = outerRadiusFor(expansion).coerceAtLeast(innerRadiusPx)
        ArcVisual(
            itemIndex = index,
            span = span,
            band = RadialBand(innerRadiusPx, outerPx),
            fill = fill,
            stroke = stroke,
            strokeWidthPx = resolved.sectorStrokeWidthPx,
            alpha = alphaOf(item.id),
        )
    }
}
