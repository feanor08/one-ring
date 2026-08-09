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
import androidx.compose.material.icons.rounded.Navigation
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

/** A. The simplest possible one-ring usage: six flat actions, press-scrub-release, no menus, no seek. */
@Composable
fun BasicDemo(onBack: () -> Unit) {
    var lastSelected by remember { mutableStateOf<String?>(null) }
    val log = rememberEventLogState()
    val state = rememberOneRingState()

    val items = remember {
        oneRingItems {
            action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq))
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            action(
                id = "messages",
                label = "Messages",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.Message),
            )
            action(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote))
            action(id = "navigation", label = "Navigation", icon = OneRingIcon.Vector(Icons.Rounded.Navigation))
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    DemoScaffold(
        title = Demo.Basic.title,
        subtitle = "Press, scrub between wedges to see the highlight and outward hover growth, release to commit.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = {
                lastSelected = it.label
                log.log("selected -> ${it.label}")
            },
            state = state,
            onEvent = { log.log(it.toString()) },
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Last selected: ${lastSelected ?: "(none yet)"}")
        Spacer(Modifier.height(16.dp))
        EventLogCard(log, modifier = Modifier.padding(bottom = 12.dp))
        CodeExampleCard(
            code = """
                val items = oneRingItems {
                    action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq))
                    // ... five more action(...) entries
                }

                OneRing(
                    items = items,
                    onAction = { lastSelected = it.label },
                )
            """,
        )
    }
}
