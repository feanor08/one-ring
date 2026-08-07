package com.feanor08.onering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.feanor08.onering.internal.RingAnimations
import kotlinx.coroutines.CoroutineScope

/**
 * The ring's current interaction mode. Grows as later phases add real
 * behavior for each state (Seek); kept to what's actually implemented so far.
 */
public enum class OneRingMode {
    Idle,
    Tracking,
    Transitioning,
    Submenu,
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

    public val mode: OneRingMode
        get() = when {
            phase != Phase.Settled -> OneRingMode.Transitioning
            path.isNotEmpty() -> OneRingMode.Submenu
            touching -> OneRingMode.Tracking
            else -> OneRingMode.Idle
        }

    internal val animations: RingAnimations = RingAnimations()
    internal lateinit var scope: CoroutineScope

    /** Input is gated to nothing but a fresh down while any phase other than [Settled] is active. */
    internal sealed interface Phase {
        object Settled : Phase
        data class Opening(val parentId: String) : Phase
        data class Closing(val parentId: String) : Phase
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
