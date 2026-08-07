package com.feanor08.onering.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.polarOffset

private val TargetSize: Dp = 48.dp

/**
 * Invisible, real semantics nodes over the main ring so TalkBack (or any
 * accessibility service) can operate [com.feanor08.onering.OneRing] without a
 * single scrub gesture -- explore-by-touch consumes pointer events, so this is
 * a second, complete input path, not decoration. There's no icon/content
 * layer yet (see docs/PLAN.md), so each node is a fixed-size touch target
 * centered on its wedge's midpoint rather than shaped to the wedge itself;
 * that's enough for correct focus order, description, state, and activation.
 */
@Composable
internal fun RingSemanticsLayer(
    items: List<OneRingItem>,
    spans: List<AngularSpan>,
    innerPx: Float,
    outerPx: Float,
    centerPx: Offset,
    highlightedId: String?,
    onActivate: (String) -> Unit,
) {
    val density = LocalDensity.current
    val midRadius = (innerPx + outerPx) / 2f

    items.forEachIndexed { index, item ->
        val span = spans.getOrNull(index) ?: return@forEachIndexed
        val point = polarOffset(midRadius, span.midDeg)
        val topLeftPx = Offset(
            centerPx.x + point.x - with(density) { TargetSize.toPx() } / 2f,
            centerPx.y + point.y - with(density) { TargetSize.toPx() } / 2f,
        )
        val topLeftDp = with(density) { androidx.compose.ui.unit.DpOffset(topLeftPx.x.toDp(), topLeftPx.y.toDp()) }

        Box(
            modifier = Modifier
                .offset(topLeftDp.x, topLeftDp.y)
                .size(TargetSize)
                .semantics(mergeDescendants = true) {
                    contentDescription = item.contentDescription ?: item.label
                    role = Role.Button
                    traversalIndex = index.toFloat()
                    if (!item.enabled) disabled()
                    if (item.id == highlightedId) selected = true
                    onClick(label = item.label) {
                        onActivate(item.id)
                        true
                    }
                },
        )
    }
}
