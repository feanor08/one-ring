package com.feanor08.onering.style

import androidx.compose.runtime.Immutable

/** The complete visual + behavioral configuration for a ring. */
@ConsistentCopyVisibility
@Immutable
public data class OneRingStyle internal constructor(
    public val colors: OneRingColors,
    public val dimensions: OneRingDimensions,
    public val motion: OneRingMotion,
    public val behavior: OneRingBehavior,
)
