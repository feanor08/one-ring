package com.feanor08.onering.style

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every color the ring draws. Construct via [com.feanor08.onering.OneRingDefaults.colors] --
 * the constructor is internal so field additions later don't break source
 * compatibility for callers using named/defaulted arguments through the factory.
 */
@ConsistentCopyVisibility
@Immutable
public data class OneRingColors internal constructor(
    public val sector: Color,
    public val sectorAlternate: Color,
    public val sectorHighlighted: Color,
    public val sectorSelected: Color,
    public val sectorDisabled: Color,
    /**
     * A [com.feanor08.onering.OneRingItem.Toggle] at rest while unchecked/off,
     * and while checked/on (respectively [sectorToggleOn]) -- a muted tint,
     * not the ordinary highlight accent, blended smoothly between the two on
     * every toggle commit. Boolean state is communicated by fill tint and
     * radial height only, never by [stroke]/[strokeHighlighted], which stay
     * the ring's ordinary neutral colors for every item including toggles.
     */
    public val sectorToggleOff: Color,
    /** [sectorToggleOff], but while highlighted (pointer currently over the wedge). */
    public val sectorToggleOffHighlighted: Color,
    /** The checked/on counterpart to [sectorToggleOff]. */
    public val sectorToggleOn: Color,
    /** [sectorToggleOn], but while highlighted (pointer currently over the wedge). */
    public val sectorToggleOnHighlighted: Color,
    public val stroke: Color,
    public val strokeHighlighted: Color,
    public val content: Color,
    public val contentHighlighted: Color,
    public val contentDisabled: Color,
    public val center: Color,
    public val seekTrack: Color,
    public val seekProgress: Color,
    public val seekThumb: Color,
    public val seekThumbBorder: Color,
)
