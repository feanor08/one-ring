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
import com.feanor08.onering.style.OneRingMotion
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Drives press-scrub-release highlighting from a low-level pointer loop
 * (`awaitEachGesture`, not `detectDragGestures`, which applies touch slop the
 * interaction can't afford -- see docs/PLAN.md section 9.1). One instance is
 * created per [OneRingState] and never recreated across recompositions, so an
 * in-flight gesture is never restarted; [update] refreshes its view of the
 * current items/geometry/motion/callback each frame instead.
 */
internal class RingInteractionController(private val state: OneRingState) {
    private var items: List<OneRingItem> = emptyList()
    private var resolved: ResolvedRing? = null
    private var motion: OneRingMotion? = null
    private var onAction: (OneRingItem.Action) -> Unit = {}

    fun update(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        motion: OneRingMotion,
        onAction: (OneRingItem.Action) -> Unit,
    ) {
        this.items = items
        this.resolved = resolved
        this.motion = motion
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
        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(
            (r.mainInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f),
            r.mainHoverOuterPx + r.touchSlopRadialPx,
        )
        val hitIndex = HitTest.hitWedge(angle, radius, r.mainSpans, band)
        setHighlight(hitIndex?.let { items.getOrNull(it)?.id })
    }

    private fun commit() {
        val id = state.highlightedId ?: return
        val item = items.firstOrNull { it.id == id }
        setHighlight(null)
        // Menu/Seek commit (opening a submenu or entering seek mode) arrives in
        // Phase 4/5/6. For now, only leaf Action items produce a callback.
        if (item is OneRingItem.Action && item.enabled) onAction(item)
    }

    suspend fun handleGestures(scope: PointerInputScope) = with(scope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.touching = true
            down.consume()
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
