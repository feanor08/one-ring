package com.feanor08.onering.notification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The entire notification feed as ONE scrollable [LazyColumn] of cards --
 * scrolling continues naturally from one source group's cards into the next
 * rather than stopping at per-group boundaries (see docs/notifications.md;
 * this is why no card gets its own internal scroll container). Groups still
 * drive ordering ([OneRingNotificationGroup]'s most-recently-updated-first
 * convention) and spacing (extra gap before the first card of a new group),
 * they just aren't rendered with their own header row.
 *
 * The circular-edge narrowing effect ([circleEdgeTransform]) is applied via a
 * *deferred-read* `graphicsLayer { }` block on each item, reading
 * [LazyListState.layoutInfo] directly inside the layer block rather than in a
 * `@Composable` scope. That's what keeps scrolling smooth: a deferred
 * `graphicsLayer` block re-evaluates on every frame the list state changes
 * without triggering recomposition or another layout pass -- unlike, say,
 * `onGloballyPositioned`-driven state mutation, which would cause exactly the
 * kind of layout feedback loop this design deliberately avoids.
 *
 * [interactive] gates every pointer-input surface here. Each card's swipe
 * and dismiss-button handlers are gated by simply not installing their
 * `pointerInput` modifiers (see [NotificationCard]) -- but the list's own
 * scroll needed a stronger gate than `LazyColumn`'s `userScrollEnabled`
 * parameter: an instrumented experiment proved that a `LazyColumn` still
 * wins hit-testing over the ring `Canvas` beneath it even with
 * `userScrollEnabled = false` (its `scrollable` node still claims the
 * gesture; the flag only stops it from *acting* on the drag once claimed).
 * That's a real, previously-unproven gap between "won't scroll" and "won't
 * intercept," and it silently broke every submenu child / seek drag whose
 * hit region happened to fall inside the (hidden, non-interactive)
 * `coreOverlay` circle. So when [interactive] is false this renders a plain,
 * non-scrolling [Column] instead of a [LazyColumn] at all -- with no
 * `pointerInput`/`scrollable` node of any kind in the subtree, there is
 * nothing left that *could* claim the gesture, which is what composition-level
 * gating was supposed to guarantee in the first place. See
 * [OneRingNotificationHost]'s "coreOverlay interactivity" doc. The visual
 * tree itself (icons, text, colors) is unaffected by [interactive] -- only
 * whether it can be touched.
 */
@Composable
internal fun NotificationFeedList(
    groups: List<OneRingNotificationGroup>,
    style: OneRingNotificationStyle,
    coreOverlayDiameterPx: Float,
    interactive: Boolean,
    expandedSourceKey: String?,
    onDismiss: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val maxCardWidthDp = with(density) { (coreOverlayDiameterPx * style.dimensions.maxCardWidthFraction).toDp() }
    val minCardHeightDp = with(density) { (coreOverlayDiameterPx * style.dimensions.minCardHeightFraction).toDp() }
    val collapsedCardMaxHeightDp = with(density) { (coreOverlayDiameterPx * style.dimensions.collapsedCardMaxHeightFraction).toDp() }
    val topPaddingDp = with(density) { (coreOverlayDiameterPx * style.dimensions.topContentPaddingFraction).toDp() }
    val bottomPaddingDp = with(density) { (coreOverlayDiameterPx * style.dimensions.bottomContentPaddingFraction).toDp() }

    if (interactive) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().clip(CircleShape),
            contentPadding = PaddingValues(top = topPaddingDp, bottom = bottomPaddingDp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            groups.forEachIndexed { groupIndex, group ->
                val itemKey = "group:${group.sourceKey}"
                item(key = itemKey) {
                    val topSpacing = if (groupIndex == 0) 0.dp else style.dimensions.groupSpacing
                    val edgeTransformLookup = GroupEdgeTransformLookup {
                        val layoutInfo = listState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                        val viewportCenterPx = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        itemInfo?.let { GroupEdgeTransformFrame(it.offset.toFloat(), it.size.toFloat(), viewportCenterPx) }
                    }
                    SourceGroupContent(
                        group = group,
                        style = style,
                        maxCardWidthDp = maxCardWidthDp,
                        minCardHeightDp = minCardHeightDp,
                        collapsedCardMaxHeightDp = collapsedCardMaxHeightDp,
                        interactive = true,
                        expandedSourceKey = expandedSourceKey,
                        onDismiss = onDismiss,
                        onToggleExpanded = onToggleExpanded,
                        edgeTransformLookup = edgeTransformLookup,
                        modifier = Modifier.padding(top = topSpacing),
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().clip(CircleShape).padding(top = topPaddingDp, bottom = bottomPaddingDp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            groups.forEachIndexed { groupIndex, group ->
                val topSpacing = if (groupIndex == 0) 0.dp else style.dimensions.groupSpacing
                SourceGroupContent(
                    group = group,
                    style = style,
                    maxCardWidthDp = maxCardWidthDp,
                    minCardHeightDp = minCardHeightDp,
                    collapsedCardMaxHeightDp = collapsedCardMaxHeightDp,
                    interactive = false,
                    expandedSourceKey = expandedSourceKey,
                    onDismiss = onDismiss,
                    onToggleExpanded = onToggleExpanded,
                    edgeTransformLookup = null,
                    modifier = Modifier.padding(top = topSpacing),
                )
            }
        }
    }
}

/**
 * One source group, rendered by the exact same rule regardless of which
 * branch above is active: a single active thread is a plain [NotificationCard];
 * two or more is the persistent [NotificationGroupSlot]. Both branches must
 * agree on this so a source's presentation doesn't change shape merely
 * because the feed became hidden/covered (see docs/notifications-plan.md
 * section 2, "existing presentation and integration").
 */
@Composable
private fun SourceGroupContent(
    group: OneRingNotificationGroup,
    style: OneRingNotificationStyle,
    maxCardWidthDp: Dp,
    minCardHeightDp: Dp,
    collapsedCardMaxHeightDp: Dp,
    interactive: Boolean,
    expandedSourceKey: String?,
    onDismiss: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    edgeTransformLookup: GroupEdgeTransformLookup?,
    modifier: Modifier = Modifier,
) {
    when (stackDecision(group.threads.size)) {
        StackDecision.Single -> {
            val thread = group.threads.first()
            NotificationCard(
                notification = thread,
                style = style,
                interactive = interactive,
                onDismiss = { onDismiss(thread.threadKey) },
                modifier = modifier
                    .width(maxCardWidthDp)
                    .heightIn(min = minCardHeightDp)
                    .circleEdgeGraphicsLayer(edgeTransformLookup, style.dimensions),
            )
        }
        StackDecision.Group -> {
            NotificationGroupSlot(
                group = group,
                style = style,
                maxCardWidthDp = maxCardWidthDp,
                minCardHeightDp = minCardHeightDp,
                collapsedCardMaxHeightDp = collapsedCardMaxHeightDp,
                interactive = interactive,
                expanded = expandedSourceKey == group.sourceKey,
                onDismiss = onDismiss,
                onToggleExpanded = { onToggleExpanded(group.sourceKey) },
                edgeTransformLookup = edgeTransformLookup,
                modifier = modifier,
            )
        }
    }
}

/**
 * Applies [circleEdgeTransform] as a deferred `graphicsLayer` read of
 * [lookup]'s current frame -- see this file's class doc for why this
 * specific mechanism (rather than a recomposition-driven one) is what keeps
 * scrolling smooth. Null (the non-interactive branch, or before this item's
 * first layout pass) renders with no edge treatment.
 */
private fun Modifier.circleEdgeGraphicsLayer(
    lookup: GroupEdgeTransformLookup?,
    dimensions: OneRingNotificationDimensions,
): Modifier = this.graphicsLayer {
    val frame = lookup?.current() ?: return@graphicsLayer
    if (frame.viewportCenterPx <= 0f) return@graphicsLayer
    val itemCenterPx = frame.groupItemOffsetPx + frame.itemSizePx / 2f
    val distanceFraction = abs(itemCenterPx - frame.viewportCenterPx) / frame.viewportCenterPx
    val transform = circleEdgeTransform(distanceFraction, dimensions)
    scaleX = transform.scaleX
    alpha = transform.alpha
}
