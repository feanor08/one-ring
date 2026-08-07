package com.feanor08.onering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.feanor08.onering.internal.RingFrameBuilder
import com.feanor08.onering.internal.RingInteractionController
import com.feanor08.onering.internal.RingPathPool
import com.feanor08.onering.internal.RingSemanticsLayer
import com.feanor08.onering.internal.currentSubmenu
import com.feanor08.onering.internal.drawRing
import com.feanor08.onering.internal.effectiveMotion
import com.feanor08.onering.internal.rememberAnimationsEnabled
import com.feanor08.onering.internal.resolveRing
import com.feanor08.onering.internal.submenuChildSpans
import com.feanor08.onering.internal.warnIfTargetsTooSmall
import com.feanor08.onering.style.OneRingStyle

/**
 * A radial interaction wheel: press, scrub, release-to-commit -- not a set of
 * ordinary buttons layered on a canvas. Committing a leaf [OneRingItem.Action]
 * fires [onAction]; committing a [OneRingItem.Menu] grows it into the close
 * control, fades its siblings out, and reveals its children as a submenu band
 * (expanding inward from a thin strip at the submenu's outer radius -- see
 * docs/interaction-spec.md), all continuing smoothly from wherever the hover
 * animation currently was (no "selection stutter" -- see docs/PLAN.md section
 * 10). Only one level of nesting renders/responds to input so far; seek
 * controls land in a later phase.
 *
 * Every wedge also carries a real (invisible) semantics node, so the whole
 * flow is operable with TalkBack without a single scrub gesture -- explore-
 * by-touch consumes pointer events, so this is a second, complete input path,
 * not decoration. System-level "remove animations" is honored automatically.
 *
 * @param items the top-level wedges, in clockwise order starting at
 *   [com.feanor08.onering.style.OneRingDimensions.startAngleDeg].
 * @param onAction called when a leaf [OneRingItem.Action] is committed (released
 *   while highlighted), at any nesting depth currently supported.
 * @param state navigation/highlight state; hoist with [rememberOneRingState] to
 *   observe or drive it programmatically.
 * @param style colors, sizes, and motion; see [OneRingDefaults.style].
 * @param haptics performs feedback for interaction moments; defaults to the
 *   platform view-based implementation, gated by
 *   [com.feanor08.onering.style.OneRingBehavior.hapticsEnabled]. Pass
 *   [OneRingHaptics.None] to disable regardless of that flag.
 * @param onEvent an optional firehose for navigation events (highlight change,
 *   commit, open, close) beyond what [onAction] alone covers -- most
 *   consumers don't need this.
 * @param center optional content rendered in the ring's empty core.
 */
@Composable
public fun OneRing(
    items: List<OneRingItem>,
    onAction: (OneRingItem.Action) -> Unit,
    modifier: Modifier = Modifier,
    state: OneRingState = rememberOneRingState(),
    style: OneRingStyle = OneRingDefaults.style(),
    haptics: OneRingHaptics = rememberDefaultOneRingHaptics(),
    onEvent: ((OneRingEvent) -> Unit)? = null,
    center: (@Composable OneRingCenterScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val pathPool = remember { RingPathPool() }
    val controller = remember(state) { RingInteractionController(state) }
    val animationsEnabled = rememberAnimationsEnabled()

    // pointerInput is keyed on Unit, never on items/state/style, so an
    // in-flight gesture is never restarted by an unrelated recomposition --
    // rememberUpdatedState lets the (stable, never-recreated) suspend block
    // always see the current controller instance. See docs/PLAN.md section 9.1.
    val currentController = rememberUpdatedState(controller)

    BoxWithConstraints(modifier = modifier) {
        val sizePx = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        if (sizePx.width <= 0f || sizePx.height <= 0f) return@BoxWithConstraints

        val resolved = resolveRing(sizePx, density, style.dimensions, items.size)
        val motion = effectiveMotion(style.motion, animationsEnabled)
        controller.update(items, resolved, style.dimensions, motion, style.behavior, haptics, onAction, onEvent)

        LaunchedEffect(items.size, resolved) {
            warnIfTargetsTooSmall(resolved, items.size, minTouchPx = with(density) { 48.dp.toPx() })
        }

        val expansionOf = { itemId: String -> state.animations.of(itemId).expansion.value }
        val submenu = currentSubmenu(items, state.path)
        val parentSpan = submenu?.let { (parent, _) ->
            resolved.mainSpans.getOrNull(items.indexOfFirst { it.id == parent.id })
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { currentController.value.handleGestures(this) },
        ) {
            var frame = RingFrameBuilder.build(
                items = items,
                resolved = resolved,
                colors = style.colors,
                expansionOf = expansionOf,
                alphaOf = { itemId -> state.animations.of(itemId).contentAlpha.value },
            )

            if (submenu != null && parentSpan != null) {
                val submenuArcs = RingFrameBuilder.buildSubmenuArcs(
                    children = submenu.second,
                    parentSpan = parentSpan,
                    resolved = resolved,
                    dimensions = style.dimensions,
                    colors = style.colors,
                    reveal = state.submenuReveal.value,
                    expansionOf = expansionOf,
                )
                frame = frame.copy(arcs = frame.arcs + submenuArcs)
            }

            drawRing(frame, pathPool)
        }

        val settled = state.phase == OneRingState.Phase.Settled
        if (settled && submenu == null) {
            RingSemanticsLayer(
                items = items,
                spans = resolved.mainSpans,
                innerPx = resolved.mainInnerPx,
                outerPx = resolved.mainOuterPx,
                centerPx = resolved.centerPx,
                highlightedId = state.highlightedId,
                onActivate = { id -> controller.commitDirectly(id) },
            )
        } else if (settled && submenu != null && parentSpan != null) {
            val (parent, children) = submenu
            RingSemanticsLayer(
                items = listOf(parent.copy(contentDescription = "Close ${parent.label}")),
                spans = listOf(parentSpan),
                innerPx = resolved.mainInnerPx,
                outerPx = resolved.mainSelectedOuterPx,
                centerPx = resolved.centerPx,
                highlightedId = null,
                onActivate = { controller.closeDirectly() },
            )
            val childSpans = submenuChildSpans(resolved, style.dimensions, parentSpan, children.size)
            RingSemanticsLayer(
                items = children,
                spans = childSpans,
                innerPx = resolved.submenuInnerPx,
                outerPx = resolved.submenuOuterPx,
                centerPx = resolved.centerPx,
                highlightedId = state.highlightedId,
                onActivate = { id -> controller.commitDirectly(id) },
            )
        }

        if (center != null) {
            val centerDiameterDp = with(density) { (resolved.centerRadiusPx * 2).toDp() }
            val scope = remember(state.path, state.highlightedId) {
                OneRingCenterScope(currentMenuId = state.path.lastOrNull(), highlightedId = state.highlightedId)
            }
            Box(
                modifier = Modifier.align(Alignment.Center).size(centerDiameterDp),
            ) {
                scope.center()
            }
        }
    }
}
