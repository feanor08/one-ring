package com.feanor08.onering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import com.feanor08.onering.internal.RingFrameBuilder
import com.feanor08.onering.internal.RingPathPool
import com.feanor08.onering.internal.drawRing
import com.feanor08.onering.internal.resolveRing
import com.feanor08.onering.style.OneRingStyle

/**
 * A radial interaction wheel: press, scrub, release-to-commit. This entry point
 * currently renders the ring's resting state only -- pointer interaction,
 * selection, and nested submenus land in later phases (see docs/PLAN.md).
 *
 * @param items the top-level wedges, in clockwise order starting at
 *   [com.feanor08.onering.style.OneRingDimensions.startAngleDeg].
 * @param style colors, sizes, and motion; see [OneRingDefaults.style].
 */
@Composable
public fun OneRing(
    items: List<OneRingItem>,
    modifier: Modifier = Modifier,
    style: OneRingStyle = OneRingDefaults.style(),
) {
    val density = LocalDensity.current
    val pathPool = remember { RingPathPool() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasSize = Size(size.width, size.height)
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@Canvas

        val resolved = resolveRing(canvasSize, density, style.dimensions, items.size)
        val frame = RingFrameBuilder.buildIdle(items, resolved, style.colors)
        drawRing(frame, pathPool)
    }
}
