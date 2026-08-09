package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.rememberDefaultOneRingHaptics
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.sample.ui.components.EventLogCard
import com.feanor08.onering.sample.ui.components.rememberEventLogState

/** S + W + section 8. Disabled items, the haptics switch, and an honest account of what TalkBack can do today. */
@Composable
fun AccessibilityDemo(onBack: () -> Unit) {
    var hapticsOn by remember { mutableStateOf(true) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableFloatStateOf(40f) }
    val log = rememberEventLogState()
    val defaultHaptics = rememberDefaultOneRingHaptics()

    val items = remember(brightness) {
        oneRingItems {
            action(id = "wifi", label = "Wi-Fi", icon = OneRingIcon.Vector(Icons.Rounded.Wifi))
            action(
                id = "bluetooth",
                label = "Bluetooth",
                icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth),
                enabled = false,
                contentDescription = "Bluetooth, unavailable",
            )
            action(id = "navigation", label = "Navigation", icon = OneRingIcon.Vector(Icons.Rounded.Navigation))
            action(
                id = "camera",
                label = "Camera",
                icon = OneRingIcon.Vector(Icons.Rounded.CameraAlt),
                enabled = false,
                contentDescription = "Camera, unavailable",
            )
            seek(
                id = "brightness",
                label = "Brightness",
                value = brightness,
                valueRange = 0f..100f,
                onValueChange = { brightness = it },
                valueLabel = { "${it.toInt()}%" },
            )
        }
    }

    DemoScaffold(
        title = Demo.Accessibility.title,
        subtitle = "Bluetooth and Camera are disabled: they render dimmed, don't commit, and are announced as " +
            "disabled. Turn TalkBack on and swipe through the wheel to hear the semantics.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = {
                lastAction = it.label
                log.log("action -> ${it.label}")
            },
            onEvent = { log.log(it.toString()) },
            haptics = if (hapticsOn) defaultHaptics else OneRingHaptics.None,
            style = OneRingDefaults.style(behavior = OneRingDefaults.behavior(hapticsEnabled = hapticsOn)),
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Last action: ${lastAction ?: "(none yet)"}  ·  Brightness: ${brightness.toInt()}%")

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text("Haptics", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hapticsOn, onCheckedChange = { hapticsOn = it })
        }
        Text(
            "Haptics fire on crossing into a new sector, committing, and entering/exiting seek -- never " +
                "continuously while the pointer stays inside one wedge.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("What accessibility support exists today", style = MaterialTheme.typography.titleSmall)
                Bullet("Every wedge is a real, invisible semantics node positioned at its own midpoint.")
                Bullet("Content descriptions fall back to the item's label when none is supplied.")
                Bullet("Role is Button; focus order follows clockwise item order via traversalIndex.")
                Bullet("Disabled items are marked disabled(); the highlighted item is marked selected.")
                Bullet("Menus can be opened AND closed by TalkBack -- the open parent exposes a \"Close X\" node.")
                Bullet("A seek control can be entered and exited without any pointer gesture.")
                Bullet("System \"remove animations\" is honored automatically -- all specs become snap().")

                Spacer(Modifier.height(8.dp))
                Text("Current limitations", style = MaterialTheme.typography.titleSmall)
                Bullet(
                    "A seek's VALUE cannot yet be changed via TalkBack -- there is no setProgress / " +
                        "ProgressBarRangeInfo semantics on the active seek, so adjusting it still needs a real drag.",
                )
                Bullet(
                    "Semantics nodes are fixed 48dp targets at wedge midpoints, not wedge-shaped regions, " +
                        "so explore-by-touch between them lands on empty space rather than the neighbouring wedge.",
                )
                Bullet("No stateDescription is published (e.g. \"70 percent\") for seek items.")
            }
        }

        Spacer(Modifier.height(12.dp))
        EventLogCard(log)
    }
}

@Composable
private fun Bullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}
