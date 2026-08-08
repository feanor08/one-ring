package com.feanor08.onering.internal

import android.provider.Settings
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.feanor08.onering.style.OneRingMotion

/** Whether system animations are enabled (the "Remove animations" accessibility setting is off). */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** [motion] unchanged if animations are enabled, otherwise every spec replaced with an instant snap. */
internal fun effectiveMotion(motion: OneRingMotion, animationsEnabled: Boolean): OneRingMotion {
    if (animationsEnabled) return motion
    val instant = snap<Float>()
    return OneRingMotion(
        hover = instant,
        select = instant,
        close = instant,
        submenuReveal = instant,
        iconMorph = instant,
        siblingExit = instant,
        seekHintRise = instant,
        seekHintSettle = instant,
    )
}
