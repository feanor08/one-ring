package com.feanor08.onering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual content for a ring item. The library never hardcodes icons -- consumers
 * supply an [ImageVector], a [Painter], arbitrary `@Composable` content, or
 * nothing at all.
 */
public sealed interface OneRingIcon {
    public object None : OneRingIcon

    @Immutable
    public data class Vector(public val imageVector: ImageVector, public val tint: Color? = null) : OneRingIcon

    @Immutable
    public data class Painted(public val painter: Painter, public val tint: Color? = null) : OneRingIcon

    /** Arbitrary composable content, sized and positioned by the ring's content layout. */
    public class Content(public val content: @Composable () -> Unit) : OneRingIcon
}
