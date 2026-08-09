package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Offset
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.SeekTrack
import com.feanor08.onering.geometry.polarOffset
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
        build(items, resolved, colors, expansionOf = { 0f }, highlightedId = null)

    fun build(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        colors: OneRingColors,
        expansionOf: (itemId: String) -> Float,
        alphaOf: (itemId: String) -> Float = { 1f },
        highlightedId: String? = null,
        // See OneRingState.mainSiblingReveal: 1 = every non-active wedge at
        // full radius (the resting default), lerping toward 0 (collapsed to
        // the inner edge) while a submenu/seek is open. [activeItemId] is
        // exempted -- its radius stays purely a function of its own
        // continuous expansion, exactly as before this parameter existed.
        mainSiblingRevealProgress: Float = 1f,
        activeItemId: String? = null,
    ): RingFrame {
        val arcs = buildArcs(
            items = items,
            spans = resolved.mainSpans,
            innerRadiusPx = resolved.mainInnerPx,
            resolved = resolved,
            colors = colors,
            expansionOf = expansionOf,
            alphaOf = alphaOf,
            highlightedId = highlightedId,
            outerRadiusFor = { expansion -> resolved.mainOuterFor(expansion) },
            siblingRevealProgress = mainSiblingRevealProgress,
            activeItemId = activeItemId,
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
     * radius while the inner radius animates inward from it -- matching the
     * reference prototype exactly (see docs/interaction-spec.md). Each child's
     * own [alphaOf] is multiplied into [reveal], so a seek entry's "other
     * children fade in place" reads correctly during and after the reveal.
     * Per-child [expansionOf] only becomes visually meaningful once [reveal]
     * settles at 1 (children aren't interactive mid-transition, since input is
     * gated during Opening/Closing).
     */
    fun buildSubmenuArcs(
        children: List<OneRingItem>,
        parentSpan: AngularSpan,
        resolved: ResolvedRing,
        dimensions: OneRingDimensions,
        colors: OneRingColors,
        reveal: Float,
        expansionOf: (itemId: String) -> Float,
        alphaOf: (itemId: String) -> Float = { 1f },
        highlightedId: String? = null,
    ): List<ArcVisual> {
        val childSpans = submenuChildSpans(resolved, dimensions, parentSpan, children)
        val clampedReveal = reveal.coerceIn(0f, 1f)
        val innerPx = lerp(resolved.submenuOuterPx, resolved.submenuInnerPx, clampedReveal)

        return buildArcs(
            items = children,
            spans = childSpans,
            innerRadiusPx = innerPx,
            resolved = resolved,
            colors = colors,
            expansionOf = expansionOf,
            alphaOf = { id -> clampedReveal * alphaOf(id) },
            highlightedId = highlightedId,
            outerRadiusFor = { expansion -> resolved.submenuOuterFor(expansion) },
        )
    }

    /**
     * The active seek control's track, filled progress, and thumb. [fraction]
     * (0..1) is the seek's current *display* value -- separate from the real
     * controlled value so the intro hint can animate without emitting
     * `onValueChange` (see docs/interaction-spec.md's seek hint section).
     */
    fun buildSeekVisual(
        track: SeekTrack,
        fraction: Float,
        band: RadialBand,
        resolved: ResolvedRing,
        colors: OneRingColors,
    ): SeekVisual? {
        if (track.isEmpty) return null
        val clampedFraction = fraction.coerceIn(0f, 1f)

        val trackArcs = track.segments.mapIndexed { index, span ->
            ArcVisual(
                itemIndex = index,
                span = span,
                band = band,
                fill = colors.seekTrack,
                stroke = colors.seekTrack,
                strokeWidthPx = 0f,
                alpha = 1f,
            )
        }

        val fillSpans = mutableListOf<AngularSpan>()
        track.fillSpans(clampedFraction, fillSpans)
        val fillArcs = fillSpans.mapIndexed { index, span ->
            ArcVisual(
                itemIndex = index,
                span = span,
                band = band,
                fill = colors.seekProgress,
                stroke = colors.seekProgress,
                strokeWidthPx = 0f,
                alpha = 1f,
            )
        }

        val thumbAngle = track.angleAt(clampedFraction)
        val thumbPoint = polarOffset(band.midPx, thumbAngle)

        return SeekVisual(
            trackArcs = trackArcs,
            fillArcs = fillArcs,
            thumbCenter = Offset(resolved.centerPx.x + thumbPoint.x, resolved.centerPx.y + thumbPoint.y),
            thumbRadiusPx = resolved.seekThumbRadiusPx,
            thumbColor = colors.seekThumb,
            thumbBorderColor = colors.seekThumbBorder,
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
        highlightedId: String?,
        outerRadiusFor: (expansion: Float) -> Float,
        siblingRevealProgress: Float = 1f,
        activeItemId: String? = null,
    ): List<ArcVisual> = items.mapIndexedNotNull { index, item ->
        val span = spans.getOrNull(index) ?: return@mapIndexedNotNull null
        val expansion = expansionOf(item.id)
        // Accent tracks the *pointer*, never structural state. Deriving it from
        // expansion instead would leave an open menu's parent (which sits at the
        // selected radius while it acts as the back control) permanently lit up,
        // as if it were still mid-selection. Geometry and highlight are separate
        // concerns: the wedge can stay expanded and still look at rest.
        val highlighted = highlightedId != null && item.id == highlightedId
        val fill = when {
            !item.enabled -> colors.sectorDisabled
            highlighted -> colors.sectorHighlighted
            index % 2 == 0 -> colors.sector
            else -> colors.sectorAlternate
        }
        val stroke = if (highlighted) colors.strokeHighlighted else colors.stroke
        val rawOuterPx = outerRadiusFor(expansion)
        // The active item (an open menu's parent, or an active main seek) is
        // never subject to the shared sibling reveal -- its radius is purely a
        // function of its own continuous expansion. Every other item lerps
        // between the inner edge (reveal=0, zero thickness) and its normal
        // radius (reveal=1), so a whole ring of siblings collapses/regrows as
        // one wedge-shaped transform instead of an instant appear/disappear.
        val outerPx = if (item.id == activeItemId) {
            rawOuterPx.coerceAtLeast(innerRadiusPx)
        } else {
            lerp(innerRadiusPx, rawOuterPx, siblingRevealProgress.coerceIn(0f, 1f)).coerceAtLeast(innerRadiusPx)
        }
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
