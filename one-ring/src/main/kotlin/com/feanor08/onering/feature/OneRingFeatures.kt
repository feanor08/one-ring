package com.feanor08.onering.feature

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingState
import com.feanor08.onering.internal.RenderIcon
import com.feanor08.onering.internal.RingOutline
import com.feanor08.onering.internal.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Draws the feature badges round a ring. Give it the same bounds as the ring plus the margin the badges need (see
 * [OneRingFeatureDefaults.requiredMargin]), with the ring centred in them, and the [state] the ring was given: the badges read the ring's live outline from
 * it and keep [OneRingFeatureStyle.ringGap] clear of it as wedges grow, shrink, open a submenu or sit out and in.
 *
 * Features that are on sit side by side in a row at the top, the others side by side in a row at the bottom, and never overlap there (see [FeatureLayout]).
 * A badge moves between the rows by riding the ring's edge, always clockwise: up through 9 o'clock, down through 3 o'clock. A badge that has to ride past
 * a resting one goes in front of it, and the resting one moves out from the ring out of its way (see [OneRingFeatureStyle.sidestep]) and comes back once it has
 * gone by. Only a badge the rider actually crosses moves; the others stay put.
 */
@Composable
public fun OneRingFeatures(
    features: List<OneRingFeature>,
    state: OneRingState,
    modifier: Modifier = Modifier,
    style: OneRingFeatureStyle = OneRingFeatureDefaults.style(),
) {
    val outline = state.outline ?: return
    val density = LocalDensity.current
    val animationsEnabled = rememberAnimationsEnabled()
    val order = remember { FeatureRowOrder() }
    val shown = features.filter { it.status != OneRingFeatureStatus.Hidden }
    val badgePx = with(density) { style.badgeSize.toPx() }
    val gapPx = with(density) { style.ringGap.toPx() }
    val spacingPx = with(density) { style.spacing.toPx() }
    val pitch = FeatureLayout.pitchDeg(outline.restOuterPx + gapPx + badgePx / 2f, badgePx, spacingPx)
    val plan = order.update(shown.map { it.id to rowOf(it.status) }, pitch)

    // Every badge's angle lives here, so each can see where the others are riding.
    val angles = remember { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    angles.keys.retainAll(shown.map { it.id }.toSet())
    shown.forEach { angles.getOrPut(it.id) { Animatable(plan.targets[it.id] ?: FeatureLayout.TOP_DEG) } }
    // The trips between rows now under way (where each started and where it ends): only a badge on a rider's way is stepped aside.
    val trips = remember { HashMap<String, FloatArray>() }
    trips.keys.retainAll(shown.map { it.id }.toSet())
    val riders = shown.filter { angles.getValue(it.id).isRunning && trips[it.id] != null }
        .map { it.id to FeatureLayout.Ride(angles.getValue(it.id).value, trips.getValue(it.id)[0], trips.getValue(it.id)[1]) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val centre = with(density) { Offset(maxWidth.toPx() / 2f, maxHeight.toPx() / 2f) }
        // A badge that has to ride over another is drawn last, so it passes in front of it.
        shown.sortedBy { it.id in plan.passing }.forEach { feature ->
            key(feature.id) {
                FeatureBadge(
                    feature, plan.targets[feature.id], angles.getValue(feature.id), trips, riders.filter { it.first != feature.id }.map { it.second },
                    pitch, centre, outline, style, animationsEnabled,
                )
            }
        }
    }
}

@Composable
private fun FeatureBadge(
    feature: OneRingFeature,
    targetDeg: Float?,
    angle: Animatable<Float, AnimationVector1D>,
    trips: MutableMap<String, FloatArray>,
    riders: List<FeatureLayout.Ride>,
    pitchDeg: Float,
    centre: Offset,
    outline: RingOutline,
    style: OneRingFeatureStyle,
    animationsEnabled: Boolean,
) {
    val density = LocalDensity.current
    val status = feature.status
    val active = status as? OneRingFeatureStatus.Active
    val badgePx = with(density) { style.badgeSize.toPx() }
    val gapPx = with(density) { style.ringGap.toPx() }

    val releasing = status as? OneRingFeatureStatus.Releasing
    // True from the moment a badge is on the right-hand arc until it has been brought back to a row.
    var onArc by remember { mutableStateOf(false) }

    // A badge first seen is simply at its place; after that it rides to wherever its place becomes.
    LaunchedEffect(targetDeg, releasing, animationsEnabled) {
        if (releasing != null) {
            // Releasing: down the right-hand side, from where it is (the top) to the bottom, taking the time it was given.
            onArc = true
            if (angle.value > 180f) angle.snapTo(angle.value - 360f)                      // the top-left slot is 351: on the arc that is -9
            trips[feature.id] = floatArrayOf(0f, 180f)                                    // anything resting on the arc steps aside as it passes
            val left = ((180f - angle.value) / 180f).coerceIn(0f, 1f)
            if (animationsEnabled) angle.animateTo(180f, tween((releasing.durationMs * left).toInt().coerceAtLeast(1), easing = LinearEasing)) else angle.snapTo(180f)
            trips.remove(feature.id)
            return@LaunchedEffect
        }
        val target = targetDeg ?: return@LaunchedEffect
        if (onArc) {
            // Off the arc. Landing in the bottom row, or back to the top if the feature came on again mid-release (the one trip that goes counter-clockwise: the
            // way it came). Either way along the arc, the number line, not round the ring.
            onArc = false
            val dest = if (target > 270f) target - 360f else target                       // the top row's left-hand slots (351) are -9 on the arc
            if (animationsEnabled) {
                val ms = (style.travelMs * (abs(dest - angle.value) / 180f)).toInt().coerceIn(MIN_TRAVEL_MS, style.travelMs)
                angle.animateTo(dest, tween(ms, easing = FastOutSlowInEasing))
            } else {
                angle.snapTo(dest)
            }
            angle.snapTo(FeatureLayout.normalize(angle.value))
            return@LaunchedEffect
        }
        val dest = FeatureLayout.destination(angle.value, target)
        if (dest != angle.value) {
            // A trip between rows (not a small shift within one) is what steps other badges aside, and only those on its way.
            if (abs(dest - angle.value) > FeatureLayout.SMALL_SHIFT_DEG) trips[feature.id] = floatArrayOf(angle.value, dest) else trips.remove(feature.id)
            if (animationsEnabled) {
                val ms = (style.travelMs * (abs(dest - angle.value) / 180f)).toInt().coerceIn(MIN_TRAVEL_MS, style.travelMs)
                angle.animateTo(dest, tween(ms, easing = FastOutSlowInEasing))
            } else {
                angle.snapTo(dest)
            }
        }
        trips.remove(feature.id)
        angle.snapTo(FeatureLayout.normalize(angle.value))
    }

    val valueColor = active?.let { feature.colorFor(it.value) } ?: releasing?.let { feature.colorFor(it.value) }
    val fade = tween<Color>(style.colorFadeMs)
    val fill by animateColorAsState(valueColor ?: style.colors.idleFill, fade, label = "fill")
    val rim by animateColorAsState(valueColor ?: style.colors.idleBorder, fade, label = "rim")
    val iconTint by animateColorAsState(if (valueColor != null) style.colors.activeIcon else style.colors.idleIcon, fade, label = "icon")
    val pulseColor by animateColorAsState(valueColor ?: style.colors.idleBorder, fade, label = "pulse")

    // Where it is: out from the centre by the ring's reach at its own angle, plus the gap, plus its own radius.
    val a = angle.value
    // A resting badge in the way of one riding across it moves out from the ring to let it by, and comes back as the rider goes on.
    // (A badge on a trip of its own does not step aside; one that is only recentring in its row, because a neighbour left, does.)
    val aside = if (trips.containsKey(feature.id)) 0f else FeatureLayout.dodge(a, riders, pitchDeg)
    val orbit = outline.radiusAt(FeatureLayout.normalize(a)) + gapPx + badgePx / 2f + aside * with(density) { style.sidestep.toPx() }
    val rad = a * PI.toFloat() / 180f
    val cx = centre.x + orbit * sin(rad)
    val cy = centre.y - orbit * cos(rad)

    // Pulses: a burst once it has arrived, then again every period, for as long as it is on.
    val pulses = remember(style.pulseCount) { List(style.pulseCount) { Animatable(1f) } }
    val scope = rememberCoroutineScope()
    val isOn = active != null
    LaunchedEffect(isOn, animationsEnabled) {
        if (!isOn || !animationsEnabled) return@LaunchedEffect
        snapshotFlow { angle.isRunning }.first { !it }
        while (true) {
            pulses.forEachIndexed { i, p ->
                scope.launch { delay(i * style.pulseStaggerMs.toLong()); p.snapTo(0f); p.animateTo(1f, tween(style.pulseDurationMs)) }
            }
            delay(style.pulsePeriodMs.toLong())
        }
    }

    pulses.forEach { p ->
        val f = p.value
        if (f < 1f) {
            val spread = with(density) { style.pulseSpread.toPx() }
            val d = badgePx + 2f * spread * f
            Box(
                Modifier
                    .offset { IntOffset((cx - d / 2f).roundToInt(), (cy - d / 2f).roundToInt()) }
                    .requiredSize(with(density) { d.toDp() })
                    .graphicsLayer { alpha = (1f - f) * (1f - f) * 0.5f + (1f - f) * 0.5f }
                    .border(style.pulseWidth, pulseColor, CircleShape),
            )
        }
    }

    val isDisabled = status == OneRingFeatureStatus.Disabled
    Box(
        Modifier
            .offset { IntOffset((cx - badgePx / 2f).roundToInt(), (cy - badgePx / 2f).roundToInt()) }
            .size(style.badgeSize)
            .background(fill, CircleShape)
            .border(style.borderWidth, rim, CircleShape)
            .semantics { contentDescription = feature.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val slash = with(density) { style.slashWidth.toPx() }
        val slashGap = with(density) { style.slashGap.toPx() }
        Box(
            Modifier
                .requiredSize(style.iconSize)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (isDisabled) {
                        val from = Offset(size.width * 0.22f, size.height * 0.22f)
                        val to = Offset(size.width * 0.78f, size.height * 0.78f)
                        // Cut a clear gap either side of the slash, then draw the slash in it, so the line reads on the icon instead of merging with it.
                        drawLine(Color.Black, from, to, strokeWidth = slash + 2f * slashGap, cap = StrokeCap.Round, blendMode = BlendMode.Clear)
                        drawLine(iconTint, from, to, strokeWidth = slash, cap = StrokeCap.Round)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            RenderIcon(icon = feature.icon, tint = iconTint, size = style.iconSize)
        }
    }
}

private const val MIN_TRAVEL_MS = 250

/** Where a feature belongs: on is the top row, releasing the right-hand arc between the rows, anything else the bottom row. */
private fun rowOf(status: OneRingFeatureStatus): RowKind = when (status) {
    is OneRingFeatureStatus.Active -> RowKind.TOP
    is OneRingFeatureStatus.Releasing -> RowKind.ARC
    else -> RowKind.BOTTOM
}
