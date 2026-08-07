package com.feanor08.onering.style

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * A radius expressed relative to the ring's base radius (`min(width, height) / 2`),
 * as an absolute [Dp], or clamped between the two -- so the ring can scale
 * sensibly from a phone to a tablet to an automotive display.
 */
@Immutable
public sealed interface RadialSize {
    @JvmInline
    public value class Fraction(public val value: Float) : RadialSize

    @JvmInline
    public value class Absolute(public val dp: Dp) : RadialSize

    @Immutable
    public data class Clamped(public val fraction: Float, public val min: Dp, public val max: Dp) : RadialSize
}
