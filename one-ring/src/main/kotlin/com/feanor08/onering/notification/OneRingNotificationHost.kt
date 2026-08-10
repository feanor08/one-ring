package com.feanor08.onering.notification

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.feanor08.onering.OneRingCenterScope
import com.feanor08.onering.internal.rememberAnimationsEnabled

/**
 * A reusable notification feed host intended for [com.feanor08.onering.OneRing]'s
 * `coreOverlay` slot:
 *
 * ```
 * OneRing(
 *     items = items,
 *     state = ringState,
 *     onAction = { ... },
 *     coreOverlay = { OneRingNotificationHost(state = notifications) },
 * )
 * ```
 *
 * Automatically reacts to the ring's [com.feanor08.onering.OneRingMode] (via
 * this [OneRingCenterScope] receiver) -- visible while [com.feanor08.onering.OneRingMode.Idle]
 * /[com.feanor08.onering.OneRingMode.Tracking], hidden the moment a submenu or
 * seek opens, without every consumer having to reproduce that logic
 * themselves. See docs/notifications.md for the full behavior.
 *
 * For testing (or using the host outside a ring entirely), see the overload
 * taking an explicit [visible] flag.
 */
@Composable
public fun OneRingCenterScope.OneRingNotificationHost(
    state: OneRingNotificationState,
    modifier: Modifier = Modifier,
    style: OneRingNotificationStyle = OneRingNotificationDefaults.style(),
) {
    OneRingNotificationHost(
        state = state,
        visible = notificationsVisible(mode),
        modifier = modifier,
        style = style,
    )
}

/**
 * The non-extension form of [OneRingNotificationHost], for use outside a
 * [com.feanor08.onering.OneRing] (or in tests) where there is no
 * [OneRingCenterScope] to derive [visible] from automatically.
 */
@Composable
public fun OneRingNotificationHost(
    state: OneRingNotificationState,
    visible: Boolean,
    modifier: Modifier = Modifier,
    style: OneRingNotificationStyle = OneRingNotificationDefaults.style(),
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val feedAlpha = remember { Animatable(if (visible) 1f else 0f) }
    var lastAnnouncedSequence by remember { mutableLongStateOf(0L) }
    var activeArrival by remember { mutableStateOf<OneRingNotification?>(null) }

    LaunchedEffect(visible, animationsEnabled) {
        val target = if (visible) 1f else 0f
        if (animationsEnabled) feedAlpha.animateTo(target, style.motion.visibilityFade) else feedAlpha.snapTo(target)
    }

    // An in-flight arrival belongs entirely to the moment it was visible in --
    // if the ring transitions away (a submenu opens) mid-arrival, drop it
    // rather than leaving an opaque circle stranded over a hidden feed.
    LaunchedEffect(visible) {
        if (!visible) activeArrival = null
    }

    LaunchedEffect(state.latestSequence, visible) {
        val latest = state.latestSequence
        if (shouldPlayArrival(visible, latest, lastAnnouncedSequence)) {
            activeArrival = state.notifications.firstOrNull { it.sequence == latest }
        } else {
            lastAnnouncedSequence = advancedWatermark(latest)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val diameterPx = minOf(sizePx.width, sizePx.height)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = feedAlpha.value }
                .semantics { if (!visible) invisibleToUser() },
        ) {
            NotificationFeedList(
                groups = state.groups,
                style = style,
                coreOverlayDiameterPx = diameterPx,
                onDismiss = { threadKey -> state.dismiss(threadKey) },
            )
        }

        activeArrival?.let { notification ->
            NotificationArrivalOverlay(
                notification = notification,
                style = style,
                animationsEnabled = animationsEnabled,
                onFinished = {
                    lastAnnouncedSequence = advancedWatermark(notification.sequence)
                    activeArrival = null
                },
            )
        }
    }
}
