package com.feanor08.onering

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.feanor08.onering.internal.RingFrameBuilder
import com.feanor08.onering.internal.RingInteractionController
import com.feanor08.onering.internal.RingPathPool
import com.feanor08.onering.internal.currentSubmenu
import com.feanor08.onering.internal.drawRing
import com.feanor08.onering.internal.resolveRing
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
 * @param items the top-level wedges, in clockwise order starting at
 *   [com.feanor08.onering.style.OneRingDimensions.startAngleDeg].
 * @param onAction called when a leaf [OneRingItem.Action] is committed (released
 *   while highlighted), at any nesting depth currently supported.
 * @param state navigation/highlight state; hoist with [rememberOneRingState] to
 *   observe or drive it programmatically.
 * @param style colors, sizes, and motion; see [OneRingDefaults.style].
 */
@Composable
public fun OneRing(
    items: List<OneRingItem>,
    onAction: (OneRingItem.Action) -> Unit,
    modifier: Modifier = Modifier,
    state: OneRingState = rememberOneRingState(),
    style: OneRingStyle = OneRingDefaults.style(),
) {
    val density = LocalDensity.current
    val pathPool = remember { RingPathPool() }
    val controller = remember(state) { RingInteractionController(state) }

    // pointerInput is keyed on Unit, never on items/state/style, so an
    // in-flight gesture is never restarted by an unrelated recomposition --
    // rememberUpdatedState lets the (stable, never-recreated) suspend block
    // always see the current controller instance. See docs/PLAN.md section 9.1.
    val currentController = rememberUpdatedState(controller)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { currentController.value.handleGestures(this) },
    ) {
        val canvasSize = Size(size.width, size.height)
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@Canvas

        val resolved = resolveRing(canvasSize, density, style.dimensions, items.size)
        controller.update(items, resolved, style.dimensions, style.motion, style.behavior, onAction)

        val expansionOf = { itemId: String -> state.animations.of(itemId).expansion.value }

        var frame = RingFrameBuilder.build(
            items = items,
            resolved = resolved,
            colors = style.colors,
            expansionOf = expansionOf,
            alphaOf = { itemId -> state.animations.of(itemId).contentAlpha.value },
        )

        val submenu = currentSubmenu(items, state.path)
        val parentIndex = submenu?.let { (parent, _) -> items.indexOfFirst { it.id == parent.id } }
        val parentSpan = parentIndex?.let { resolved.mainSpans.getOrNull(it) }
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
}
