package com.feanor08.onering

import androidx.compose.runtime.Stable

/** Context available to [OneRing]'s `center` and `coreOverlay` content slots. */
@Stable
public class OneRingCenterScope internal constructor(
    /** The currently open menu's id, or null at the top level. */
    public val currentMenuId: String?,
    /** The currently highlighted item's id (at whichever level is active), or null. */
    public val highlightedId: String?,
    /** The ring's current interaction mode -- see [OneRingMode]. */
    public val mode: OneRingMode,
)
