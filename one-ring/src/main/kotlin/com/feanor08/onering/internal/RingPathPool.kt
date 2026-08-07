package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.polarOffset

/**
 * Reuses [Path] objects across frames (`reset()` instead of allocating) so the
 * steady-state draw loop, which can run at up to 120Hz, doesn't allocate.
 */
internal class RingPathPool {
    private val paths = mutableListOf<Path>()

    /** An annular-sector path for the given [index] slot, rebuilt in place. */
    fun annularSector(index: Int, center: Offset, span: AngularSpan, band: RadialBand): Path {
        while (paths.size <= index) paths.add(Path())
        val path = paths[index]
        path.reset()

        if (span.sweepDeg <= 0f || band.thicknessPx <= 0f) return path

        // This package's angle convention is 0 = 12 o'clock, clockwise (see
        // docs/geometry.md). Compose's arc angle convention is 0 = 3 o'clock,
        // clockwise -- the same -90 degree offset baked into polarOffset().
        val composeStartAngle = span.startDeg - 90f

        val outerRect = Rect(
            center.x - band.outerPx,
            center.y - band.outerPx,
            center.x + band.outerPx,
            center.y + band.outerPx,
        )
        val innerRect = Rect(
            center.x - band.innerPx,
            center.y - band.innerPx,
            center.x + band.innerPx,
            center.y + band.innerPx,
        )

        path.arcTo(outerRect, composeStartAngle, span.sweepDeg, forceMoveTo = true)

        val innerAtEnd = polarOffset(band.innerPx, span.endDeg)
        path.lineTo(center.x + innerAtEnd.x, center.y + innerAtEnd.y)

        path.arcTo(innerRect, composeStartAngle + span.sweepDeg, -span.sweepDeg, forceMoveTo = false)
        path.close()

        return path
    }
}
