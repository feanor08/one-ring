package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold
import kotlinx.coroutines.delay

/** The ring should visually dominate the demo -- as large as the viewport allows, within this range. */
private val PreferredRingSize = 460.dp
private val MinRingSize = 300.dp
private val RingHorizontalMargin = 24.dp

/**
 * The center `coreOverlay` slot hosting a live notification feed alongside a
 * realistic six-item ring (a Music submenu, a main Volume seek). Proves the
 * feed hides while any submenu/seek is open and returns once the main ring
 * settles, same-thread messages accumulate into one card, and sources stay
 * grouped and independently accented while scrolling.
 */
@Composable
fun NotificationDemo(onBack: () -> Unit) {
    var mediaVolume by remember { mutableFloatStateOf(50f) }
    var mainVolume by remember { mutableFloatStateOf(40f) }
    var adarshFollowUpIndex by remember { mutableIntStateOf(0) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    val notifications = rememberOneRingNotificationState()
    val ringState = rememberOneRingState()

    LaunchedEffect(Unit) {
        delay(300)
        notifications.post(NotificationDemoData.adarshFirst())
    }

    val items = remember(mediaVolume, mainVolume) {
        oneRingItems {
            action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.Headset))
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            action(id = "messages", label = "Messages", icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.Message))
            menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                action(id = "previous", label = "Previous", icon = OneRingIcon.Vector(Icons.Rounded.SkipPrevious))
                action(id = "playPause", label = "Play / Pause", icon = OneRingIcon.Vector(Icons.Rounded.PlayArrow))
                action(id = "next", label = "Next", icon = OneRingIcon.Vector(Icons.Rounded.SkipNext))
                seek(
                    id = "mediaVolume",
                    label = "Volume",
                    icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                    value = mediaVolume,
                    onValueChange = { mediaVolume = it },
                    valueLabel = { "${it.toInt()}%" },
                )
            }
            seek(
                id = "volume",
                label = "Volume",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                value = mainVolume,
                onValueChange = { mainVolume = it },
                valueLabel = { "${it.toInt()}%" },
            )
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    // The ring's own core must stay transparent -- notifications float
    // directly over this screen's (light) background, never over a solid
    // disc. `center` (the small, separate slot) is unused by this demo, but
    // its default fill would otherwise still peek through the middle of the
    // much larger, otherwise-transparent coreOverlay whenever it has no
    // content of its own to draw (e.g. no notifications yet, or between
    // cards) -- so it's turned off here explicitly.
    val ringStyle = remember {
        OneRingDefaults.style(colors = OneRingDefaults.colors(center = Color.Transparent))
    }
    // The demo's own background is light, so the feed uses the library's
    // light neutral palette; each notification's own accentColor (set in
    // NotificationDemoData) still drives its border/icon/subtitle either way.
    val notificationStyle = remember {
        OneRingNotificationDefaults.style(colors = OneRingNotificationDefaults.lightColors())
    }

    DemoScaffold(
        title = Demo.Notifications.title,
        subtitle = "The feed lives in the ring's empty core (coreOverlay). Open Music or the Volume seek " +
            "and watch it fade out; it only returns once the main ring finishes settling back. Scroll to " +
            "read older notifications; swipe a card left, or tap its X, to dismiss it. Post two or more " +
            "WhatsApp messages to see them collapse into a tappable stack.",
        onBack = onBack,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val available: Dp = maxWidth - RingHorizontalMargin * 2
            // Never exceeds the available width (would clip); MinRingSize is
            // only a floor when there's room for it, never forced past `available`.
            val ringSize = available.coerceAtMost(PreferredRingSize).coerceAtLeast(minOf(MinRingSize, available))
            OneRing(
                items = items,
                onAction = { lastAction = it.label },
                state = ringState,
                style = ringStyle,
                modifier = Modifier.padding(vertical = 20.dp).size(ringSize),
                coreOverlay = { OneRingNotificationHost(state = notifications, style = notificationStyle) },
            )
        }
        Text("mode: ${ringState.mode}  ·  last action: ${lastAction ?: "(none yet)"}")

        Spacer(Modifier.height(16.dp))
        Text("Messages", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            Button(onClick = { notifications.post(NotificationDemoData.adarshFirst()) }) {
                Text("WhatsApp · Adarsh")
            }
            Button(
                onClick = {
                    notifications.post(NotificationDemoData.adarshFollowUp(adarshFollowUpIndex))
                    adarshFollowUpIndex++
                },
            ) { Text("Adarsh +1") }
            Button(onClick = { notifications.post(NotificationDemoData.varsha()) }) { Text("WhatsApp · Varsha") }
            Button(onClick = { notifications.post(NotificationDemoData.family()) }) { Text("WhatsApp · Family") }
        }

        Text("Other", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            Button(onClick = { notifications.post(NotificationDemoData.softwareUpdate()) }) { Text("Software Update") }
            Button(onClick = { notifications.post(NotificationDemoData.tyrePressure()) }) { Text("Tyre Pressure") }
            Button(onClick = { notifications.post(NotificationDemoData.serviceReminder()) }) { Text("Service Reminder") }
            Button(onClick = { notifications.post(NotificationDemoData.cloudNotification()) }) { Text("Cloud Notification") }
            Button(onClick = { notifications.post(NotificationDemoData.batteryAlert()) }) { Text("Battery Alert") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Button(onClick = { notifications.clear() }) { Text("Clear All") }
            Button(
                onClick = {
                    notifications.clear()
                    adarshFollowUpIndex = 0
                },
            ) { Text("Reset Demo") }
        }

        Spacer(Modifier.height(12.dp))
        CodeExampleCard(
            code = """
                val ringState = rememberOneRingState()
                val notifications = rememberOneRingNotificationState()

                OneRing(
                    items = items,
                    state = ringState,
                    onAction = { /* ... */ },
                    coreOverlay = { OneRingNotificationHost(state = notifications) },
                )

                notifications.post(
                    OneRingNotificationPost(
                        threadKey = "whatsapp:adarsh",
                        sourceKey = "whatsapp",
                        sourceLabel = "WhatsApp",
                        title = "Adarsh",
                        message = "I'll reach in 10 minutes.",
                        /* ... */
                    ),
                )
            """,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
