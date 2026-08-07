package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem

/**
 * Resolves the currently-open submenu (if any) from the top-level [items] and
 * [path]. Only one level deep is supported so far -- deeper nesting is a later
 * phase (see docs/PLAN.md's "nesting depth" open question).
 */
internal fun currentSubmenu(items: List<OneRingItem>, path: List<String>): Pair<OneRingItem.Menu, List<OneRingItem>>? {
    if (path.size != 1) return null
    val parent = items.firstOrNull { it.id == path[0] } as? OneRingItem.Menu ?: return null
    return parent to parent.children
}
