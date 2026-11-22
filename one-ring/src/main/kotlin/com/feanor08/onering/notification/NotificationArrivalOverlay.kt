package com.feanor08.onering.notification

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 *
 * The sequence is strictly splash-then-message: [onReveal] fires once, right
 * after the icon-only hold ends and *before* [OneRingNotificationMotion.arrivalExit]
 * starts -- i.e. while this overlay is still fully opaque -- which is the
 * caller's cue that it's now safe to swap the feed underneath to the new/
 * updated content. Only once that content is safely hidden behind the still-
 * opaque splash does the exit fade begin, and only once the exit fade
 * finishes does [onFinished] fire (removing this overlay and exposing what
 * [onReveal] already swapped in). A caller that read live data directly
 * instead of waiting for [onReveal] would let the new card flash into view
 * before the splash ever started -- see [OneRingNotificationHost]'s class doc.
 */
@Composable
internal fun NotificationArrivalOverlay(
    notification: OneRingNotification,
    style: OneRingNotificationStyle,
    presentation: OneRingNotificationPresentation = OneRingNotificationPresentation.Circular,
    animationsEnabled: Boolean,
    onReveal: () -> Unit,
    onFinished: () -> Unit,
) {
    val growProgress = remember(notification.threadKey, notification.sequence) { Animatable(0f) }
    val iconProgress = remember(notification.threadKey, notification.sequence) { Animatable(0f) }
    val exitAlpha = remember(notification.threadKey, notification.sequence) { Animatable(1f) }

    LaunchedEffect(notification.threadKey, notification.sequence, animationsEnabled) {
        if (!animationsEnabled) {
            // Reduced motion: skip the geometric arrival entirely, reveal and
            // hand back to the feed immediately -- there's no opaque cover to
            // hide the reveal behind, so there's nothing "strictly before" to
            // enforce here.
            onReveal()
            onFinished()
            return@LaunchedEffect
        }
        launch { growProgress.animateTo(1f, style.motion.arrivalGrow) }
        launch {
            delay(style.motion.arrivalIconDelayMillis)
            iconProgress.animateTo(1f, style.motion.arrivalIconFade)
        }
        delay(style.motion.arrivalHoldUntilMillis)
        onReveal()
        exitAlpha.animateTo(0f, style.motion.arrivalExit)
        onFinished()
    }

    val arrivalIcon = notification.sourceIcon.takeIf { it != OneRingIcon.None } ?: notification.icon

    val rect = presentation as? OneRingNotificationPresentation.Rectangular
    // Circular: a circle that grows from a point. Rectangular: a translucent slab, as big as the feed, that slides down from above it (the same grow
    // progress, so the same timing); it is translucent, but what shows through is the feed as it was before the arrival, never the new card.
    val shape = if (rect == null) CircleShape else RoundedCornerShape(style.dimensions.cornerRadius)
    val arrivalModifier = if (rect == null) {
        Modifier.fillMaxSize().graphicsLayer {
            alpha = exitAlpha.value
            scaleX = growProgress.value
            scaleY = growProgress.value
        }
            .clip(shape)
            .background(style.colors.arrivalBackground)
            .border(BorderStroke(5.dp, notification.accentColor), shape)
    } else {
        // The slab is exactly a card (its width and its collapsed height), at the top of the feed: the arrival is never bigger than the messages it announces.
        Modifier.width(rect.cardWidth).height(rect.cardMaxCollapsedHeight).graphicsLayer {
            alpha = exitAlpha.value
            translationY = -rect.arrivalTravel.toPx() * (1f - growProgress.value)
        }
            .clip(shape)
            .background(style.colors.arrivalBackground.copy(alpha = rect.arrivalAlpha))
            .border(BorderStroke(style.dimensions.borderWidth, notification.accentColor), shape)
    }

    val iconSize = if (rect == null) ArrivalIconSize else rect.cardMaxCollapsedHeight - 12.dp
    val slab: @Composable () -> Unit = {
        Box(
            modifier = arrivalModifier
                .clearAndSetSemantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "New notification: ${notification.title}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = iconProgress.value
                        val scale = 0.7f + 0.3f * iconProgress.value
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                RenderIcon(icon = arrivalIcon, tint = notification.accentColor, size = iconSize)
            }
        }
    }
    if (rect == null) slab() else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { slab() }
}
