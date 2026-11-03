package com.feanor08.onering

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.feanor08.onering.geometry.SeekTrack
import com.feanor08.onering.internal.RingAnimations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Job

/** The ring's current interaction mode. */
public enum class OneRingMode {
    Idle,
    Tracking,
    Transitioning,
    Submenu,
    Seek,
}

/**
 * Hoistable navigation/interaction state for a ring. Owns the per-item
 * [RingAnimations] registry, so animations survive every mode change --
 * see docs/PLAN.md section 10. Only [path] is preserved across process death
 * ([rememberOneRingState]'s [Saver]); mid-animation values are never restored,
 * since restoring into a half-finished transition would be meaningless.
 */
@Stable
public class OneRingState internal constructor(initialPath: List<String>) {
    public var path: List<String> by mutableStateOf(initialPath)
        internal set

    public var highlightedId: String? by mutableStateOf(null)
        internal set

    internal var touching: Boolean by mutableStateOf(false)
    internal var phase: Phase by mutableStateOf(Phase.Settled)
    internal var seekSession: SeekSession? by mutableStateOf(null)

    public val mode: OneRingMode
        get() = when {
            phase != Phase.Settled -> OneRingMode.Transitioning
            seekSession != null -> OneRingMode.Seek
            path.isNotEmpty() -> OneRingMode.Submenu
            touching -> OneRingMode.Tracking
            else -> OneRingMode.Idle
        }

    internal val animations: RingAnimations = RingAnimations()

    /**
     * A single shared reveal progress (0..1) for the currently open submenu:
     * children start as a thin strip pinned at the submenu's outer radius and
     * expand inward as this rises, exactly matching the reference prototype
     * (see docs/interaction-spec.md). This is one shared value, not per-item,
     * because it's one continuous choreography affecting every child at once.
     */
    internal val submenuReveal: Animatable<Float, *> = Animatable(0f)

    /**
     * A single shared reveal progress (0..1) for the main ring's *non-active*
     * wedges -- every item except whichever one currently owns an open submenu
     * or an active main-ring seek. 1 = full main ring (the resting state);
     * opening a submenu animates this toward 0 (siblings collapse to zero
     * radial thickness at the inner edge, the mirror of the submenu's own
     * outer-to-inner growth), and closing animates it back to 1 (siblings grow
     * back outward from the inner edge). The active item's own radius is never
     * driven by this -- it has its own continuous [ItemAnimation.expansion].
     */
    internal val mainSiblingReveal: Animatable<Float, *> = Animatable(1f)

    internal lateinit var scope: CoroutineScope

    internal sealed interface Request {
        data class Commit(val itemId: String) : Request
        object Close : Request
        data class Highlight(val itemId: String?) : Request
    }

    internal val requests = MutableSharedFlow<Request>(extraBufferCapacity = 16)

    /**
     * Commits [itemId] as if it had been highlighted and released (opens a menu, runs an action, toggles). For a
     * hold-to-commit action it commits at once: a switch or a voice command cannot hold. Ignored mid-transition.
     */
    public fun commit(itemId: String) {
        requests.tryEmit(Request.Commit(itemId))
    }

    /** Closes the open submenu or seek, as the close control would. Ignored when nothing is open or mid-transition. */
    public fun close() {
        requests.tryEmit(Request.Close)
    }

    /**
     * Highlights [itemId] (null clears it), as a pointer resting on it would; for a switch-driven selection. Ignored while a
     * finger is on the ring or mid-transition. Highlighting a hold-to-commit action starts its hold.
     */
    public fun highlight(itemId: String?) {
        requests.tryEmit(Request.Highlight(itemId))
    }

    /** Input is gated to nothing but a fresh down while any phase other than [Settled] is active. */
    internal sealed interface Phase {
        object Settled : Phase
        data class Opening(val parentId: String) : Phase
        data class Closing(val parentId: String) : Phase
    }

    /**
     * The currently active seek control. [displayValue] (0..1, track-fraction
     * space) is what's actually drawn -- separate from the controlled
     * [com.feanor08.onering.OneRingItem.Seek.value] so the intro hint can
     * animate without ever calling `onValueChange`. [hintJob] is cancelled the
     * moment a drag starts.
     */
    internal class SeekSession(
        val itemId: String,
        val track: SeekTrack,
        val displayValue: Animatable<Float, *> = Animatable(0f),
    ) {
        var hintJob: Job? = null
        var dragging: Boolean = false
    }

    public companion object {
        internal val Saver: Saver<OneRingState, List<String>> = Saver(
            save = { it.path },
            restore = { OneRingState(it) },
        )
    }
}

/** Creates and remembers a [OneRingState], restoring [path][OneRingState.path] across configuration change. */
@Composable
public fun rememberOneRingState(initialPath: List<String> = emptyList()): OneRingState {
    val state = rememberSaveable(saver = OneRingState.Saver) { OneRingState(initialPath) }
    state.scope = rememberCoroutineScope()
    return state
}
