package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.notification.OneRingNotificationDefaults
import com.feanor08.onering.notification.OneRingNotificationHost
import com.feanor08.onering.notification.rememberOneRingNotificationState
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.rememberOneRingState
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.DemoScaffold

private val PreferredRingSize = 460.dp
private val MinRingSize = 300.dp
private val RingHorizontalMargin = 24.dp

/**
 * A dedicated screen, pre-loaded on open, for visually checking the
 * notification stack at real scale -- 8 and 20 threads under one source each
 * (see [NotificationStressData]) -- rather than tapping individual demo
 * buttons one at a time. Both stacks are pre-populated silently (the feed
 * stays hidden -- `visible = false` -- for the one frame the posts happen
 * in, so no arrival splash plays 28 times in a row; see
 * `OneRingNotificationHost`'s "Arrival ordering" doc for why a post while
 * hidden never plays that animation) and the feed only becomes visible once
 * everything is already in place. "Reload" clears and redoes this from
 * scratch, for repeat checks.
 *
 * What to look for (docs/notifications-plan.md's Phase 5 performance check):
 * front-card anchoring, real rear-card peel, clip/border/corner quality,
 * content-reveal timing, and frame smoothness -- on both the 8-thread and
 * 20-thread stack, and especially during rapid repeated tap-to-expand /
 * tap-to-collapse.
 */
@Composable
fun NotificationStackStressDemo(onBack: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val notifications = rememberOneRingNotificationState()
    val ringState = rememberOneRingState()

    LaunchedEffect(reloadKey) {
        visible = false
        notifications.clear()
        NotificationStressData.eightThreadStack().forEach { notifications.post(it) }
        NotificationStressData.twentyThreadStack().forEach { notifications.post(it) }
        visible = true
    }

    val items = remember {
        oneRingItems {
            action(id = "a", label = "A", icon = OneRingIcon.None)
            action(id = "b", label = "B", icon = OneRingIcon.None)
            action(id = "c", label = "C", icon = OneRingIcon.None)
            action(id = "d", label = "D", icon = OneRingIcon.None)
        }
    }

    // Same reasoning as NotificationDemo: keep the ring's own center
    // transparent so the feed floats directly over this screen's background.
    val ringStyle = remember {
        OneRingDefaults.style(colors = OneRingDefaults.colors(center = Color.Transparent))
    }
    val notificationStyle = remember {
        OneRingNotificationDefaults.style(colors = OneRingNotificationDefaults.lightColors())
    }

    DemoScaffold(
        title = "Notification Stack Stress",
        subtitle = "Two pre-loaded stacks -- 8 threads and 20 threads, one source each, with a mix of " +
            "short and long messages -- for checking front-card anchoring, real rear-card peel, clipping, " +
            "and frame smoothness at real scale. Scroll to see both; tap a front card to expand/collapse.",
        onBack = onBack,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val available: Dp = maxWidth - RingHorizontalMargin * 2
            val ringSize = available.coerceAtMost(PreferredRingSize).coerceAtLeast(minOf(MinRingSize, available))
            OneRing(
                items = items,
                onAction = {},
                state = ringState,
                style = ringStyle,
                modifier = Modifier.padding(vertical = 20.dp).size(ringSize),
                coreOverlay = { OneRingNotificationHost(state = notifications, visible = visible, style = notificationStyle) },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Button(onClick = { reloadKey++ }) { Text("Reload") }
        }
    }
}
