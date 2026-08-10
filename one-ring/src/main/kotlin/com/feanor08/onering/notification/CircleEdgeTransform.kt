package com.feanor08.onering.notification

/** The `graphicsLayer` values a feed item should render with at a given distance from the viewport center. */
internal data class CircleEdgeTransform(val scaleX: Float, val alpha: Float)

/**
 * [distanceFraction] is the normalized vertical distance (0 = viewport
 * center, 1 = viewport edge) of a card's own center from the circular
 * viewport's vertical center. As it approaches the circular edge, a card's
 * `scaleX` and `alpha` fall off by up to [OneRingNotificationDimensions.edgeWidthReductionFraction] /
 * [OneRingNotificationDimensions.edgeAlphaReductionFraction] -- narrowing and
 * dimming cards that are scrolling toward the top/bottom of the circle,
 * matching the reference prototype's proportions (see docs/notifications.md).
 *
 * Pure so it's unit-testable without Compose; the composable side (see
 * `NotificationFeedList`) only ever reads this from inside a deferred
 * `graphicsLayer { }` block, never triggering recomposition or a layout pass
 * -- this is what keeps scrolling smooth (see that file's class doc).
 */
internal fun circleEdgeTransform(
    distanceFraction: Float,
    dimensions: OneRingNotificationDimensions,
): CircleEdgeTransform {
    val clamped = distanceFraction.coerceIn(0f, 1f)
    return CircleEdgeTransform(
        scaleX = 1f - dimensions.edgeWidthReductionFraction * clamped,
        alpha = 1f - dimensions.edgeAlphaReductionFraction * clamped,
    )
}
