package com.feanor08.onering.notification

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every default value for [OneRingNotificationHost]'s configuration -- the only place its design constants live. */
public object OneRingNotificationDefaults {

    private val EaseOutCubic: CubicBezierEasing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    public fun style(
        colors: OneRingNotificationColors = colors(),
        dimensions: OneRingNotificationDimensions = dimensions(),
        motion: OneRingNotificationMotion = motion(),
    ): OneRingNotificationStyle = OneRingNotificationStyle(colors, dimensions, motion)

    public fun colors(
        cardBackground: Color = Color(0xFF0B0C0C),
        title: Color = Color(0xFFF2F2F2),
        body: Color = Color(0xFFC7C9CC),
        secondaryText: Color = Color(0xFF8A8D91),
        arrivalBackground: Color = Color(0xFF08090A),
    ): OneRingNotificationColors = OneRingNotificationColors(
        cardBackground = cardBackground,
        title = title,
        body = body,
        secondaryText = secondaryText,
        arrivalBackground = arrivalBackground,
    )

    public fun dimensions(
        maxCardWidthFraction: Float = 0.88f,
        edgeWidthReductionFraction: Float = 0.12f,
        edgeAlphaReductionFraction: Float = 0.4f,
        minCardHeightFraction: Float = 0.40f,
        cornerRadius: Dp = 14.dp,
        borderWidth: Dp = 1.5.dp,
        cardSpacing: Dp = 10.dp,
        groupSpacing: Dp = 20.dp,
        horizontalContentPadding: Dp = 16.dp,
        topContentPaddingFraction: Float = 0.25f,
        bottomContentPaddingFraction: Float = 0.30f,
    ): OneRingNotificationDimensions = OneRingNotificationDimensions(
        maxCardWidthFraction = maxCardWidthFraction,
        edgeWidthReductionFraction = edgeWidthReductionFraction,
        edgeAlphaReductionFraction = edgeAlphaReductionFraction,
        minCardHeightFraction = minCardHeightFraction,
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        cardSpacing = cardSpacing,
        groupSpacing = groupSpacing,
        horizontalContentPadding = horizontalContentPadding,
        topContentPaddingFraction = topContentPaddingFraction,
        bottomContentPaddingFraction = bottomContentPaddingFraction,
    )

    public fun motion(
        visibilityFade: AnimationSpec<Float> = tween(140, easing = LinearEasing),
        arrivalGrow: AnimationSpec<Float> = tween(430, easing = EaseOutCubic),
        arrivalGrowDurationMillis: Long = 430L,
        arrivalIconDelayMillis: Long = 160L,
        arrivalIconFade: AnimationSpec<Float> = tween(220, easing = EaseOutCubic),
        arrivalHoldUntilMillis: Long = 730L,
        arrivalExit: AnimationSpec<Float> = tween(340, easing = EaseOutCubic),
        arrivalExitDurationMillis: Long = 340L,
        swipeSettle: AnimationSpec<Float> = tween(220, easing = EaseOutCubic),
        swipeDismiss: AnimationSpec<Float> = tween(180, easing = EaseOutCubic),
    ): OneRingNotificationMotion = OneRingNotificationMotion(
        visibilityFade = visibilityFade,
        arrivalGrow = arrivalGrow,
        arrivalGrowDurationMillis = arrivalGrowDurationMillis,
        arrivalIconDelayMillis = arrivalIconDelayMillis,
        arrivalIconFade = arrivalIconFade,
        arrivalHoldUntilMillis = arrivalHoldUntilMillis,
        arrivalExit = arrivalExit,
        arrivalExitDurationMillis = arrivalExitDurationMillis,
        swipeSettle = swipeSettle,
        swipeDismiss = swipeDismiss,
    )
}
