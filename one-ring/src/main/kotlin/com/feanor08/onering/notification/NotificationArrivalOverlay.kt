package com.feanor08.onering.notification

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.internal.RenderIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ArrivalIconSize = 56.dp

/**
 * The opaque, growing "new notification" circle -- see docs/notifications.md's
 * "incoming icon-first animation" section. [notification] identifies which
 * post to animate for; a new (threadKey, sequence) pair restarts the whole
 * sequence from scratch (`remember` keyed on both), matching the "latest-wins
 * / cancel-and-restart" allowance for rapid posts (the *data* itself never
 * drops a message either way -- see [NotificationFeed]).
 *
 * CRITICAL invariant: this overlay's own alpha stays fully opaque (1f) for
 * the entire grow-hold sequence -- only [OneRingNotificationMotion.arrivalExit]
 * ever reduces it. That's structural, not tuned: nothing else in this
 * composable ever touches the outer alpha before the exit phase, so the feed
 * underneath cannot show through while the arrival icon is on screen (the
 * bug this was explicitly built to avoid -- see docs/notifications.md).
 */
@Composable
internal fun NotificationArrivalOverlay(
    notification: OneRingNotification,
    style: OneRingNotificationStyle,
    animationsEnabled: Boolean,
    onFinished: () -> Unit,
) {
    val growProgress = remember(notification.threadKey, notification.sequence) { Animatable(0f) }
    val iconProgress = remember(notification.threadKey, notification.sequence) { Animatable(0f) }
    val exitAlpha = remember(notification.threadKey, notification.sequence) { Animatable(1f) }

    LaunchedEffect(notification.threadKey, notification.sequence, animationsEnabled) {
        if (!animationsEnabled) {
            // Reduced motion: skip the geometric arrival entirely, snap straight
            // to fully revealed, and hand back to the feed immediately.
            onFinished()
            return@LaunchedEffect
        }
        launch { growProgress.animateTo(1f, style.motion.arrivalGrow) }
        launch {
            delay(style.motion.arrivalIconDelayMillis)
            iconProgress.animateTo(1f, style.motion.arrivalIconFade)
        }
        delay(style.motion.arrivalHoldUntilMillis)
        exitAlpha.animateTo(0f, style.motion.arrivalExit)
        onFinished()
    }

    val arrivalIcon = notification.sourceIcon.takeIf { it != OneRingIcon.None } ?: notification.icon

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = growProgress.value
                scaleY = growProgress.value
            }
            .clip(CircleShape)
            .background(style.colors.arrivalBackground)
            .border(BorderStroke(1.5.dp, notification.accentColor), CircleShape)
            .clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "New notification: ${notification.title}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ArrivalIconSize)
                .graphicsLayer {
                    alpha = iconProgress.value
                    val scale = 0.7f + 0.3f * iconProgress.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            RenderIcon(icon = arrivalIcon, tint = notification.accentColor, size = ArrivalIconSize)
        }
    }
}
