package com.feanor08.onering.notification

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feanor08.onering.internal.RenderIcon
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val CardIconSize = 22.dp
private val DismissTargetSize = 32.dp
private const val SWIPE_DISMISS_THRESHOLD_FRACTION = 0.32f
private const val SWIPE_OVERSHOOT_FRACTION = 1.15f

/**
 * One notification thread's card: title, source/count subtitle, every
 * accumulated message, an X at the top-right, and swipe-left-to-dismiss.
 * Rendered inside `NotificationFeedList`'s single `LazyColumn` -- there is no
 * scroll container here, so a card taller than the viewport simply extends
 * past it and is read by continuing to scroll the outer feed (see that
 * file's class doc).
 */
@Composable
internal fun NotificationCard(
    notification: OneRingNotification,
    style: OneRingNotificationStyle,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(notification.threadKey) { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    val subtitle = notification.subtitle ?: run {
        val count = notification.messages.size
        if (count > 1) "${notification.sourceLabel} · $count messages" else notification.sourceLabel
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .graphicsLayer { translationX = offsetX.value }
            .pointerInput(notification.threadKey) {
                swipeToDismissGesture(
                    offsetX = offsetX,
                    widthPxProvider = { widthPx },
                    style = style,
                    scope = scope,
                    onDismiss = onDismiss,
                )
            }
            .background(style.colors.cardBackground, RoundedCornerShape(style.dimensions.cornerRadius))
            .border(
                BorderStroke(style.dimensions.borderWidth, notification.accentColor),
                RoundedCornerShape(style.dimensions.cornerRadius),
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(notification.title)
                    append(". ")
                    append(subtitle)
                    append(". ")
                    append(notification.messages.joinToString(" "))
                }
                customActions = listOf(
                    CustomAccessibilityAction(label = "Dismiss ${notification.title} notification") {
                        onDismiss()
                        true
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    RenderIcon(icon = notification.icon, tint = notification.accentColor, size = CardIconSize)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = notification.title,
                            style = TextStyle(color = style.colors.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BasicText(
                            text = subtitle,
                            style = TextStyle(color = notification.accentColor, fontSize = 12.sp),
                        )
                    }
                }
                DismissButton(
                    tint = notification.accentColor,
                    title = notification.title,
                    onDismiss = onDismiss,
                )
            }
            Spacer(modifier = Modifier.size(6.dp))
            Column {
                notification.messages.forEach { message ->
                    BasicText(
                        text = message,
                        style = TextStyle(color = style.colors.body, fontSize = 13.sp, lineHeight = 17.sp),
                        modifier = Modifier.padding(vertical = 1.5.dp),
                    )
                }
            }
            notification.timestampLabel?.let { timestamp ->
                Spacer(modifier = Modifier.size(4.dp))
                BasicText(text = timestamp, style = TextStyle(color = style.colors.secondaryText, fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun DismissButton(tint: Color, title: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DismissTargetSize)
            .clearAndSetSemantics {
                contentDescription = "Dismiss $title notification"
                role = Role.Button
                onClick(label = "Dismiss") {
                    onDismiss()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.Center,
    ) {
        DismissGlyph(tint = tint)
    }
}

@Composable
private fun DismissGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = size.minDimension * 0.14f
        drawLine(
            color = tint,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Swipe-left-to-dismiss with a directional lock against the enclosing
 * `LazyColumn`'s vertical scroll: horizontal movement is only claimed once it
 * clearly wins over vertical movement (see the threshold below), matching
 * [com.feanor08.onering.internal.RingInteractionController]'s
 * `awaitEachGesture`-based approach rather than Foundation's swipe-to-dismiss
 * (which would pull in a Material dependency this module deliberately
 * doesn't have -- see docs/notifications.md).
 */
private suspend fun PointerInputScope.swipeToDismissGesture(
    offsetX: Animatable<Float, AnimationVector1D>,
    widthPxProvider: () -> Float,
    style: OneRingNotificationStyle,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var locked = false
        var settledOut = false
        var totalDx = 0f
        var totalDy = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (locked) {
                    val widthPx = widthPxProvider().takeIf { it > 0f } ?: 1f
                    val dismissThresholdPx = widthPx * SWIPE_DISMISS_THRESHOLD_FRACTION
                    if (offsetX.value < -dismissThresholdPx) {
                        scope.launch {
                            offsetX.animateTo(-widthPx * SWIPE_OVERSHOOT_FRACTION, style.motion.swipeDismiss)
                            onDismiss()
                        }
                    } else {
                        scope.launch { offsetX.animateTo(0f, style.motion.swipeSettle) }
                    }
                }
                break
            }
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            if (!locked && !settledOut) {
                totalDx += dx
                totalDy += dy
                when {
                    abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * SWIPE_OVERSHOOT_FRACTION && totalDx < 0 -> {
                        locked = true
                    }
                    abs(totalDy) > touchSlop && abs(totalDy) >= abs(totalDx) -> {
                        // Vertical movement won -- hand the gesture back to the
                        // enclosing LazyColumn entirely rather than consuming it.
                        settledOut = true
                        break
                    }
                }
            } else if (locked) {
                scope.launch { offsetX.snapTo((offsetX.value + dx).coerceAtMost(0f)) }
                change.consume()
            }
        }
    }
}
