package com.feanor08.onering.geometry

/**
 * Splits [availableSweepDeg] among [weights] in proportion to each weight's
 * share of their sum. Weights are *relative*: `[2, 1, 1]` and `[50, 25, 25]`
 * produce identical results, and an all-equal list reproduces the plain
 * `sweep / count` division exactly.
 *
 * The returned sweeps sum to [availableSweepDeg] (within float tolerance) by
 * construction: each sweep is derived from the difference between successive
 * *cumulative* fractions rather than computed independently and summed. That
 * makes the final boundary land exactly on the end angle instead of drifting
 * by an accumulated rounding error, which at 12 items is visible as a hairline
 * seam.
 *
 * Invalid weights are rejected at [com.feanor08.onering.OneRingItem]
 * construction, so by here they're already finite and positive; a defensive
 * total <= 0 still degrades to equal division rather than emitting NaN spans.
 */
internal fun proportionalSweeps(weights: List<Float>, availableSweepDeg: Float): List<Float> {
    if (weights.isEmpty()) return emptyList()
    val available = availableSweepDeg.coerceAtLeast(0f)

    var total = 0f
    for (w in weights) total += w
    if (!total.isFinite() || total <= 0f) {
        val equal = available / weights.size
        return List(weights.size) { equal }
    }

    var cumulativeWeight = 0f
    var previousDeg = 0f
    return List(weights.size) { i ->
        cumulativeWeight += weights[i]
        val boundaryDeg = available * (cumulativeWeight / total)
        (boundaryDeg - previousDeg).also { previousDeg = boundaryDeg }
    }
}

/**
 * Lays [weights] out as consecutive spans starting at [startDeg], separated by
 * [gapDeg]. Gaps are removed from the available sweep *before* proportioning,
 * so they never distort the ratio between items -- a weight-2 item is exactly
 * twice a weight-1 item's sweep whatever the gap is.
 */
internal fun weightedSpans(
    weights: List<Float>,
    startDeg: Float,
    totalSweepDeg: Float,
    gapDeg: Float,
): List<AngularSpan> {
    if (weights.isEmpty()) return emptyList()
    val totalGap = if (weights.size > 1) gapDeg * (weights.size - 1) else 0f
    val sweeps = proportionalSweeps(weights, (totalSweepDeg - totalGap).coerceAtLeast(0f))

    var cursor = startDeg
    return List(weights.size) { i ->
        AngularSpan(cursor, sweeps[i]).also { cursor += sweeps[i] + gapDeg }
    }
}
