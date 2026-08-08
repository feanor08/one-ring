package com.feanor08.onering.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import com.feanor08.onering.OneRingEvent
import com.feanor08.onering.OneRingHapticEvent
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.HitTest
import com.feanor08.onering.geometry.RadialBand
import com.feanor08.onering.geometry.SeekTrack
import com.feanor08.onering.geometry.angleOfOffset
import com.feanor08.onering.style.OneRingBehavior
import com.feanor08.onering.style.OneRingDimensions
import com.feanor08.onering.style.OneRingMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Drives press-scrub-release highlighting and the open/close/seek selection
 * transitions from a low-level pointer loop (`awaitEachGesture`, not
 * `detectDragGestures`, which applies touch slop the interaction can't afford
 * -- see docs/PLAN.md section 9.1). One instance is created per [OneRingState]
 * and never recreated across recompositions, so an in-flight gesture is never
 * restarted; [update] refreshes its view of the current items/geometry/style/
 * callback each frame instead.
 *
 * [commitDirectly] and [closeDirectly] give the accessibility semantics layer
 * a way to trigger exactly the same commit/close logic a pointer gesture
 * would, without needing to fake pointer events.
 *
 * Only one level of nesting is interactive so far (a committed submenu leaf
 * fires [onAction] and closes back to the top level); deeper nesting is a
 * later phase.
 */
internal class RingInteractionController(private val state: OneRingState) {
    private var items: List<OneRingItem> = emptyList()
    private var resolved: ResolvedRing? = null
    private var dimensions: OneRingDimensions? = null
    private var motion: OneRingMotion? = null
    private var behavior: OneRingBehavior? = null
    private var haptics: OneRingHaptics = OneRingHaptics.None
    private var onAction: (OneRingItem.Action) -> Unit = {}
    private var onEvent: ((OneRingEvent) -> Unit)? = null

    fun update(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        dimensions: OneRingDimensions,
        motion: OneRingMotion,
        behavior: OneRingBehavior,
        haptics: OneRingHaptics,
        onAction: (OneRingItem.Action) -> Unit,
        onEvent: ((OneRingEvent) -> Unit)?,
    ) {
        this.items = items
        this.resolved = resolved
        this.dimensions = dimensions
        this.motion = motion
        this.behavior = behavior
        this.haptics = haptics
        this.onAction = onAction
        this.onEvent = onEvent
    }

    private fun haptic(event: OneRingHapticEvent) {
        if (behavior?.hapticsEnabled == true) haptics.perform(event)
    }

    private fun setHighlight(id: String?) {
        if (state.highlightedId == id) return
        val previous = state.highlightedId
        state.highlightedId = id
        onEvent?.invoke(OneRingEvent.HighlightChanged(id))
        haptic(OneRingHapticEvent.HighlightChanged)
        val hoverSpec = motion?.hover ?: return

        previous?.let { previousId ->
            state.scope.launch { state.animations.of(previousId).expansion.animateTo(0f, hoverSpec) }
        }
        id?.let { newId ->
            state.scope.launch { state.animations.of(newId).expansion.animateTo(1f, hoverSpec) }
        }
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

    private fun hitTestSubmenu(position: Offset, r: ResolvedRing, childSpans: List<AngularSpan>): Int? {
        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(
            (r.submenuInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f),
            r.submenuHoverOuterPx + r.touchSlopRadialPx,
        )
        return HitTest.hitWedge(angle, radius, childSpans, band)
    }

    private fun parentSpanFor(parentId: String, r: ResolvedRing): AngularSpan? =
        r.mainSpans.getOrNull(items.indexOfFirst { it.id == parentId })

    /** The item currently rendered as the close (X) control -- a main seek, or a submenu parent. */
    private fun closeTargetId(): String? =
        if (state.path.isEmpty()) state.seekSession?.itemId else state.path.lastOrNull()

    /** True (and a close/exit transition started) if [position] hits the current close control's wedge. */
    private fun tryClosePress(position: Offset): Boolean {
        val r = resolved ?: return false
        val targetId = closeTargetId() ?: return false
        if (state.path.isNotEmpty() && behavior?.closeOnParentPress != true) return false
        val span = parentSpanFor(targetId, r) ?: return false

        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(r.mainInnerPx, r.mainSelectedOuterPx + r.touchSlopRadialPx)
        if (!band.contains(radius) || !span.contains(angle)) return false

        if (state.path.isEmpty()) beginCloseMainSeek(targetId) else beginClose(targetId)
        return true
    }

    /** Directly closes the current submenu or main-ring seek, as if its close wedge were pressed. */
    fun closeDirectly() {
        val targetId = closeTargetId() ?: return
        if (state.path.isEmpty()) beginCloseMainSeek(targetId) else beginClose(targetId)
    }

    /** Directly commits [id] as if it had been highlighted and released -- for the semantics layer. */
    fun commitDirectly(id: String) {
        setHighlight(id)
        commit()
    }

    private fun commit() {
        val id = state.highlightedId ?: return
        setHighlight(null)
        if (state.path.isNotEmpty()) {
            commitSubmenuChild(id)
        } else {
            commitMainItem(id)
        }
    }

    private fun commitMainItem(id: String) {
        when (val item = items.firstOrNull { it.id == id }) {
            is OneRingItem.Action -> commitAction(item)
            is OneRingItem.Menu -> if (item.enabled) beginOpen(item) else haptic(OneRingHapticEvent.Rejected)
            is OneRingItem.Seek -> if (item.enabled) beginMainSeek(item) else haptic(OneRingHapticEvent.Rejected)
            null -> Unit
        }
    }

    private fun commitSubmenuChild(id: String) {
        val (parent, children) = currentSubmenu(items, state.path) ?: return
        when (val item = children.firstOrNull { it.id == id }) {
            is OneRingItem.Action -> {
                commitAction(item)
                if (item.enabled) beginClose(parent.id)
            }
            is OneRingItem.Seek -> if (item.enabled) {
                beginSubmenuSeek(
                    parent,
                    item,
                )
            } else {
                haptic(OneRingHapticEvent.Rejected)
            }
            // Nested Menu within a submenu isn't interactive yet.
            else -> Unit
        }
    }

    private fun commitAction(item: OneRingItem.Action) {
        if (!item.enabled) {
            haptic(OneRingHapticEvent.Rejected)
            return
        }
        onAction(item)
        onEvent?.invoke(OneRingEvent.ActionCommitted(item.id))
        haptic(OneRingHapticEvent.Committed)
    }

    private fun beginOpen(item: OneRingItem.Menu) {
        val m = motion ?: return
        state.phase = OneRingState.Phase.Opening(item.id)

        items.asSequence().filter { it.id != item.id }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).expansion.animateTo(0f, m.hover) }
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(0f, m.siblingExit) }
        }
        state.scope.launch { state.submenuReveal.animateTo(1f, m.submenuReveal) }

        state.scope.launch {
            // The parent's own expansion animation is the single source of truth
            // for when the open transition settles -- it continues from wherever
            // its hover animation currently is (see ItemAnimation), never from a
            // hardcoded start, so there is no stutter regardless of how far into
            // the hover animation the release landed.
            state.animations.of(item.id).expansion.animateTo(2f, m.select)
            state.path = state.path + item.id
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.MenuOpened(item.id))
            haptic(OneRingHapticEvent.MenuOpened)
        }
    }

    private fun beginClose(parentId: String) {
        val m = motion ?: return
        state.phase = OneRingState.Phase.Closing(parentId)
        setHighlight(null)

        val exitingSeekId = state.seekSession?.itemId
        state.seekSession?.hintJob?.cancel()
        state.seekSession = null
        exitingSeekId?.let { seekId ->
            state.scope.launch { state.animations.of(seekId).expansion.animateTo(0f, m.hover) }
            state.scope.launch { state.animations.of(seekId).contentAlpha.animateTo(1f, m.siblingExit) }
        }

        items.asSequence().filter { it.id != parentId }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(1f, m.siblingExit) }
        }
        state.scope.launch { state.submenuReveal.animateTo(0f, m.submenuReveal) }

        state.scope.launch {
            state.animations.of(parentId).expansion.animateTo(0f, m.close)
            state.path = state.path.dropLast(1)
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.MenuClosed(parentId))
            haptic(OneRingHapticEvent.MenuClosed)
            exitingSeekId?.let { onEvent?.invoke(OneRingEvent.SeekExited(it)) }
        }
    }

    // ---- Seek: entry, exit, hint, drag ----------------------------------

    private fun findSeekItem(id: String): OneRingItem.Seek? {
        items.firstOrNull { it.id == id }?.let { return it as? OneRingItem.Seek }
        return currentSubmenu(items, state.path)?.second?.firstOrNull { it.id == id } as? OneRingItem.Seek
    }

    private fun fractionForValue(item: OneRingItem.Seek): Float {
        val span = item.valueRange.endInclusive - item.valueRange.start
        if (span <= 0f) return 0f
        return ((item.value - item.valueRange.start) / span).coerceIn(0f, 1f)
    }

    private fun beginMainSeek(item: OneRingItem.Seek) {
        val m = motion ?: return
        val r = resolved ?: return
        val index = items.indexOfFirst { it.id == item.id }
        val anchorSpan = r.mainSpans.getOrNull(index) ?: return

        state.phase = OneRingState.Phase.Opening(item.id)
        items.asSequence().filter { it.id != item.id }.forEach { sibling ->
            // Fade in place -- unlike opening a submenu, siblings don't collapse.
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(0f, m.siblingExit) }
        }

        state.scope.launch {
            state.animations.of(item.id).expansion.animateTo(2f, m.select)
            val track = SeekTrack.build(anchor = anchorSpan)
            state.seekSession = OneRingState.SeekSession(itemId = item.id, track = track)
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.SeekEntered(item.id))
            haptic(OneRingHapticEvent.SeekEntered)
            runSeekHint(item)
        }
    }

    private fun beginCloseMainSeek(itemId: String) {
        val m = motion ?: return
        state.phase = OneRingState.Phase.Closing(itemId)
        state.seekSession?.hintJob?.cancel()
        state.seekSession = null

        items.asSequence().filter { it.id != itemId }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(1f, m.siblingExit) }
        }
        state.scope.launch {
            state.animations.of(itemId).expansion.animateTo(0f, m.close)
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.SeekExited(itemId))
            haptic(OneRingHapticEvent.SeekExited)
        }
    }

    private fun beginSubmenuSeek(parent: OneRingItem.Menu, item: OneRingItem.Seek) {
        val m = motion ?: return
        val r = resolved ?: return
        val d = dimensions ?: return
        val (_, children) = currentSubmenu(items, state.path) ?: return
        val parentSpan = parentSpanFor(parent.id, r) ?: return
        val childSpans = submenuChildSpans(r, d, parentSpan, children.size)
        val index = children.indexOfFirst { it.id == item.id }
        val anchorSpan = childSpans.getOrNull(index) ?: return

        state.phase = OneRingState.Phase.Opening(item.id)
        children.asSequence().filter { it.id != item.id }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(0f, m.siblingExit) }
        }

        state.scope.launch {
            // Hover, not selected: the outer parent (already X) stays the close
            // control, the seek child itself never morphs into one.
            state.animations.of(item.id).expansion.animateTo(1f, m.hover)
            val track = SeekTrack.build(anchor = anchorSpan, excluded = listOf(parentSpan))
            state.seekSession = OneRingState.SeekSession(itemId = item.id, track = track)
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.SeekEntered(item.id))
            haptic(OneRingHapticEvent.SeekEntered)
            runSeekHint(item)
        }
    }

    private fun runSeekHint(item: OneRingItem.Seek) {
        val session = state.seekSession ?: return
        val m = motion ?: return
        val b = behavior ?: return
        val originalFraction = fractionForValue(item)

        if (!b.seekHintEnabled) {
            state.scope.launch { session.displayValue.snapTo(originalFraction) }
            return
        }

        session.hintJob = state.scope.launch {
            val overshootFraction = (originalFraction + b.seekHintOvershootFraction).coerceIn(0f, 1f)
            // The one legitimate snapTo in this codebase: resetting the *display*
            // value (never the controlled value) to the track's zero before the
            // hint rises -- see docs/interaction-spec.md's seek hint section.
            session.displayValue.snapTo(0f)
            session.displayValue.animateTo(overshootFraction, m.seekHintRise)
            delay(90)
            session.displayValue.animateTo(originalFraction, m.seekHintSettle)
        }
    }

    // Deliberately NOT suspend: AwaitPointerEventScope's suspend functions are
    // "restricted suspension" (to guarantee synchronous gesture processing),
    // so an arbitrary suspend call like Animatable.snapTo() can't be awaited
    // directly from inside it -- the actual snapTo is dispatched onto
    // state.scope instead, the same fire-and-forget pattern setHighlight()
    // already uses for its animateTo() calls.
    private fun updateSeekValue(position: Offset, session: OneRingState.SeekSession, item: OneRingItem.Seek) {
        val r = resolved ?: return
        val band = if (state.path.isEmpty()) {
            RadialBand((r.mainInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f), r.mainOuterPx + r.touchSlopRadialPx)
        } else {
            RadialBand(
                (r.submenuInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f),
                r.submenuOuterPx + r.touchSlopRadialPx,
            )
        }
        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        if (!band.contains(radius)) return

        val fraction = session.track.fractionAt(angle)
        val range = item.valueRange
        var value = range.start + fraction * (range.endInclusive - range.start)
        if (item.steps > 0) {
            val stepSize = (range.endInclusive - range.start) / (item.steps + 1)
            if (stepSize > 0f) {
                value = range.start + ((value - range.start) / stepSize).roundToInt() * stepSize
            }
        }
        value = value.coerceIn(range.start, range.endInclusive)

        item.onValueChange(value)
        state.scope.launch { session.displayValue.snapTo(fraction) }
    }

    private suspend fun AwaitPointerEventScope.trackSeekGesture(
        down: PointerInputChange,
        session: OneRingState.SeekSession,
        item: OneRingItem.Seek,
    ) {
        session.hintJob?.cancel()
        session.hintJob = null
        session.dragging = true
        updateSeekValue(down.position, session, item)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    updateSeekValue(change.position, session, item)
                    item.onValueChangeFinished?.invoke()
                    break
                }
                updateSeekValue(change.position, session, item)
                change.consume()
            }
        } finally {
            session.dragging = false
        }
    }

    // ---- Gesture entry point ---------------------------------------------

    private suspend fun AwaitPointerEventScope.trackGesture(down: PointerInputChange, hitId: (Offset) -> String?) {
        state.touching = true
        setHighlight(hitId(down.position))
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    setHighlight(hitId(change.position))
                    commit()
                    break
                }
                setHighlight(hitId(change.position))
                change.consume()
            }
        } finally {
            state.touching = false
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

            val session = state.seekSession
            if (session != null) {
                val item = findSeekItem(session.itemId) ?: return@awaitEachGesture
                trackSeekGesture(down, session, item)
                return@awaitEachGesture
            }

            val r = resolved ?: return@awaitEachGesture

            if (state.path.isEmpty()) {
                trackGesture(down) { pos -> hitTestMainRing(pos, r)?.let { items.getOrNull(it)?.id } }
                return@awaitEachGesture
            }

            val d = dimensions ?: return@awaitEachGesture
            val (parent, children) = currentSubmenu(items, state.path) ?: return@awaitEachGesture
            val parentSpan = parentSpanFor(parent.id, r) ?: return@awaitEachGesture
            val childSpans = submenuChildSpans(r, d, parentSpan, children.size)
            trackGesture(down) { pos -> hitTestSubmenu(pos, r, childSpans)?.let { children[it].id } }
        }
    }
}
