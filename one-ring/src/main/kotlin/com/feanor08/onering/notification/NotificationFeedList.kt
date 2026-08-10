package com.feanor08.onering.notification

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feanor08.onering.internal.RenderIcon
import kotlin.math.abs

private val SourceHeaderIconSize = 16.dp

/**
 * The entire notification feed as ONE scrollable [LazyColumn] -- source
 * headers and cards are both items in the same list, so scrolling continues
 * naturally from a card into the next source group rather than stopping at
 * per-card boundaries (see docs/notifications.md; this is why no card gets
 * its own internal scroll container).
 *
 * The circular-edge narrowing effect ([circleEdgeTransform]) is applied via a
 * *deferred-read* `graphicsLayer { }` block on each item, reading
 * [LazyListState.layoutInfo] directly inside the layer block rather than in a
 * `@Composable` scope. That's what keeps scrolling smooth: a deferred
 * `graphicsLayer` block re-evaluates on every frame the list state changes
 * without triggering recomposition or another layout pass -- unlike, say,
 * `onGloballyPositioned`-driven state mutation, which would cause exactly the
 * kind of layout feedback loop this design deliberately avoids.
 */
@Composable
internal fun NotificationFeedList(
    groups: List<OneRingNotificationGroup>,
    style: OneRingNotificationStyle,
    coreOverlayDiameterPx: Float,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    val maxCardWidthDp = with(density) { (coreOverlayDiameterPx * style.dimensions.maxCardWidthFraction).toDp() }
    val minCardHeightDp = with(density) { (coreOverlayDiameterPx * style.dimensions.minCardHeightFraction).toDp() }
    val topPaddingDp = with(density) { (coreOverlayDiameterPx * style.dimensions.topContentPaddingFraction).toDp() }
    val bottomPaddingDp = with(density) { (coreOverlayDiameterPx * style.dimensions.bottomContentPaddingFraction).toDp() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().clip(CircleShape),
        contentPadding = PaddingValues(top = topPaddingDp, bottom = bottomPaddingDp),
    ) {
        groups.forEachIndexed { groupIndex, group ->
            val headerKey = "header:${group.sourceKey}"
            item(key = headerKey) {
                SourceHeader(
                    group = group,
                    style = style,
                    modifier = Modifier
                        .width(maxCardWidthDp)
                        .circleEdgeGraphicsLayer(listState, headerKey, style.dimensions)
                        .padding(
                            top = if (groupIndex == 0) 0.dp else style.dimensions.groupSpacing,
                            bottom = 8.dp,
                        ),
                )
            }
            group.threads.forEachIndexed { threadIndex, thread ->
                item(key = thread.threadKey) {
                    NotificationCard(
                        notification = thread,
                        style = style,
                        onDismiss = { onDismiss(thread.threadKey) },
                        modifier = Modifier
                            .width(maxCardWidthDp)
                            .heightIn(min = minCardHeightDp)
                            .circleEdgeGraphicsLayer(listState, thread.threadKey, style.dimensions)
                            .padding(
                                bottom = if (threadIndex == group.threads.lastIndex) 0.dp else style.dimensions.cardSpacing,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(group: OneRingNotificationGroup, style: OneRingNotificationStyle, modifier: Modifier = Modifier) {
    val threadCount = group.threads.size
    val countLabel = if (threadCount == 1) "1 active" else "$threadCount active"
    Row(
        modifier = modifier
            .clearAndSetSemantics {
                heading()
                contentDescription = "${group.sourceLabel}, $countLabel"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RenderIcon(icon = group.sourceIcon, tint = style.colors.secondaryText, size = SourceHeaderIconSize)
        Spacer(modifier = Modifier.width(6.dp))
        BasicText(
            text = group.sourceLabel,
            style = TextStyle(color = style.colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicText(text = countLabel, style = TextStyle(color = style.colors.secondaryText, fontSize = 11.sp))
    }
}

/**
 * Applies [circleEdgeTransform] as a deferred `graphicsLayer` read of
 * [listState]'s current layout for the item identified by [key] -- see this
 * file's class doc for why this specific mechanism (rather than a
 * recomposition-driven one) is what keeps scrolling smooth.
 */
private fun Modifier.circleEdgeGraphicsLayer(
    listState: LazyListState,
    key: Any,
    dimensions: OneRingNotificationDimensions,
): Modifier = this.graphicsLayer {
    val layoutInfo = listState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (itemInfo != null && viewportHeight > 0f) {
        val viewportCenter = viewportHeight / 2f
        val itemCenter = itemInfo.offset + itemInfo.size / 2f
        val distanceFraction = abs(itemCenter - viewportCenter) / viewportCenter
        val transform = circleEdgeTransform(distanceFraction, dimensions)
        scaleX = transform.scaleX
        alpha = transform.alpha
    }
}
