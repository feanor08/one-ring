package com.feanor08.onering.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Dumb draw pass: paints a [RingFrame] with zero conditionals about logical state. */
internal fun DrawScope.drawRing(frame: RingFrame, pathPool: RingPathPool) {
    frame.arcs.forEachIndexed { index, arc ->
        if (arc.alpha <= 0f) return@forEachIndexed
        val path = pathPool.annularSector(index, frame.centerPx, arc.span, arc.band)
        drawPath(path, color = arc.fill, alpha = arc.alpha)
        if (arc.strokeWidthPx > 0f) {
            drawPath(path, color = arc.stroke, alpha = arc.alpha, style = Stroke(width = arc.strokeWidthPx))
        }
    }

    frame.centerCircle?.let { circle ->
        drawCircle(color = circle.fill, radius = circle.radiusPx, center = circle.center)
    }
}
