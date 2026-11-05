package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem

/**
 * A structural key for a list of [OneRingItem]s -- ids, types, child counts,
 * enabled flags, and weights only. Lambda fields (`onValueChange`, etc.) make
 * item equality unstable across recompositions when written inline, so
 * expensive `remember`s (resolved geometry) must key on this instead of on
 * `items` itself. Weight is part of the key because it changes the computed
 * spans: a live weight edit has to invalidate the resolved geometry.
 */
internal fun List<OneRingItem>.layoutKey(): Int {
    var hash = size
    for (item in this) {
        hash = hash * 31 + item.id.hashCode()
        hash = hash * 31 + item.enabled.hashCode()
        hash = hash * 31 + item.weight.hashCode()
        hash = hash * 31 + when (item) {
            is OneRingItem.Action -> 1
            is OneRingItem.Menu -> 31 * 2 + item.children.layoutKey()
            is OneRingItem.Seek -> 3
            is OneRingItem.Toggle -> 4
        }
    }
    return hash
}
