package com.feanor08.onering.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import com.feanor08.onering.OneRingIcon

/**
 * Renders an [OneRingIcon] at [size], honoring [OneRingIcon.Vector.tint] /
 * [OneRingIcon.Painted.tint] when the icon supplies its own, falling back to
 * [tint] otherwise. Shared by the ring's own wedge content ([RingContentLayer])
 * and the notification package's cards/arrival glyph, so every consumer of an
 * [OneRingIcon] renders it identically -- extracted here rather than duplicated
 * because the two call sites need different sizes (a wedge icon vs. a large
 * arrival glyph) but must otherwise behave the same.
 */
@Composable
internal fun RenderIcon(icon: OneRingIcon, tint: Color, size: Dp) {
    when (icon) {
        is OneRingIcon.None -> Unit
        is OneRingIcon.Vector -> Image(
            painter = rememberVectorPainter(icon.imageVector),
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = ColorFilter.tint(icon.tint ?: tint),
        )
        is OneRingIcon.Painted -> Image(
            painter = icon.painter,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = ColorFilter.tint(icon.tint ?: tint),
        )
        is OneRingIcon.Content -> icon.content()
    }
}
