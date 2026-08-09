package com.feanor08.onering.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.polarOffset
import com.feanor08.onering.style.OneRingColors

private val MaxIconSize: Dp = 32.dp
private val MinIconSize: Dp = 16.dp

/**
 * Draws each wedge's [OneRingIcon] as a real Compose child node, centered on
 * the wedge's midpoint at whatever radius its expansion animation currently
 * puts it at. This is a separate pass from the arcs (which are pure Canvas
 * drawing) because [OneRingIcon.Content] is arbitrary consumer composable
 * content and can't be rendered into a DrawScope.
 *
 * Semantics are cleared here: [RingSemanticsLayer] already publishes one merged
 * node per wedge, and a second describable node per icon would make TalkBack
 * announce every item twice.
 */
@Composable
internal fun RingContentLayer(
    items: List<OneRingItem>,
    spans: List<AngularSpan>,
    innerPx: Float,
    centerPx: Offset,
    colors: OneRingColors,
    highlightedId: String?,
    outerPxOf: (String) -> Float,
    alphaOf: (String) -> Float,
    scale: Float = 1f,
    iconAlphaOf: (String) -> Float = { 1f },
    backIconAlphaOf: (String) -> Float = { 0f },
) {
    val density = LocalDensity.current

    items.forEachIndexed { index, item ->
        val span = spans.getOrNull(index) ?: return@forEachIndexed
        val itemAlpha = alphaOf(item.id)
        if (itemAlpha <= 0.01f) return@forEachIndexed

        val outerPx = outerPxOf(item.id)
        val midRadius = (innerPx + outerPx) / 2f
        val bandThicknessPx = outerPx - innerPx
        // Cap by both the band's thickness and the wedge's arc width, so icons
        // never overflow their own sector when there are many items.
        val arcWidthPx = midRadius * Math.toRadians(span.sweepDeg.toDouble()).toFloat()
        val sizePx = minOf(bandThicknessPx, arcWidthPx) * 0.55f
        val iconSize = with(density) { sizePx.toDp() }.coerceIn(MinIconSize, MaxIconSize)

        val point = polarOffset(midRadius, span.midDeg)
        val halfPx = with(density) { iconSize.toPx() } / 2f
        val topLeftDp = with(density) {
            androidx.compose.ui.unit.DpOffset(
                (centerPx.x + point.x - halfPx).toDp(),
                (centerPx.y + point.y - halfPx).toDp(),
            )
        }

        val tint = when {
            !item.enabled -> colors.contentDisabled
            item.id == highlightedId -> colors.contentHighlighted
            else -> colors.content
        }

        // The item's own glyph and its close (X) glyph are two independently
        // faded layers occupying the same spot, never a crossfade between one
        // slot's contents: the controller staggers their fades so only one is
        // ever meaningfully visible. Drawing them as separate layers is what
        // makes that staging expressible at all.
        val iconAlpha = itemAlpha * iconAlphaOf(item.id)
        val backAlpha = itemAlpha * backIconAlphaOf(item.id)

        if (iconAlpha > 0.01f) {
            GlyphLayer(topLeftDp, iconSize, iconAlpha, scale) {
                RenderIcon(icon = item.icon, tint = tint)
            }
        }
        if (backAlpha > 0.01f) {
            val closeIcon = when (item) {
                is OneRingItem.Menu -> item.closeIcon
                is OneRingItem.Seek -> item.closeIcon
                else -> null
            }
            GlyphLayer(topLeftDp, iconSize, backAlpha, scale) {
                if (closeIcon != null) RenderIcon(icon = closeIcon, tint = tint) else CloseGlyph(tint = tint)
            }
        }
    }
}

@Composable
private fun GlyphLayer(
    topLeftDp: androidx.compose.ui.unit.DpOffset,
    iconSize: Dp,
    alpha: Float,
    scale: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(topLeftDp.x, topLeftDp.y)
            .size(iconSize)
            .alpha(alpha)
            .scale(scale)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RenderIcon(icon: OneRingIcon, tint: Color) {
    when (icon) {
        is OneRingIcon.None -> Unit
        is OneRingIcon.Vector -> Image(
            painter = rememberVectorPainter(icon.imageVector),
            contentDescription = null,
            modifier = Modifier.size(MaxIconSize),
            colorFilter = ColorFilter.tint(icon.tint ?: tint),
        )
        is OneRingIcon.Painted -> Image(
            painter = icon.painter,
            contentDescription = null,
            modifier = Modifier.size(MaxIconSize),
            colorFilter = ColorFilter.tint(icon.tint ?: tint),
        )
        is OneRingIcon.Content -> icon.content()
    }
}

/**
 * The X shown on an open menu's / active seek's parent wedge, drawn rather
 * than supplied as an asset so the library keeps its zero-icon-dependency
 * footprint. Consumers who want a different glyph set
 * [OneRingItem.Menu.closeIcon] / [OneRingItem.Seek.closeIcon].
 */
@Composable
private fun CloseGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(MaxIconSize)) {
        val inset = size.minDimension * 0.28f
        val stroke = size.minDimension * 0.10f
        drawLine(
            color = tint,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
