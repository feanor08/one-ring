package com.feanor08.onering.internal

import androidx.compose.runtime.Stable

/** Item-id-keyed registry of [ItemAnimation]s, owned by OneRingState. */
@Stable
internal class RingAnimations {
    private val byId = mutableMapOf<String, ItemAnimation>()

    fun of(id: String, initialToggleProgress: Float = 0f): ItemAnimation =
        byId.getOrPut(id) { ItemAnimation(initialToggleProgress) }

    /** Drops animation state for items no longer present, called when the item list structurally changes. */
    fun prune(liveIds: Set<String>) {
        byId.keys.retainAll(liveIds)
    }
}
