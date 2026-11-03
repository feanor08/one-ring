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
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.internal.RingContentLayer
import com.feanor08.onering.internal.RingFrameBuilder
import com.feanor08.onering.internal.RingInteractionController
import com.feanor08.onering.internal.RingPathPool
import com.feanor08.onering.internal.RingSemanticsLayer
import com.feanor08.onering.internal.currentSubmenu
import com.feanor08.onering.internal.drawRing
import com.feanor08.onering.internal.effectiveMotion
import com.feanor08.onering.internal.lerp
import com.feanor08.onering.internal.rememberAnimationsEnabled
import com.feanor08.onering.internal.resolveRing
import com.feanor08.onering.internal.submenuChildSpans
import com.feanor08.onering.internal.warnIfCoreOverlayOverlapsMainRing
import com.feanor08.onering.internal.warnIfTargetsTooSmall
import com.feanor08.onering.style.OneRingStyle

/**
 * A radial interaction wheel: press, scrub, release-to-commit -- not a set of
 * ordinary buttons layered on a canvas. Committing a leaf [OneRingItem.Action]
 * fires [onAction]; committing a [OneRingItem.Menu] grows it into the close
 * control, fades its siblings out, and reveals its children as a submenu band
 * (expanding inward from a thin strip at the submenu's outer radius); committing
 * a [OneRingItem.Seek] grows it (fully selected on the main ring, or just to
 * hover inside a submenu, where the parent stays the close control) and turns
 * the rest of the ring into a radial drag track -- see docs/interaction-spec.md
 * for both. Every transition continues smoothly from wherever the hover
 * animation currently was (no "selection stutter" -- see docs/PLAN.md section
 * 10). Only one level of nesting renders/responds to input so far.
 *
 * Every wedge also carries a real (invisible) semantics node, so the whole
 * flow is operable with TalkBack without a single scrub gesture -- explore-
 * by-touch consumes pointer events, so this is a second, complete input path,
 * not decoration. System-level "remove animations" is honored automatically.
 * (Semantic drag-to-adjust for an active seek is not yet implemented --
 * TalkBack can enter and exit a seek control, but adjusting its value still
 * needs a real drag gesture.)
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
 *   commit, open, close, seek enter/exit) beyond what [onAction] alone covers
 *   -- most consumers don't need this.
 * @param center optional content rendered in the ring's empty core, sized by
 *   [com.feanor08.onering.style.OneRingDimensions.centerRadius]. Unchanged by
 *   [coreOverlay]'s existence -- the two slots are independent and both
 *   optional.
 * @param coreOverlay optional, substantially larger content rendered in the
 *   ring's core, sized by
 *   [com.feanor08.onering.style.OneRingDimensions.coreOverlayRadius] --
 *   intended for content like [com.feanor08.onering.notification.OneRingNotificationHost]
 *   that needs real space rather than [center]'s small footprint. Drawn above
 *   [center] when both are supplied. The ring itself never claims pointer
 *   input starting inside this slot's circular footprint while it's at its
 *   own top level and settled (see [com.feanor08.onering.internal.RingInteractionController]'s
 *   `isInsideActiveCoreOverlay`) -- that half keeps the ring out of the
 *   overlay's way. The other half is the overlay's own responsibility: this
 *   slot has no built-in mechanism that stops its content from intercepting a
 *   submenu/seek gesture meant for the ring underneath it, so any interactive
 *   `coreOverlay` content must gate its own `pointerInput`/scroll/drag
 *   handlers off while [OneRingCenterScope.mode] isn't
 *   [OneRingMode.Idle]/[OneRingMode.Tracking] -- ideally by not composing
 *   those handlers at all (rather than consuming events), so there's nothing
 *   left for hit-testing to find. [com.feanor08.onering.notification.OneRingNotificationHost]
 *   demonstrates this pattern; see its "coreOverlay interactivity" doc for
 *   why composition-level gating, not event consumption, is the right tool
 *   here.
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
    coreOverlay: (@Composable OneRingCenterScope.() -> Unit)? = null,
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

        val resolved = resolveRing(sizePx, density, style.dimensions, items.map { it.weight })
        val motion = effectiveMotion(style.motion, animationsEnabled)
        controller.update(
            items, resolved, style.dimensions, motion, style.behavior, haptics, onAction, onEvent,
            hasCoreOverlay = coreOverlay != null,
        )

        // Programmatic commit / close / highlight (OneRingState.commit etc.), run by the same controller the pointer uses.
        LaunchedEffect(state, controller) {
            state.requests.collect { request ->
                if (state.phase != OneRingState.Phase.Settled || state.touching) return@collect
                when (request) {
                    is OneRingState.Request.Commit -> controller.commitDirectly(request.itemId)
                    OneRingState.Request.Close -> controller.closeDirectly()
                    is OneRingState.Request.Highlight -> controller.highlightDirectly(request.itemId)
                }
            }
        }

        LaunchedEffect(items.size, resolved) {
            warnIfTargetsTooSmall(resolved, items.size, minTouchPx = with(density) { 48.dp.toPx() })
        }
        if (coreOverlay != null) {
            LaunchedEffect(resolved) {
                warnIfCoreOverlayOverlapsMainRing(resolved)
            }
        }

        val expansionOf = { itemId: String -> state.animations.of(itemId).expansion.value }
        val alphaOf = { itemId: String -> state.animations.of(itemId).contentAlpha.value }
        val iconAlphaOf = { itemId: String -> state.animations.of(itemId).iconAlpha.value }
        val backIconAlphaOf = { itemId: String -> state.animations.of(itemId).backIconAlpha.value }
        val submenu = currentSubmenu(items, state.path)
        val parentSpan = submenu?.let { (parent, _) ->
            resolved.mainSpans.getOrNull(items.indexOfFirst { it.id == parent.id })
        }
        val seekSession = state.seekSession

        // The one main-ring item currently exempt from mainSiblingReveal --
        // whichever wedge owns the open submenu or active seek. Phase.Opening/
        // Closing carry this id synchronously from the very first frame of a
        // transition (before path/seekSession are published), so there's no
        // gap where a transitioning item is briefly treated as a sibling.
        val activeMainItemId: String? = when (val phase = state.phase) {
            is OneRingState.Phase.Opening -> phase.parentId
            is OneRingState.Phase.Closing -> phase.parentId
            else -> state.path.firstOrNull() ?: seekSession?.itemId
        }
        val mainSiblingReveal = state.mainSiblingReveal.value.coerceIn(0f, 1f)

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
                alphaOf = alphaOf,
                highlightedId = state.highlightedId,
                mainSiblingRevealProgress = mainSiblingReveal,
                activeItemId = activeMainItemId,
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
                    alphaOf = alphaOf,
                    highlightedId = state.highlightedId,
                )
                frame = frame.copy(arcs = frame.arcs + submenuArcs)
            }

            // Hold-to-commit fills sit above the wedges (the icons are a layer above the canvas).
            val holdProgressOf = { itemId: String -> state.animations.of(itemId).holdProgress.value }
            if (submenu == null) {
                frame = frame.copy(
                    arcs = frame.arcs + RingFrameBuilder.buildHoldFills(
                        items = items,
                        spans = resolved.mainSpans,
                        innerRadiusPx = resolved.mainInnerPx,
                        colors = style.colors,
                        expansionOf = expansionOf,
                        alphaOf = alphaOf,
                        progressOf = holdProgressOf,
                        outerRadiusFor = { expansion -> resolved.mainOuterFor(expansion) },
                    ),
                )
            } else if (parentSpan != null) {
                val children = submenu.second
                frame = frame.copy(
                    arcs = frame.arcs + RingFrameBuilder.buildHoldFills(
                        items = children,
                        spans = submenuChildSpans(resolved, style.dimensions, parentSpan, children),
                        innerRadiusPx = resolved.submenuInnerPx,
                        colors = style.colors,
                        expansionOf = expansionOf,
                        alphaOf = alphaOf,
                        progressOf = holdProgressOf,
                        outerRadiusFor = { expansion -> resolved.submenuOuterFor(expansion.coerceIn(0f, 1f)) },
                    ),
                )
            }

            if (seekSession != null) {
                val seekBand = if (state.path.isEmpty()) {
                    resolved.mainBand
                } else {
                    RadialBand(resolved.submenuInnerPx, resolved.submenuOuterPx)
                }
                val seekVisual = RingFrameBuilder.buildSeekVisual(
                    track = seekSession.track,
                    fraction = seekSession.displayValue.value,
                    band = seekBand,
                    resolved = resolved,
                    colors = style.colors,
                )
                frame = frame.copy(seek = seekVisual)
            }

            drawRing(frame, pathPool)
        }

        // Icons/content are real child nodes rather than a Canvas pass, since
        // OneRingIcon.Content is arbitrary consumer composable content. Which
        // glyph shows is decided by the two animated alphas, not by a flag --
        // that's what lets the icon -> X change be staged rather than crossfaded.
        RingContentLayer(
            items = items,
            spans = resolved.mainSpans,
            innerPx = resolved.mainInnerPx,
            centerPx = resolved.centerPx,
            colors = style.colors,
            highlightedId = state.highlightedId,
            outerPxOf = { id ->
                val base = resolved.mainOuterFor(expansionOf(id))
                if (id == activeMainItemId) base else lerp(resolved.mainInnerPx, base, mainSiblingReveal)
            },
            alphaOf = alphaOf,
            iconAlphaOf = iconAlphaOf,
            backIconAlphaOf = backIconAlphaOf,
        )

        if (submenu != null && parentSpan != null) {
            val (_, children) = submenu
            val childSpans = submenuChildSpans(resolved, style.dimensions, parentSpan, children)
            // Content rides the wedge as it grows: the same reveal-lerped inner
            // radius the arcs use, so icons travel inward with their sector
            // instead of sitting at the final radius while the shape catches up.
            val reveal = state.submenuReveal.value.coerceIn(0f, 1f)
            RingContentLayer(
                items = children,
                spans = childSpans,
                innerPx = lerp(resolved.submenuOuterPx, resolved.submenuInnerPx, reveal),
                centerPx = resolved.centerPx,
                colors = style.colors,
                highlightedId = state.highlightedId,
                outerPxOf = { id -> resolved.submenuOuterFor(expansionOf(id).coerceIn(0f, 1f)) },
                alphaOf = { id -> alphaOf(id) * reveal },
                scale = REVEAL_MIN_CONTENT_SCALE + (1f - REVEAL_MIN_CONTENT_SCALE) * reveal,
                iconAlphaOf = iconAlphaOf,
                backIconAlphaOf = backIconAlphaOf,
            )
        }

        val settled = state.phase == OneRingState.Phase.Settled
        when {
            settled && seekSession != null && state.path.isEmpty() -> {
                val seekItem = items.firstOrNull { it.id == seekSession.itemId }
                val index = items.indexOfFirst { it.id == seekSession.itemId }
                val span = resolved.mainSpans.getOrNull(index)
                if (seekItem != null && span != null) {
                    RingSemanticsLayer(
                        items = listOf(closeDescribed(seekItem)),
                        spans = listOf(span),
                        innerPx = resolved.mainInnerPx,
                        outerPx = resolved.mainSelectedOuterPx,
                        centerPx = resolved.centerPx,
                        highlightedId = null,
                        onActivate = { controller.closeDirectly() },
                    )
                }
            }
            settled && submenu == null -> {
                RingSemanticsLayer(
                    items = items,
                    spans = resolved.mainSpans,
                    innerPx = resolved.mainInnerPx,
                    outerPx = resolved.mainOuterPx,
                    centerPx = resolved.centerPx,
                    highlightedId = state.highlightedId,
                    onActivate = { id -> controller.commitDirectly(id) },
                )
            }
            settled && submenu != null && parentSpan != null -> {
                val (parent, children) = submenu
                RingSemanticsLayer(
                    items = listOf(closeDescribed(parent)),
                    spans = listOf(parentSpan),
                    innerPx = resolved.mainInnerPx,
                    outerPx = resolved.mainSelectedOuterPx,
                    centerPx = resolved.centerPx,
                    highlightedId = null,
                    onActivate = { controller.closeDirectly() },
                )
                // While a submenu child seek is active, only the close control
                // above is exposed -- adjusting the seek still needs a drag.
                if (seekSession == null) {
                    val childSpans = submenuChildSpans(resolved, style.dimensions, parentSpan, children)
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
            }
        }

        val centerScope = remember(state.path, state.highlightedId, state.mode) {
            OneRingCenterScope(
                currentMenuId = state.path.lastOrNull(),
                highlightedId = state.highlightedId,
                mode = state.mode,
            )
        }

        if (center != null) {
            val centerDiameterDp = with(density) { (resolved.centerRadiusPx * 2).toDp() }
            Box(
                modifier = Modifier.align(Alignment.Center).size(centerDiameterDp),
            ) {
                centerScope.center()
            }
        }

        // Drawn above `center` (and the ring's own content/semantics layers,
        // declared earlier) so it sits on top -- consistent with coreOverlay
        // being the intended surface for real interactive content like a
        // notification feed. See isInsideActiveCoreOverlay's doc comment for
        // how the ring's own gesture loop cedes this region while it's
        // interactive, and this composable's `coreOverlay` param doc for the
        // other half: the ring makes NO attempt to intercept or gate
        // coreOverlay's own content -- that's the content's responsibility,
        // using `centerScope.mode`.
        if (coreOverlay != null) {
            val coreOverlayDiameterDp = with(density) { (resolved.coreOverlayRadiusPx * 2).toDp() }
            Box(
                modifier = Modifier.align(Alignment.Center).size(coreOverlayDiameterDp),
            ) {
                centerScope.coreOverlay()
            }
        }
    }
}

/**
 * How small submenu content starts as its wedge grows in. Deliberately subtle:
 * the geometric growth is the effect, and a scale that started near zero would
 * read as a second, competing animation.
 */
private const val REVEAL_MIN_CONTENT_SCALE = 0.85f

private fun closeDescribed(item: OneRingItem): OneRingItem = when (item) {
    is OneRingItem.Menu -> item.copy(contentDescription = "Close ${item.label}")
    is OneRingItem.Seek -> item.copy(contentDescription = "Close ${item.label}")
    is OneRingItem.Action -> item
}
