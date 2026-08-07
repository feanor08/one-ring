package com.feanor08.onering.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.HitTest
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.angleOfOffset
import com.feanor08.onering.style.OneRingBehavior
import com.feanor08.onering.style.OneRingMotion
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Drives press-scrub-release highlighting and the main-ring selection
 * transition from a low-level pointer loop (`awaitEachGesture`, not
 * `detectDragGestures`, which applies touch slop the interaction can't afford
 * -- see docs/PLAN.md section 9.1). One instance is created per [OneRingState]
 * and never recreated across recompositions, so an in-flight gesture is never
 * restarted; [update] refreshes its view of the current items/geometry/style/
 * callback each frame instead.
 *
 * Submenu content itself (rendering a committed Menu's children) is Phase 5;
 * this phase only drives the parent wedge's own selected/close-control
 * transition and fades its top-level siblings out.
 */
internal class RingInteractionController(private val state: OneRingState) {
    private var items: List<OneRingItem> = emptyList()
    private var resolved: ResolvedRing? = null
    private var motion: OneRingMotion? = null
    private var behavior: OneRingBehavior? = null
    private var onAction: (OneRingItem.Action) -> Unit = {}

    fun update(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        motion: OneRingMotion,
        behavior: OneRingBehavior,
        onAction: (OneRingItem.Action) -> Unit,
    ) {
        this.items = items
        this.resolved = resolved
        this.motion = motion
        this.behavior = behavior
        this.onAction = onAction
    }

    private fun setHighlight(id: String?) {
        if (state.highlightedId == id) return
        val previous = state.highlightedId
        state.highlightedId = id
        val hoverSpec = motion?.hover ?: return

        previous?.let { previousId ->
            state.scope.launch { state.animations.of(previousId).expansion.animateTo(0f, hoverSpec) }
        }
        id?.let { newId ->
            state.scope.launch { state.animations.of(newId).expansion.animateTo(1f, hoverSpec) }
        }
    }

    private fun updateHighlight(position: Offset) {
        val r = resolved ?: return
        val hitIndex = hitTestMainRing(position, r)
        setHighlight(hitIndex?.let { items.getOrNull(it)?.id })
    }

    private fun hitTestMainRing(position: Offset, r: ResolvedRing): Int? {
        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(
            (r.mainInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f),
            r.mainHoverOuterPx + r.touchSlopRadialPx,
        )
        return HitTest.hitWedge(angle, radius, r.mainSpans, band)
    }

    /** True (and a close transition started) if [position] hits the open parent's grown/selected wedge. */
    private fun tryClosePress(position: Offset): Boolean {
        val parentId = state.path.lastOrNull() ?: return false
        if (behavior?.closeOnParentPress != true) return false
        val r = resolved ?: return false
        val parentIndex = items.indexOfFirst { it.id == parentId }
        val span = r.mainSpans.getOrNull(parentIndex) ?: return false

        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(r.mainInnerPx, r.mainSelectedOuterPx + r.touchSlopRadialPx)
        if (!band.contains(radius) || !span.contains(angle)) return false

        beginClose(parentId)
        return true
    }

    private fun commit() {
        val id = state.highlightedId ?: return
        val item = items.firstOrNull { it.id == id }
        setHighlight(null)
        when {
            item is OneRingItem.Action && item.enabled -> onAction(item)
            item is OneRingItem.Menu && item.enabled -> beginOpen(item)
            // Seek commit (entering seek mode) arrives in Phase 6.
        }
    }

    private fun beginOpen(item: OneRingItem.Menu) {
        val m = motion ?: return
        state.phase = OneRingState.Phase.Opening(item.id)

        items.asSequence().filter { it.id != item.id }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).expansion.animateTo(0f, m.hover) }
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(0f, m.siblingExit) }
        }

        state.scope.launch {
            // The parent's own expansion animation is the single source of truth
            // for when the open transition settles -- it continues from wherever
            // its hover animation currently is (see ItemAnimation), never from a
            // hardcoded start, so there is no stutter regardless of how far into
            // the hover animation the release landed.
            state.animations.of(item.id).expansion.animateTo(2f, m.select)
            state.path = state.path + item.id
            state.phase = OneRingState.Phase.Settled
        }
    }

    private fun beginClose(parentId: String) {
        val m = motion ?: return
        state.phase = OneRingState.Phase.Closing(parentId)
        setHighlight(null)

        items.asSequence().filter { it.id != parentId }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(1f, m.siblingExit) }
        }

        state.scope.launch {
            state.animations.of(parentId).expansion.animateTo(0f, m.close)
            state.path = state.path.dropLast(1)
            state.phase = OneRingState.Phase.Settled
        }
    }

    suspend fun handleGestures(scope: PointerInputScope) = with(scope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()

            // Input is gated to nothing but a fresh down while a transition is
            // in flight -- see docs/PLAN.md section 6.3.
            if (state.phase != OneRingState.Phase.Settled) return@awaitEachGesture

            if (tryClosePress(down.position)) return@awaitEachGesture

            // Inside a submenu, the only valid interaction (until Phase 5 renders
            // its children) is pressing the close wedge, handled above.
            if (state.path.isNotEmpty()) return@awaitEachGesture

            state.touching = true
            updateHighlight(down.position)

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        updateHighlight(change.position)
                        commit()
                        break
                    }
                    updateHighlight(change.position)
                    change.consume()
                }
            } finally {
                state.touching = false
            }
        }
    }
}
