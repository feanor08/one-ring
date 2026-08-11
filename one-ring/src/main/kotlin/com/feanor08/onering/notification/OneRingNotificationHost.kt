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
 *
 * ### Arrival ordering
 *
 * The feed does **not** render [OneRingNotificationState.groups] directly.
 * [state]'s own data updates the instant [OneRingNotificationState.post] is
 * called, but the *rendered* feed must never expose a new/updated card before
 * its arrival splash has started covering it -- otherwise the update would
 * visibly flash into view a frame (or more) before the splash appears. To
 * guarantee that ordering, this host keeps `safeGroups`, a snapshot that
 * continuously mirrors [state]'s live groups *only while no arrival is in
 * flight*; the moment an arrival starts, `safeGroups` is already correct
 * (the value from just before the change, no time-travel needed) and simply
 * stops being updated, so the feed stays pinned on it. [NotificationArrivalOverlay]
 * only calls back to swap `safeGroups` over to the live value once it is
 * already fully covering the screen (see its `onReveal` doc) -- so the swap
 * itself is never visible either. The feed is also made non-interactive for
 * the duration of an active arrival (see "coreOverlay interactivity" below),
 * so nothing (e.g. a dismiss) can race that snapshot.
 *
 * ### coreOverlay interactivity
 *
 * `OneRing`'s `coreOverlay` slot has no built-in mechanism that stops its
 * content from intercepting a submenu/seek gesture -- that's each
 * `coreOverlay` consumer's own responsibility, using
 * [com.feanor08.onering.OneRingCenterScope.mode] (see
 * [com.feanor08.onering.OneRing]'s `coreOverlay` parameter doc). This host
 * does it by threading an `interactive` flag (`visible && activeArrival ==
 * null`) down to [NotificationFeedList], which uses it to decide whether its
 * `pointerInput` modifiers -- the list's own scroll, each card's swipe, each
 * card's dismiss button -- are even *installed* at all, via plain
 * conditional composition (`Modifier.then(if (interactive) Modifier.pointerInput
 * { ... } else Modifier)` and `LazyColumn`'s own `userScrollEnabled`).
 *
 * This is deliberately a composition-level gate, not a pointer-consumption
 * one: a consumption-based approach (an ancestor marking events "consumed"
 * on some pass, hoping descendants -- and only descendants -- respect it)
 * depends on assumptions about how Compose orders and delivers events across
 * *sibling* subtrees (this feed vs. the ring's own `Canvas`), which aren't
 * something to rely on. Conditionally omitting the `pointerInput` modifiers
 * altogether needs no such assumption: when not interactive, there is
 * nothing here for hit-testing to find, full stop, regardless of how
 * sibling dispatch order works. The visual tree (icons, text, the fade
 * itself) is completely unaffected -- only whether it can be touched.
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

    // See this file's "Arrival ordering" doc: continuously mirrors
    // `state.groups` while nothing is (or is about to be) mid-arrival, so
    // it's already the correct pre-change value the instant an arrival
    // begins, and simply stops moving until that arrival's `onReveal`
    // explicitly catches it up.
    //
    // `arrivalPending` is NOT just `activeArrival != null`: `activeArrival`
    // is only set by the LaunchedEffect below, which runs asynchronously --
    // on the frame where a post() first makes `shouldPlayArrival` true, that
    // effect hasn't run yet, so `activeArrival` is still null. Gating on
    // `activeArrival` alone would let this exact frame mirror the brand-new
    // data before the splash has even started, reintroducing the "message
    // flashes before splash" bug. Recomputing the same `shouldPlayArrival`
    // check synchronously here closes that one-frame gap.
    val latestForGate = state.latestSequence
    val arrivalPending = activeArrival != null || shouldPlayArrival(visible, latestForGate, lastAnnouncedSequence)
    var safeGroups by remember { mutableStateOf(state.groups) }
    if (!arrivalPending) {
        safeGroups = state.groups
    }
    val displayedGroups = if (arrivalPending) safeGroups else state.groups

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

        // Non-interactive for the whole time an arrival is covering the feed
        // too -- there's nothing useful to touch behind an opaque splash,
        // and this is what keeps `safeGroups` from racing a dismiss (see the
        // class doc's "Arrival ordering" section).
        val interactive = visible && activeArrival == null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = feedAlpha.value }
                .semantics { if (!visible) invisibleToUser() },
        ) {
            NotificationFeedList(
                groups = displayedGroups,
                style = style,
                coreOverlayDiameterPx = diameterPx,
                interactive = interactive,
                onDismiss = { threadKey -> state.dismiss(threadKey) },
            )
        }

        activeArrival?.let { notification ->
            NotificationArrivalOverlay(
                notification = notification,
                style = style,
                animationsEnabled = animationsEnabled,
                onReveal = { safeGroups = state.groups },
                onFinished = {
                    lastAnnouncedSequence = advancedWatermark(notification.sequence)
                    activeArrival = null
                },
            )
        }
    }
}
