package com.feanor08.onering.internal

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Dumb draw pass: paints a [RingFrame] with zero conditionals about logical
 * state, except one -- [ArcVisual.isHighlighted]. Every arc's fill+stroke is
 * painted in `arcs` order first (a later wedge can still legitimately draw
 * over an earlier one's *fill*, e.g. an expanded wedge's neighbor), then
 * every highlighted arc's *stroke only* is redrawn as a final foreground
 * pass. Without that second pass, a wedge whose highlight outline is
 * followed in `arcs` order by another wedge sharing part of its radial band
 * (e.g. the open-submenu parent vs. its own children) could have one edge of
 * its outline silently painted over -- exactly the bug the reference HTML
 * prototype had to add its own final outline pass to fix. No physical gaps
 * between wedges are used to work around this; the draw order is the fix.
 */
internal fun DrawScope.drawRing(frame: RingFrame, pathPool: RingPathPool) {
    var poolIndex = 0

    // One sweep shader per distinct gradient in the frame (normally one or two), not one per wedge.
    var lastColors: List<Color>? = null
    var lastBrush: Brush? = null
    fun brushFor(colors: List<Color>): Brush {
        if (colors !== lastColors || lastBrush == null) {
            lastBrush = Brush.sweepGradient(colors = colors, center = frame.centerPx)
            lastColors = colors
        }
        return lastBrush!!
    }
    fun DrawScope.strokeArc(arc: ArcVisual, path: androidx.compose.ui.graphics.Path) {
        val gradient = arc.strokeGradient
        if (gradient != null && gradient.size >= 2) {
            drawPath(path, brush = brushFor(gradient), alpha = arc.alpha, style = Stroke(width = arc.strokeWidthPx))
        } else {
            drawPath(path, color = arc.stroke, alpha = arc.alpha, style = Stroke(width = arc.strokeWidthPx))
        }
    }

    frame.arcs.forEach { arc ->
        if (arc.alpha > 0f) {
            val path = pathPool.annularSector(poolIndex, frame.centerPx, arc.span, arc.band)
            drawPath(path, color = arc.fill, alpha = arc.alpha)
            if (arc.strokeWidthPx > 0f) {
                strokeArc(arc, path)
            }
        }
        poolIndex++
    }

    frame.arcs.forEach { arc ->
        if (arc.isHighlighted && arc.alpha > 0f && arc.strokeWidthPx > 0f) {
            val path = pathPool.annularSector(poolIndex, frame.centerPx, arc.span, arc.band)
            strokeArc(arc, path)
            poolIndex++
        }
    }

    frame.seek?.let { seek ->
        (seek.trackArcs + seek.fillArcs).forEach { arc ->
            val path = pathPool.annularSector(poolIndex, frame.centerPx, arc.span, arc.band)
            drawPath(path, color = arc.fill, alpha = arc.alpha)
            poolIndex++
        }
        drawCircle(color = seek.thumbBorderColor, radius = seek.thumbRadiusPx + 2.dp.toPx(), center = seek.thumbCenter)
        drawCircle(color = seek.thumbColor, radius = seek.thumbRadiusPx, center = seek.thumbCenter)
    }

    frame.centerCircle?.let { circle ->
        drawCircle(color = circle.fill, radius = circle.radiusPx, center = circle.center)
    }
}
