package com.feanor08.onering.notification

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.feanor08.onering.internal.rememberAnimationsEnabled
import kotlin.math.abs
import kotlin.math.roundToInt

/** Vertical offset per collapsed rear depth (back1 = 1x this, back2 = 2x). */
internal val StackBackLayerOffset: Dp = 8.dp

/** How much of a collapsed rear card's own bottom edge stays visible as a backing strip. */
internal val StackBackLayerVisibleHeight: Dp = 10.dp

/**
 * A group item's own current position/viewport info, read at DRAW time (see
 * [NotificationGroupSlot]'s per-child `placeRelativeWithLayer` block) rather
 * than during layout or composition -- the same deferred-snapshot-read
 * discipline [NotificationFeedList]'s existing `circleEdgeGraphicsLayer`
 * already uses, so scrolling stays smooth (no recomposition/relayout per
 * scrolled pixel). Null when there is no scrollable list to ask (the
 * non-interactive/hidden `Column` branch), in which case the group renders
 * with no circular-edge treatment, matching today's behavior for that
 * branch.
 */
internal fun interface GroupEdgeTransformLookup {
    fun current(): GroupEdgeTransformFrame?
}

internal data class GroupEdgeTransformFrame(val groupItemOffsetPx: Float, val itemSizePx: Float, val viewportCenterPx: Float)

/**
 * The one persistent multi-thread UI tree for a source with two or more
 * active threads -- serves collapsed, expanding, expanded, and collapsing
 * presentation alike (see docs/notifications-plan.md section 5). There is no
 * separate "expanded" composable and no decorative rear-card substitute:
 * every child here is a real [NotificationCard] for a real thread, keyed by
 * `threadKey` (via [key]) so its remembered swipe/card state survives every
 * transition this slot ever plays -- collapsed -> expanding -> expanded ->
 * collapsing -> collapsed, insertion, dismissal, and reordering -- without
 * ever reassigning one thread's remembered state to another (see that
 * section's "thread-card identity" doc).
 *
 * All real cards are always composed (see "four or more threads" below);
 * only their position, width scale, visible clip height, and content alpha
 * change with this slot's own internal 0 (collapsed) .. 1 (fully expanded)
 * progress, animated from the boolean [expanded] target -- owned by
 * [OneRingNotificationHost] via stored/effective expansion state, not by
 * this composable (docs/notifications-plan.md section 15).
 *
 * Measurement: every card is measured once, at the same fixed full width,
 * regardless of collapsed/expanded state or depth -- narrowing is a layer
 * `scaleX`, never a different measured width (docs/notifications-plan.md
 * section 10, "do not animate measurement constraints"). Natural,
 * content-driven heights are never assumed equal.
 *
 * Placement: rear cards are bottom-aligned (see [collapsedOffsetPx]) so an
 * older card's own content height never changes how much of it peeks out.
 * "Child 3+" for a group larger than [STACK_MAX_VISIBLE_REAR_DEPTH] + 1 is
 * composed and measured like every other card but placed with zero visible
 * clip height and no interaction -- so expanding a large group never has to
 * pop a not-yet-composed card into existence.
 */
@Composable
internal fun NotificationGroupSlot(
    group: OneRingNotificationGroup,
    style: OneRingNotificationStyle,
    maxCardWidthDp: Dp,
    minCardHeightDp: Dp,
    collapsedCardMaxHeightDp: Dp,
    interactive: Boolean,
    expanded: Boolean,
    onDismiss: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    edgeTransformLookup: GroupEdgeTransformLookup?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val threadCount = group.threads.size
    val visibleRear = visibleRearDepth(threadCount)
    val cardSpacingPx = with(density) { style.dimensions.cardSpacing.toPx() }
    val backLayerOffsetPx = with(density) { StackBackLayerOffset.toPx() }
    val backLayerVisibleHeightPx = with(density) { StackBackLayerVisibleHeight.toPx() }
    val cornerRadiusPx = with(density) { style.dimensions.cornerRadius.toPx() }
    val collapsedCardMaxHeightPx = with(density) { collapsedCardMaxHeightDp.toPx() }

    // One expansion progress, animated between the collapsed/expanded
    // endpoints -- see docs/notifications-plan.md section 15. Reduced motion
    // (animations disabled) snaps directly to the endpoint inside this same
    // persistent slot rather than skipping the transition mechanism itself,
    // per that section's "Reduced motion" note.
    val animationsEnabled = rememberAnimationsEnabled()
    val transition = updateTransition(targetState = expanded, label = "stackExpansion")
    val progressRaw by transition.animateFloat(
        label = "stackExpansionProgress",
        transitionSpec = { if (animationsEnabled) style.motion.stackExpand else snap() },
    ) { target -> if (target) 1f else 0f }
    val progress = coerceProgress(progressRaw)
    val fullyExpanded = progress >= 1f && !transition.isRunning

    Layout(
        modifier = modifier,
        content = {
            group.threads.forEachIndexed { depth, thread ->
                key(thread.threadKey) {
                    val isFront = depth == 0
                    val rearVisible = depth in 1..visibleRear
                    // Secondary cards are only genuinely interactive at the
                    // fully-expanded endpoint -- never merely because their
                    // alpha happens to be high mid-transition (see
                    // docs/notifications-plan.md section 11).
                    val cardInteractive = interactive && (isFront || (rearVisible && fullyExpanded))
                    // Rear semantics are binary on `fullyExpanded`, never on
                    // alpha/progress (docs/notifications-plan.md section 12,
                    // "Transition and host gates"): a rear card exposes no
                    // readable or actionable semantics at all until expansion
                    // fully completes, and loses them again the instant
                    // collapse begins -- `clearAndSetSemantics {}` here (an
                    // ancestor of the card's own semantics node) fully
                    // suppresses its subtree rather than merely gating its
                    // actions the way [cardInteractive] gates NotificationCard's
                    // own dismiss/tap actions for the *host*-level
                    // hidden/covered case.
                    val semanticsHidden = !isFront && !fullyExpanded
                    NotificationCard(
                        notification = thread,
                        style = style,
                        interactive = cardInteractive,
                        onDismiss = { onDismiss(thread.threadKey) },
                        sourceStackLabel = if (isFront) "${group.sourceLabel} · $threadCount" else null,
                        contentAlpha = if (isFront) 1f else contentRevealAlpha(progress),
                        onTap = if (isFront) onToggleExpanded else null,
                        tapAccessibilityLabel = if (isFront) (if (fullyExpanded) "Collapse" else "Expand") else null,
                        modifier = Modifier
                            .width(maxCardWidthDp)
                            .heightIn(min = minCardHeightDp)
                            .then(if (semanticsHidden) Modifier.clearAndSetSemantics {} else Modifier),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val childConstraints = Constraints.fixedWidth(constraints.maxWidth)
        val placeables = measurables.map { it.measure(childConstraints) }
        val naturalHeights = placeables.map { it.height.toFloat() }
        val frontNaturalHeightPx = naturalHeights.firstOrNull() ?: 0f

        // The front card's *visible* collapsed height is capped -- a long
        // message is clipped, not left to grow the collapsed stack without
        // bound (docs/notifications-plan.md section 15, styled via
        // OneRingNotificationDimensions.collapsedCardMaxHeightFraction).
        // Rear-card offsets and the slot's own collapsed total height are
        // derived from this capped value too, so the peeking backing strip
        // sits right below the front card's *visible* edge, not its full
        // (possibly much taller) natural height.
        val collapsedFrontVisibleHeightPx = minOf(frontNaturalHeightPx, collapsedCardMaxHeightPx)

        val collapsedOffsets = naturalHeights.indices.map { depth ->
            // The front card's own top is always pinned at 0 -- identical to
            // its expanded-endpoint offset (see [expandedOffsetsPx]) -- so it
            // never shifts position, only its bottom clip height changes.
            // [collapsedOffsetPx]'s bottom-alignment formula is only for rear
            // cards, which peek relative to the front's *visible* edge.
            if (depth == 0) {
                0f
            } else {
                collapsedOffsetPx(
                    depth = depth,
                    frontHeightPx = collapsedFrontVisibleHeightPx,
                    backLayerOffsetPx = backLayerOffsetPx,
                    childNaturalHeightPx = naturalHeights[depth],
                )
            }
        }
        val expandedOffsets = expandedOffsetsPx(naturalHeights, cardSpacingPx)

        val collapsedHeightPx = collapsedTotalHeightPx(collapsedFrontVisibleHeightPx, visibleRear, backLayerOffsetPx)
        val expandedHeightPx = expandedTotalHeightPx(naturalHeights, cardSpacingPx)
        val totalHeightPx = lerp(collapsedHeightPx, expandedHeightPx, progress)

        layout(constraints.maxWidth, totalHeightPx.roundToInt().coerceAtLeast(0)) {
            placeables.forEachIndexed { depth, placeable ->
                val childHeightPx = naturalHeights[depth]
                val yPx = lerp(collapsedOffsets[depth], expandedOffsets[depth], progress)
                val collapsedClipPx =
                    collapsedVisibleClipHeightPx(depth, visibleRear, collapsedFrontVisibleHeightPx, backLayerVisibleHeightPx)
                val clipHeightPx = lerp(collapsedClipPx, childHeightPx, progress)
                val stackWidthScale = lerp(collapsedWidthScale(depth), 1f, progress)

                // Deferred: reads current scroll/viewport state (if any) at
                // draw time only, inside the layer block -- see
                // GroupEdgeTransformLookup's doc.
                placeable.placeRelativeWithLayer(
                    x = 0,
                    y = yPx.roundToInt(),
                    zIndex = -depth.toFloat(),
                ) {
                    clip = true
                    // The front card exposes its *top* (title/subtitle/first
                    // lines) and clips overflow at the bottom -- a capped
                    // collapsed height, not a backing strip. Rear cards do
                    // the opposite: expose their *bottom* as a peeking strip
                    // behind the front card. See each shape's own doc.
                    shape = if (depth == 0) {
                        topClipShape(cornerRadiusPx, clipHeightPx)
                    } else {
                        bottomStripShape(cornerRadiusPx, clipHeightPx)
                    }

                    var effectiveScaleX = stackWidthScale
                    var effectiveAlpha = 1f
                    val frame = edgeTransformLookup?.current()
                    if (frame != null && frame.viewportCenterPx > 0f) {
                        val groupCenterPx = frame.groupItemOffsetPx + totalHeightPx / 2f
                        val childOwnCenterPx = frame.groupItemOffsetPx + yPx + childHeightPx / 2f
                        val effectiveCenterPx = edgeTransformCenterPx(groupCenterPx, childOwnCenterPx, progress)
                        val distanceFraction = abs(effectiveCenterPx - frame.viewportCenterPx) / frame.viewportCenterPx
                        val edgeTransform = circleEdgeTransform(distanceFraction, style.dimensions)
                        effectiveScaleX *= edgeTransform.scaleX
                        effectiveAlpha = edgeTransform.alpha
                    }
                    scaleX = effectiveScaleX
                    alpha = effectiveAlpha
                }
            }
        }
    }
}

/**
 * A rounded rectangle exposing only the top [clipHeightPx] of a shape whose
 * full size is supplied by the layer at draw time -- the front card's own
 * collapsed height cap (see [NotificationGroupSlot]'s collapsed-height-cap
 * doc). Rounds every corner normally: unlike [bottomStripShape]'s backing
 * strip, this clip's bottom edge *is* the card's real, fully visible bottom
 * edge whenever the cap is active, so it must look like an ordinary rounded
 * card, not a strip with a hard cut.
 */
private fun topClipShape(cornerRadiusPx: Float, clipHeightPx: Float): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val visibleHeight = clipHeightPx.coerceIn(0f, size.height)
        val radius = CornerRadius(cornerRadiusPx.coerceAtMost(visibleHeight / 2f).coerceAtLeast(0f))
        val roundRect = RoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = visibleHeight,
            cornerRadius = radius,
        )
        val path = Path().apply { addRoundRect(roundRect) }
        return Outline.Generic(path)
    }
}

/**
 * A rounded rectangle exposing only the bottom [clipHeightPx] of a shape
 * whose full size is supplied by the layer at draw time -- i.e. a card's
 * visible backing strip while collapsed. Rounds only the bottom corners
 * (deliberately -- the top of a clipped rear card is never visible, so
 * rounding it would be wasted and, worse, would round a corner that's
 * supposed to be a hard clip edge against the card above it).
 */
private fun bottomStripShape(cornerRadiusPx: Float, clipHeightPx: Float): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val visibleHeight = clipHeightPx.coerceIn(0f, size.height)
        val top = size.height - visibleHeight
        val radius = CornerRadius(cornerRadiusPx.coerceAtMost(visibleHeight / 2f).coerceAtLeast(0f))
        val roundRect = RoundRect(
            left = 0f,
            top = top,
            right = size.width,
            bottom = size.height,
            bottomLeftCornerRadius = radius,
            bottomRightCornerRadius = radius,
        )
        val path = Path().apply { addRoundRect(roundRect) }
        return Outline.Generic(path)
    }
}
