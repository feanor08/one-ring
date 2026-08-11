package com.feanor08.onering.notification

/**
 * Compose-free decisions and geometry math behind the Apple-style
 * notification stack (docs/notifications-plan.md). Nothing here imports
 * Compose UI beyond plain value types -- everything is unit-testable on a
 * plain JVM, the same way [NotificationFeed] and [circleEdgeTransform] are.
 *
 * [NotificationGroupSlot] (the Compose UI layer) is the only consumer of
 * this file; it never duplicates this math.
 */

/** How many *real* older thread cards are given a visible backing position while collapsed. Capped, not scaled to thread count. */
internal const val STACK_MAX_VISIBLE_REAR_DEPTH = 2

/** Fractional width shrink applied per rear depth while collapsed (depth 1 -> 1x, depth 2 -> 2x this). */
internal const val STACK_BACK_LAYER_WIDTH_REDUCTION = 0.06f

/**
 * Expansion progress below which a rear card's *content* (icon/title/
 * messages/timestamp) stays fully transparent -- only the card surface
 * itself is visible before this point. Because the same formula runs both
 * directions, expanding content only reveals once past this threshold, while
 * collapsing content starts fading the instant progress drops below 1 --
 * see [contentRevealAlpha]'s doc.
 */
internal const val STACK_CONTENT_REVEAL_THRESHOLD = 0.4f

/** Whether a source group renders as a plain [NotificationCard] or a multi-thread [NotificationGroupSlot]. */
internal enum class StackDecision { Single, Group }

/** [Single] for zero or one thread, [Group] for two or more -- stack depth represents threads, never messages. */
internal fun stackDecision(threadCount: Int): StackDecision =
    if (threadCount <= 1) StackDecision.Single else StackDecision.Group

/**
 * How many of a group's older threads (beyond the front) get a visible
 * backing position while collapsed -- `min(threadCount - 1, maxVisibleRearDepth)`.
 * A group of 8 threads still reports at most [maxVisibleRearDepth]: the
 * physical stack only needs to communicate "there is more than one," and the
 * source-count text (see [displaySubtitle] vs. this group's own thread
 * count) already carries the exact number.
 */
internal fun visibleRearDepth(threadCount: Int, maxVisibleRearDepth: Int = STACK_MAX_VISIBLE_REAR_DEPTH): Int =
    (threadCount - 1).coerceIn(0, maxVisibleRearDepth)

/**
 * `current == tapped` collapses (returns null); anything else -- including a
 * different source already expanded -- expands [tapped] and implicitly
 * collapses whatever was open, since only one stored key ever exists.
 */
internal fun toggleExpanded(current: String?, tapped: String): String? = if (current == tapped) null else tapped

/**
 * The *effective* expanded source for this frame: [stored] only counts when
 * it still names a group in [groups] that currently has two or more threads.
 * Rendering must read only this value, never [stored] directly -- see
 * [OneRingNotificationHost]'s stored-vs-effective state doc. [groups] should
 * be whatever's actually on screen (the arrival-safe snapshot), not raw live
 * data, so expansion never describes something the user isn't being shown.
 */
internal fun effectiveExpandedSourceKey(
    stored: String?,
    groups: List<OneRingNotificationGroup>,
): String? = stored?.takeIf { key -> groups.any { it.sourceKey == key && it.threads.size >= 2 } }

internal fun coerceProgress(value: Float): Float = value.coerceIn(0f, 1f)

internal fun lerp(start: Float, stop: Float, progress: Float): Float = start + (stop - start) * coerceProgress(progress)

/**
 * Each child's Y offset when fully expanded: cumulative natural heights plus
 * inter-card spacing, child 0 pinned at 0 so the front card's top never
 * moves between collapsed and expanded (see docs/notifications-plan.md
 * section 9, "the slot top remains fixed").
 */
internal fun expandedOffsetsPx(naturalHeightsPx: List<Float>, cardSpacingPx: Float): List<Float> {
    val offsets = ArrayList<Float>(naturalHeightsPx.size)
    var cursor = 0f
    for (height in naturalHeightsPx) {
        offsets += cursor
        cursor += height + cardSpacingPx
    }
    return offsets
}

/** Total slot height when fully expanded: every natural height plus a spacing gap between each pair. */
internal fun expandedTotalHeightPx(naturalHeightsPx: List<Float>, cardSpacingPx: Float): Float =
    if (naturalHeightsPx.isEmpty()) 0f else naturalHeightsPx.sum() + cardSpacingPx * (naturalHeightsPx.size - 1)

/**
 * A collapsed child's Y offset, bottom-aligned rather than top-aligned:
 * `(frontHeight + depth * backLayerOffset) - thisChild'sOwnNaturalHeight`.
 * Bottom-alignment is what keeps the visible backing strip a consistent
 * height regardless of whether an older card's own content happens to be
 * taller or shorter than the front card's -- see docs/notifications-plan.md
 * section 9, "unequal-height collapsed geometry."
 */
internal fun collapsedOffsetPx(
    depth: Int,
    frontHeightPx: Float,
    backLayerOffsetPx: Float,
    childNaturalHeightPx: Float,
): Float = (frontHeightPx + depth * backLayerOffsetPx) - childNaturalHeightPx

/** Total slot height while collapsed: the front card's own height plus one offset per visible rear depth. */
internal fun collapsedTotalHeightPx(
    frontHeightPx: Float,
    visibleRearDepth: Int,
    backLayerOffsetPx: Float,
): Float = frontHeightPx + visibleRearDepth * backLayerOffsetPx

/**
 * How much of a collapsed child is actually visible (clip height), in px.
 * Depth 0 (the front card) is always fully visible; depths within
 * [visibleRearDepth] show [backLayerVisibleHeightPx]; anything deeper --
 * "child 3+" for a large group -- is fully clipped (zero), composed but
 * showing nothing, so expansion never has to pop a not-yet-composed card
 * into existence (see docs/notifications-plan.md section 6).
 */
internal fun collapsedVisibleClipHeightPx(
    depth: Int,
    visibleRearDepth: Int,
    frontHeightPx: Float,
    backLayerVisibleHeightPx: Float,
): Float = when {
    depth == 0 -> frontHeightPx
    depth <= visibleRearDepth -> backLayerVisibleHeightPx
    else -> 0f
}

/**
 * A collapsed child's width scale (independent of, and later combined with,
 * [circleEdgeTransform]'s own `scaleX`): the front card is always 1f, each
 * rear depth shrinks by one more [STACK_BACK_LAYER_WIDTH_REDUCTION].
 */
internal fun collapsedWidthScale(depth: Int, widthReductionFraction: Float = STACK_BACK_LAYER_WIDTH_REDUCTION): Float =
    (1f - depth * widthReductionFraction).coerceAtLeast(0f)

/**
 * A child's *content* alpha (icon/title/messages/timestamp -- never the card
 * surface itself, which stays visible throughout) as a function of this
 * child's own expansion progress. One formula runs both directions:
 * expanding, content stays at 0 until [STACK_CONTENT_REVEAL_THRESHOLD] is
 * passed, then ramps to 1 by progress 1; collapsing is the same curve read
 * backwards, so content starts fading the instant progress drops below 1 --
 * satisfying both "reveals after enough card surface is visible" and
 * "collapsing content begins fading immediately" with the same math.
 */
internal fun contentRevealAlpha(progress: Float, threshold: Float = STACK_CONTENT_REVEAL_THRESHOLD): Float {
    val clamped = coerceProgress(progress)
    if (threshold >= 1f) return if (clamped >= 1f) 1f else 0f
    return coerceProgress((clamped - threshold) / (1f - threshold))
}

/**
 * A child's own vertical center within the viewport, for feeding into
 * [circleEdgeTransform]. Collapsed and expanded modes read different things
 * as "this child's center" -- see docs/notifications-plan.md section 13 --
 * so the group behaves as one circular object while collapsed (every child
 * shares the *group's* center) and each card gets its own per-card treatment
 * once expanded (each child uses its own placed center), interpolated by the
 * same [progress] driving the rest of the transition.
 */
internal fun edgeTransformCenterPx(
    groupCenterInViewportPx: Float,
    childOwnCenterInViewportPx: Float,
    progress: Float,
): Float = lerp(groupCenterInViewportPx, childOwnCenterInViewportPx, progress)
