package com.feanor08.onering.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
/**
 * How long after a parent commits before its submenu starts growing. Small
 * enough that the two read as one coordinated transition (the submenu is still
 * growing while the parent is still expanding), large enough that the parent
 * visibly commits first.
 */
private const val SUBMENU_REVEAL_STAGGER_MS = 45L

/**
 * How late into a wedge's geometry change its paired glyph (X on open, the
 * original icon on close) begins fading in, expressed as the remaining
 * fraction of the distance still to travel. 0.22 means the fade starts once
 * ~78% of the distance has been covered -- late enough that it arrives
 * together with the settled shape rather than visibly trailing it, early
 * enough that it isn't tacked on after a dead pause. See [watchThenFade].
 */
private const val LATE_GLYPH_FADE_FRACTION = 0.22f

/** Polling granularity for [watchThenFade] -- coarse is fine, it only gates *when* a fade starts. */
private const val GEOMETRY_POLL_MS = 16L

internal class RingInteractionController(private val state: OneRingState) {
    private var items: List<OneRingItem> = emptyList()
    private var resolved: ResolvedRing? = null
    private var dimensions: OneRingDimensions? = null
    private var motion: OneRingMotion? = null
    private var behavior: OneRingBehavior? = null
    private var haptics: OneRingHaptics = OneRingHaptics.None
    private var onAction: (OneRingItem.Action) -> Unit = {}
    private var onEvent: ((OneRingEvent) -> Unit)? = null
    private var hasCoreOverlay: Boolean = false

    fun update(
        items: List<OneRingItem>,
        resolved: ResolvedRing,
        dimensions: OneRingDimensions,
        motion: OneRingMotion,
        behavior: OneRingBehavior,
        haptics: OneRingHaptics,
        onAction: (OneRingItem.Action) -> Unit,
        onEvent: ((OneRingEvent) -> Unit)?,
        hasCoreOverlay: Boolean = false,
    ) {
        this.items = items
        this.resolved = resolved
        this.dimensions = dimensions
        this.motion = motion
        this.behavior = behavior
        this.haptics = haptics
        this.onAction = onAction
        this.onEvent = onEvent
        this.hasCoreOverlay = hasCoreOverlay
    }

    private fun haptic(event: OneRingHapticEvent) {
        if (behavior?.hapticsEnabled == true) haptics.perform(event)
    }

    /** Internal rather than private so transition tests can start a real hover before committing. */
    internal fun setHighlight(id: String?) {
        if (state.highlightedId == id) return
        val previous = state.highlightedId
        state.highlightedId = id
        onEvent?.invoke(OneRingEvent.HighlightChanged(id))
        haptic(OneRingHapticEvent.HighlightChanged)
        val hoverSpec = motion?.hover ?: return

        // The current back control already sits at its expanded geometry; hover
        // must not drag it back down to (or up from) the ordinary hover radius.
        // Highlighting it is a *styling* change only -- see the class doc on
        // structural vs interaction state.
        val backControl = closeTargetId()

        previous?.takeIf { it != backControl }?.let { previousId ->
            state.scope.launch { state.animations.of(previousId).expansion.animateTo(0f, hoverSpec) }
        }
        id?.takeIf { it != backControl }?.let { newId ->
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

    /**
     * The id of the back (X) control if [position] is inside its wedge, else
     * null. This is an ordinary hit test feeding ordinary highlighting -- the
     * back control commits on release like every other wedge, so pressing it
     * and scrubbing away cancels the close.
     */
    /** Test hook for [hitBackControl], which is otherwise only reachable through a pointer gesture. */
    internal fun hitBackControlForTest(position: Offset): String? = hitBackControl(position)

    private fun hitBackControl(position: Offset): String? {
        val r = resolved ?: return null
        val targetId = closeTargetId() ?: return null
        if (state.path.isNotEmpty() && behavior?.closeOnParentPress != true) return null
        val span = parentSpanFor(targetId, r) ?: return null

        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        val angle = angleOfOffset(dx, dy)
        val radius = hypot(dx, dy)
        val band = RadialBand(
            (r.mainInnerPx - r.touchSlopRadialPx).coerceAtLeast(0f),
            r.mainSelectedOuterPx + r.touchSlopRadialPx,
        )
        if (!band.contains(radius) || !span.contains(angle)) return null
        return targetId
    }

    /**
     * Directly closes the current submenu or main-ring seek, as if its close
     * wedge were pressed. Ignored mid-transition, matching the pointer path
     * (which is gated in [handleGestures]) -- otherwise a close could interleave
     * with an in-flight open and the two coroutines would race to set `phase`
     * and `path`, leaving the ring stuck in a transitional state.
     */
    fun closeDirectly() {
        if (state.phase != OneRingState.Phase.Settled) return
        val targetId = closeTargetId() ?: return
        if (state.path.isEmpty()) beginCloseMainSeek(targetId) else beginClose(targetId)
    }

    /**
     * Directly commits [id] as if it had been highlighted and released -- for
     * the semantics layer. Gated mid-transition for the same reason as
     * [closeDirectly].
     */
    fun commitDirectly(id: String) {
        if (state.phase != OneRingState.Phase.Settled) return
        setHighlight(id)
        commit()
    }

    /**
     * Release-to-commit. The back control is committed here like any other
     * wedge rather than acting on pointer-down, so pressing X and scrubbing
     * away before release does *not* close -- consistent with the rest of the
     * wheel.
     */
    private fun commit() {
        val id = state.highlightedId ?: return
        setHighlight(null)
        val session = state.seekSession
        when {
            state.path.isNotEmpty() && id == state.path.last() -> beginClose(id)
            state.path.isNotEmpty() -> commitSubmenuChild(id)
            session != null && id == session.itemId -> beginCloseMainSeek(id)
            else -> commitMainItem(id)
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
        // Non-selected main wedges collapse toward the inner radius at the same
        // time the parent grows and the submenu reveals -- the mirror of
        // beginClose's regrow -- so the whole ring reads as one continuous
        // transform instead of "selected item grows, the rest just vanishes".
        state.scope.launch { state.mainSiblingReveal.animateTo(0f, m.select) }

        // Stage 1 of the icon -> X change: the original glyph fades out on its
        // own, before the X is given any opacity at all. See watchThenFade.
        state.scope.launch { state.animations.of(item.id).iconAlpha.animateTo(0f, m.iconMorph) }

        val expansion = state.animations.of(item.id).expansion
        val startExpansion = expansion.value
        // The parent's own expansion continues from wherever its hover animation
        // currently is (see ItemAnimation), never from a hardcoded start, so
        // there is no stutter regardless of how far into the hover animation the
        // release landed.
        val parentExpansion = state.scope.launch { expansion.animateTo(2f, m.select) }
        // Stage 3: the X begins fading in during roughly the final quarter of
        // the wedge's growth rather than only once it's fully settled.
        watchThenFade(expansion, startExpansion, 2f, state.animations.of(item.id).backIconAlpha, 1f, m.iconMorph)

        state.scope.launch {
            // Ordering here is the whole reason the submenu appears to grow.
            // The renderer only draws submenu arcs when `path` names an open
            // menu, so publishing `path` late (e.g. after the parent's select
            // animation) means every intermediate reveal value is computed into
            // a submenu that isn't being drawn yet -- the first visible frame
            // then lands at reveal ~1 and the submenu pops in at full size.
            //
            // So: pin the reveal to zero thickness FIRST, publish `path` only
            // once it is zero, and only then animate it open. Input stays gated
            // by Phase.Opening throughout, so an observable path mid-transition
            // can't be interacted with.
            state.submenuReveal.snapTo(0f)
            state.path = state.path + item.id
            // A short stagger so the parent visibly commits first; the two
            // animations still overlap for most of their duration.
            delay(SUBMENU_REVEAL_STAGGER_MS)
            state.submenuReveal.animateTo(1f, m.submenuReveal)

            parentExpansion.join()
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
        }

        // Every current submenu child's alpha is restored here, not just an
        // exiting seek's -- a sibling dimmed while one child was mid-seek (see
        // beginSubmenuSeek) must not stay invisible the *next* time this
        // submenu opens. RingAnimations entries persist for the state's whole
        // lifetime, so a channel nothing ever restores stays broken forever,
        // not just for this transition.
        currentSubmenu(items, state.path)?.second?.forEach { child ->
            state.scope.launch { state.animations.of(child.id).contentAlpha.animateTo(1f, m.siblingExit) }
        }

        items.asSequence().filter { it.id != parentId }.forEach { sibling ->
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(1f, m.siblingExit) }
        }
        // Non-selected main wedges regrow from the inner radius at the same
        // time the submenu collapses and the parent shrinks -- see beginOpen.
        state.scope.launch { state.mainSiblingReveal.animateTo(1f, m.close) }

        state.scope.launch {
            // Staged reverse: the X fades out completely *before* any geometry
            // moves, so it's never visible at the same time as the returning icon.
            state.animations.of(parentId).backIconAlpha.animateTo(0f, m.iconMorph)

            // `path` must stay published until the wedges have actually finished
            // shrinking, or they'd vanish mid-collapse instead of closing down to
            // zero thickness.
            val shrink = state.scope.launch { state.submenuReveal.animateTo(0f, m.submenuReveal) }
            val expansion = state.animations.of(parentId).expansion
            val startExpansion = expansion.value
            val returnJob = state.scope.launch { expansion.animateTo(0f, m.close) }
            // The original icon begins fading in during roughly the final
            // quarter of the parent's return -- the mirror of beginOpen's X.
            watchThenFade(expansion, startExpansion, 0f, state.animations.of(parentId).iconAlpha, 1f, m.iconMorph)
            returnJob.join()
            shrink.join()

            state.path = state.path.dropLast(1)
            state.phase = OneRingState.Phase.Settled
            onEvent?.invoke(OneRingEvent.MenuClosed(parentId))
            haptic(OneRingHapticEvent.MenuClosed)
            exitingSeekId?.let { onEvent?.invoke(OneRingEvent.SeekExited(it)) }
        }
    }

    /**
     * Starts [channel] fading toward [fadeTarget] once [progress] has covered
     * roughly `1 - LATE_GLYPH_FADE_FRACTION` of the distance from [startValue]
     * to [endValue] -- see [LATE_GLYPH_FADE_FRACTION]. Polls rather than
     * reacting to a snapshot, since [Animatable] is a plain class, not
     * snapshot-`State`-backed -- there's nothing to observe reactively.
     */
    private fun watchThenFade(
        progress: Animatable<Float, *>,
        startValue: Float,
        endValue: Float,
        channel: Animatable<Float, *>,
        fadeTarget: Float,
        fadeSpec: AnimationSpec<Float>,
    ) {
        val threshold = startValue + (endValue - startValue) * (1f - LATE_GLYPH_FADE_FRACTION)
        val risingToward = endValue >= startValue
        state.scope.launch {
            while (if (risingToward) progress.value < threshold else progress.value > threshold) {
                delay(GEOMETRY_POLL_MS)
            }
            channel.animateTo(fadeTarget, fadeSpec)
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
            state.scope.launch { state.animations.of(sibling.id).contentAlpha.animateTo(0f, m.siblingExit) }
        }
        // Same collapse-toward-the-inner-edge language as opening a submenu,
        // even though a main seek's track occupies the *same* band the
        // siblings just vacated (buildSeekVisual always draws the full
        // mainBand regardless of this value) -- the point is that the ring
        // still reads as one continuous transform, not "seek appears, others
        // just vanish".
        state.scope.launch { state.mainSiblingReveal.animateTo(0f, m.select) }

        // Same staged icon -> X change as a menu parent: glyph out first.
        state.scope.launch { state.animations.of(item.id).iconAlpha.animateTo(0f, m.iconMorph) }

        val expansion = state.animations.of(item.id).expansion
        val startExpansion = expansion.value
        val grow = state.scope.launch { expansion.animateTo(2f, m.select) }
        watchThenFade(expansion, startExpansion, 2f, state.animations.of(item.id).backIconAlpha, 1f, m.iconMorph)

        state.scope.launch {
            grow.join()
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
        state.scope.launch { state.mainSiblingReveal.animateTo(1f, m.close) }

        state.scope.launch {
            // Staged reverse, exactly as beginClose: X out, then geometry, then
            // the seek item's own glyph back in during the final part of the
            // return -- see watchThenFade.
            state.animations.of(itemId).backIconAlpha.animateTo(0f, m.iconMorph)
            val expansion = state.animations.of(itemId).expansion
            val startExpansion = expansion.value
            val returnJob = state.scope.launch { expansion.animateTo(0f, m.close) }
            watchThenFade(expansion, startExpansion, 0f, state.animations.of(itemId).iconAlpha, 1f, m.iconMorph)
            returnJob.join()
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
        val childSpans = submenuChildSpans(r, d, parentSpan, children)
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

    /**
     * True when [position] falls inside the `coreOverlay` slot's circular
     * footprint *and* that content is actually the interactive surface right
     * now (top-level, settled -- exactly [OneRingState.mode] Idle/Tracking,
     * the only modes `coreOverlay` content is visible in; see
     * docs/notifications.md). Submenu/seek bands can legitimately reach this
     * far in toward the center (e.g. `submenuInnerPx` sits below the default
     * `coreOverlayRadius`), so this must never gate those modes -- only the
     * empty core that has nothing else to hit-test against.
     */
    /** Test hook for [isInsideActiveCoreOverlay], otherwise only reachable through a pointer gesture. */
    internal fun isInsideActiveCoreOverlayForTest(position: Offset): Boolean = isInsideActiveCoreOverlay(position)

    private fun isInsideActiveCoreOverlay(position: Offset): Boolean {
        if (!hasCoreOverlay) return false
        if (state.phase != OneRingState.Phase.Settled) return false
        if (state.path.isNotEmpty() || state.seekSession != null) return false
        val r = resolved ?: return false
        val dx = position.x - r.centerPx.x
        val dy = position.y - r.centerPx.y
        return hypot(dx, dy) < r.coreOverlayRadiusPx
    }

    suspend fun handleGestures(scope: PointerInputScope) = with(scope) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // A down that lands inside an active coreOverlay's circle belongs
            // entirely to that content (e.g. a notification feed's scroll/swipe)
            // -- the ring must not consume it, or Foundation's scrollable/drag
            // detectors underneath would see it as already claimed and back off.
            // See RingInteractionController class doc / docs/notifications.md.
            if (isInsideActiveCoreOverlay(down.position)) return@awaitEachGesture

            down.consume()

            // Input is gated to nothing but a fresh down while a transition is
            // in flight -- see docs/PLAN.md section 6.3.
            if (state.phase != OneRingState.Phase.Settled) return@awaitEachGesture

            val session = state.seekSession
            if (session != null) {
                // A press that starts on the back control tracks it as an
                // ordinary wedge (highlight, scrub, release-to-close) instead of
                // starting a seek drag. Anywhere else on the ring is the drag.
                if (hitBackControl(down.position) != null) {
                    trackGesture(down) { pos -> hitBackControl(pos) }
                    return@awaitEachGesture
                }
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
            val childSpans = submenuChildSpans(r, d, parentSpan, children)
            // One hit function covering both bands, so scrubbing between the
            // back control and a submenu child moves the highlight naturally and
            // the commit target is decided purely by where the finger lifts.
            trackGesture(down) { pos ->
                hitBackControl(pos) ?: hitTestSubmenu(pos, r, childSpans)?.let { children[it].id }
            }
        }
    }
}
