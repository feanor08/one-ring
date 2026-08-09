package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.rememberOneRingState
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.sample.ui.components.EventLogCard
import com.feanor08.onering.sample.ui.components.rememberEventLogState

/**
 * E + F. The nested-menu interaction: parents with different child counts
 * (Audio: 2, Bluetooth: 3, Messages: 3, Music: 4, Settings: 6), each
 * committing into the close control while its own angular gap stays
 * reserved in the resulting submenu band.
 */
@Composable
fun NestedDemo(onBack: () -> Unit) {
    var lastSelected by remember { mutableStateOf<String?>(null) }
    val log = rememberEventLogState()
    val state = rememberOneRingState()

    val items = remember {
        oneRingItems {
            menu(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq)) {
                action(id = "volume", label = "Volume")
                action(id = "mute", label = "Mute")
            }
            menu(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth)) {
                action(id = "devices", label = "Devices")
                action(id = "connect", label = "Connect")
                action(id = "disconnect", label = "Disconnect")
            }
            menu(
                id = "messages",
                label = "Messages",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.Message),
            ) {
                action(id = "read", label = "Read")
                action(id = "reply", label = "Reply")
                action(id = "dismiss", label = "Dismiss")
            }
            menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                action(id = "previous", label = "Previous")
                action(id = "playPause", label = "Play / Pause")
                action(id = "next", label = "Next")
                action(id = "shuffle", label = "Shuffle")
            }
            menu(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings)) {
                action(id = "display", label = "Display")
                action(id = "sound", label = "Sound")
                action(id = "network", label = "Network")
                action(id = "system", label = "System")
                action(id = "about", label = "About")
                action(id = "developer", label = "Developer")
            }
        }
    }

    DemoScaffold(
        title = Demo.Nested.title,
        subtitle = "Select a menu wedge: it grows into the X (close) control, siblings fade, and its " +
            "children reveal as a submenu band with the parent's own gap reserved. Press X to return.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = {
                lastSelected = it.label
                log.log("action -> ${it.label}")
            },
            state = state,
            onEvent = { log.log(it.toString()) },
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Last action: ${lastSelected ?: "(none yet)"}  ·  path: ${state.path.ifEmpty { listOf("(root)") }}")
        Spacer(Modifier.height(16.dp))
        EventLogCard(log, modifier = Modifier.padding(bottom = 12.dp))
        CodeExampleCard(
            code = """
                val items = oneRingItems {
                    menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                        action(id = "previous", label = "Previous")
                        action(id = "playPause", label = "Play / Pause")
                        action(id = "next", label = "Next")
                        action(id = "shuffle", label = "Shuffle")
                    }
                    // ... more menu(...) / action(...) siblings
                }

                OneRing(items = items, onAction = { lastSelected = it.label })
            """,
        )
    }
}
