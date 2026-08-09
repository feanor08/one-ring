package com.feanor08.onering.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.RingLayout
import com.feanor08.onering.style.OneRingDimensions
import com.feanor08.onering.style.RadialSize
import kotlin.math.min

/**
 * [OneRingDimensions] resolved into concrete pixels and item spans for one
 * actual canvas size. Recomputed only when size, density, dimensions, or the
 * items' structural shape ([com.feanor08.onering.internal.layoutKey]) change --
 * never on every recomposition.
 */
@Immutable
internal data class ResolvedRing(
    val centerPx: Offset,
    val baseRadiusPx: Float,
    val mainInnerPx: Float,
    val mainOuterPx: Float,
    val mainHoverOuterPx: Float,
    val mainSelectedOuterPx: Float,
    val submenuInnerPx: Float,
    val submenuOuterPx: Float,
    val submenuHoverOuterPx: Float,
    val centerRadiusPx: Float,
    val sectorStrokeWidthPx: Float,
    val touchSlopRadialPx: Float,
    val mainSpans: List<AngularSpan>,
    val submenuGapDeg: Float,
    val seekThumbRadiusPx: Float,
) {
    val mainBand: RadialBand get() = RadialBand(mainInnerPx, mainOuterPx)

    /** [expansion] is 0 = rest, 1 = hover, 2 = fully selected -- see docs/PLAN.md section 10.4. */
    fun mainOuterFor(expansion: Float): Float = when {
        expansion <= 0f -> mainOuterPx
        expansion <= 1f -> lerp(mainOuterPx, mainHoverOuterPx, expansion)
        else -> lerp(mainHoverOuterPx, mainSelectedOuterPx, (expansion - 1f).coerceAtMost(1f))
    }

    fun submenuOuterFor(hover: Float): Float = lerp(submenuOuterPx, submenuHoverOuterPx, hover.coerceIn(0f, 1f))
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun RadialSize.resolvePx(baseRadiusPx: Float, density: Density): Float = when (this) {
    is RadialSize.Fraction -> baseRadiusPx * value
    is RadialSize.Absolute -> with(density) { dp.toPx() }
    is RadialSize.Clamped -> with(density) {
        (baseRadiusPx * fraction).coerceIn(min.toPx(), max.toPx())
    }
}

/** Equal-weight convenience overload -- identical to passing [itemCount] weights of `1f`. */
internal fun resolveRing(sizePx: Size, density: Density, dimensions: OneRingDimensions, itemCount: Int): ResolvedRing =
    resolveRing(sizePx, density, dimensions, List(itemCount.coerceAtLeast(0)) { 1f })

/**
 * @param weights each item's relative angular size, in item order. Pass a list
 *   of `1f` (or use the [resolveRing] overload taking a count) for equal wedges.
 */
internal fun resolveRing(
    sizePx: Size,
    density: Density,
    dimensions: OneRingDimensions,
    weights: List<Float>,
): ResolvedRing {
    val baseRadius = min(sizePx.width, sizePx.height) / 2f
    val center = Offset(sizePx.width / 2f, sizePx.height / 2f)

    val mainOuterPx = dimensions.mainOuter.resolvePx(baseRadius, density)
    val gapPx = with(density) { dimensions.sectorGap.toPx() }
    // Convert a fixed pixel gap to a wedge-count-independent angular gap at the
    // main band's outer radius, so the visual gap stays constant in dp.
    val gapDeg = if (mainOuterPx > 0f) (gapPx / mainOuterPx) * (180f / Math.PI.toFloat()) else 0f

    val mainSpans = RingLayout.weighted(
        weights = weights,
        startDeg = dimensions.startAngleDeg,
        totalSweepDeg = dimensions.totalSweepDeg,
        gapDeg = gapDeg,
        centerFirstItem = true,
    )

    val submenuOuterPx = dimensions.submenuOuter.resolvePx(baseRadius, density)
    val submenuGapDeg = if (submenuOuterPx > 0f) (gapPx / submenuOuterPx) * (180f / Math.PI.toFloat()) else 0f

    return ResolvedRing(
        centerPx = center,
        baseRadiusPx = baseRadius,
        mainInnerPx = dimensions.mainInner.resolvePx(baseRadius, density),
        mainOuterPx = mainOuterPx,
        mainHoverOuterPx = dimensions.mainHoverOuter.resolvePx(baseRadius, density),
        mainSelectedOuterPx = dimensions.mainSelectedOuter.resolvePx(baseRadius, density),
        submenuInnerPx = dimensions.submenuInner.resolvePx(baseRadius, density),
        submenuOuterPx = submenuOuterPx,
        submenuHoverOuterPx = dimensions.submenuHoverOuter.resolvePx(baseRadius, density),
        centerRadiusPx = dimensions.centerRadius.resolvePx(baseRadius, density),
        sectorStrokeWidthPx = with(density) { dimensions.sectorStrokeWidth.toPx() },
        touchSlopRadialPx = with(density) { dimensions.touchSlopRadial.toPx() },
        mainSpans = mainSpans,
        submenuGapDeg = submenuGapDeg,
        seekThumbRadiusPx = with(density) { dimensions.seekThumbRadius.toPx() },
    )
}
