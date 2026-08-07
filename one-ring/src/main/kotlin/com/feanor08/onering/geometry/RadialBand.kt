package com.feanor08.onering.geometry

/** An annular band: [innerPx, outerPx], in resolved pixels. */
public data class RadialBand(val innerPx: Float, val outerPx: Float) {
    init {
        require(outerPx >= innerPx) { "outerPx ($outerPx) must be >= innerPx ($innerPx)" }
    }

    public val thicknessPx: Float get() = outerPx - innerPx
    public val midPx: Float get() = (innerPx + outerPx) / 2f

    public fun contains(radiusPx: Float, slopPx: Float = 0f): Boolean =
        radiusPx >= innerPx - slopPx && radiusPx <= outerPx + slopPx
}
