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
import com.feanor08.onering.internal.drawRing
import com.feanor08.onering.internal.resolveRing
import com.feanor08.onering.style.OneRingStyle

/**
 * A radial interaction wheel: press, scrub, release-to-commit -- not a set of
 * ordinary buttons layered on a canvas. Nested submenus and seek controls land
 * in later phases (see docs/PLAN.md); this entry point currently supports the
 * main ring's press-scrub-highlight-release flow for leaf [OneRingItem.Action]s.
 *
 * @param items the top-level wedges, in clockwise order starting at
 *   [com.feanor08.onering.style.OneRingDimensions.startAngleDeg].
 * @param onAction called when a leaf [OneRingItem.Action] is committed (released
 *   while highlighted).
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
        controller.update(items, resolved, style.motion, onAction)

        val frame = RingFrameBuilder.build(items, resolved, style.colors) { itemId ->
            state.animations.of(itemId).expansion.value
        }
        drawRing(frame, pathPool)
    }
}
