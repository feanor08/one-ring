package com.feanor08.onering

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The specific interaction moments [OneRing] can perform haptic feedback for. */
public enum class OneRingHapticEvent {
    /** The pointer crossed into a new wedge (fired on change only, never continuously). */
    HighlightChanged,

    /** A leaf action or submenu child was committed. */
    Committed,

    /** A disabled item was released on; deliberately distinct from [Committed]. */
    Rejected,

    /** A menu finished opening. */
    MenuOpened,

    /** A menu finished closing. */
    MenuClosed,

    /** A seek control was entered (main-ring or submenu). */
    SeekEntered,

    /** A seek control was exited. */
    SeekExited,
}

/**
 * Performs haptic feedback for a [OneRing] interaction moment. Consumers can
 * supply their own (e.g. an automotive HMI routing haptics through a vehicle
 * service instead of the display) via [OneRing]'s `haptics` parameter, or pass
 * [OneRingHaptics.None] to disable feedback entirely regardless of
 * [com.feanor08.onering.style.OneRingBehavior.hapticsEnabled].
 */
public fun interface OneRingHaptics {
    public fun perform(event: OneRingHapticEvent)

    public companion object {
        public val None: OneRingHaptics = OneRingHaptics {}
    }
}

/** The platform-backed default: [android.view.View.performHapticFeedback], API-level gated. */
@Composable
public fun rememberDefaultOneRingHaptics(): OneRingHaptics {
    val view = LocalView.current
    return remember(view) {
        OneRingHaptics { event ->
            val constant = constantFor(event)
            if (constant != null) view.performHapticFeedback(constant)
        }
    }
}

private fun constantFor(event: OneRingHapticEvent): Int? = when (event) {
    OneRingHapticEvent.HighlightChanged,
    OneRingHapticEvent.MenuOpened,
    OneRingHapticEvent.MenuClosed,
    OneRingHapticEvent.SeekEntered,
    OneRingHapticEvent.SeekExited,
    ->
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    OneRingHapticEvent.Committed ->
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
    OneRingHapticEvent.Rejected ->
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else null
}
