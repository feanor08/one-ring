package com.feanor08.onering.style

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.feanor08.onering.geometry.SubmenuAlignment

/**
 * Every size and angle the ring's geometry engine is configured with. Construct
 * via [com.feanor08.onering.OneRingDefaults.dimensions]; the defaults reproduce
 * the reference prototype's proportions exactly -- see docs/interaction-spec.md.
 */
@ConsistentCopyVisibility
@Immutable
public data class OneRingDimensions internal constructor(
    public val mainInner: RadialSize,
    public val mainOuter: RadialSize,
    public val mainHoverOuter: RadialSize,
    public val mainSelectedOuter: RadialSize,
    public val submenuInner: RadialSize,
    public val submenuOuter: RadialSize,
    public val submenuHoverOuter: RadialSize,
    public val centerRadius: RadialSize,
    /**
     * The radius of [com.feanor08.onering.OneRing]'s large `coreOverlay` slot
     * (distinct from the small `center` slot, which uses [centerRadius]). Must
     * stay smaller than [mainInner] so overlay content never overlaps the main
     * ring; the default (0.43) leaves real clearance under every built-in
     * preset, including [com.feanor08.onering.OneRingDefaults.compactStyle].
     */
    public val coreOverlayRadius: RadialSize,
    public val startAngleDeg: Float,
    public val totalSweepDeg: Float,
    public val sectorGap: Dp,
    public val sectorStrokeWidth: Dp,
    public val touchSlopRadial: Dp,
    public val maxSubmenuItemSweepDeg: Float,
    public val submenuAlignment: SubmenuAlignment,
    public val seekThumbRadius: Dp,
)
