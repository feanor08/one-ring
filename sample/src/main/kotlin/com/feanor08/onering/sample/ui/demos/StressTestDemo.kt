package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.rememberOneRingState
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.DebugOverlayCard
import com.feanor08.onering.sample.ui.components.DebugOverlayToggle
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.sample.ui.components.EventLogCard
import com.feanor08.onering.sample.ui.components.rememberEventLogState

/** Y + Z. The stutter-regression proof, and a rapid-input torture test with a live state readout. */
@Composable
fun StressTestDemo(onBack: () -> Unit) {
    DemoScaffold(title = Demo.StressTest.title, onBack = onBack) {
        ContinuitySection()
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        RapidInputSection()
    }
}

@Composable
private fun ContinuitySection() {
    var lastSelected by remember { mutableStateOf<String?>(null) }

    Text("Animation continuity", style = MaterialTheme.typography.titleMedium)
    Text(
        "Quickly scrub onto a sector and release BEFORE its hover animation finishes. The committed sector " +
            "must continue growing from wherever it currently is -- it must not snap inward first, and it " +
            "must not jump outward before continuing. Motion here is deliberately slowed so the difference " +
            "would be obvious if the regression ever came back.",
        style = MaterialTheme.typography.bodySmall,
    )

    val items = remember {
        oneRingItems {
            repeat(6) { i ->
                menu(id = "m$i", label = "Menu $i", icon = OneRingIcon.Vector(Icons.Rounded.Star)) {
                    action(id = "m${i}a", label = "Child A")
                    action(id = "m${i}b", label = "Child B")
                }
            }
        }
    }

    OneRing(
        items = items,
        onAction = { lastSelected = it.label },
        style = OneRingDefaults.style(
            motion = OneRingDefaults.motion(
                select = androidx.compose.animation.core.tween(900),
                submenuReveal = androidx.compose.animation.core.tween(900),
                close = androidx.compose.animation.core.tween(700),
            ),
        ),
        modifier = Modifier.padding(vertical = 16.dp).size(320.dp),
    )
    Text("Last action: ${lastSelected ?: "(none yet)"}")
}

@Composable
private fun RapidInputSection() {
    val state = rememberOneRingState()
    val log = rememberEventLogState()
    var showDebug by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(50f) }

    Text("Rapid input", style = MaterialTheme.typography.titleMedium)
    Text(
        "Open, close, open a different menu, scrub rapidly, release early, press X quickly -- repeat as fast " +
            "as you can. The UI must never get stuck in Transitioning or stop responding. Watch `mode` below: " +
            "it should always settle back to Idle or Submenu.",
        style = MaterialTheme.typography.bodySmall,
    )

    val items = remember(volume) {
        oneRingItems {
            menu(id = "alpha", label = "Alpha", icon = OneRingIcon.Vector(Icons.Rounded.Star)) {
                action(id = "a1", label = "A1")
                action(id = "a2", label = "A2")
                action(id = "a3", label = "A3")
            }
            menu(id = "beta", label = "Beta", icon = OneRingIcon.Vector(Icons.Rounded.Star)) {
                action(id = "b1", label = "B1")
                action(id = "b2", label = "B2")
            }
            menu(id = "gamma", label = "Gamma", icon = OneRingIcon.Vector(Icons.Rounded.Star)) {
                action(id = "g1", label = "G1")
                action(id = "g2", label = "G2")
                action(id = "g3", label = "G3")
                action(id = "g4", label = "G4")
            }
            seek(
                id = "volume",
                label = "Volume",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                value = volume,
                valueRange = 0f..100f,
                onValueChange = { volume = it },
                valueLabel = { "${it.toInt()}%" },
            )
            action(id = "leaf", label = "Leaf", icon = OneRingIcon.Vector(Icons.Rounded.Star))
        }
    }

    OneRing(
        items = items,
        onAction = { interactions++ },
        state = state,
        onEvent = {
            interactions++
            log.log(it.toString())
        },
        style = OneRingDefaults.gameStyle(),
        modifier = Modifier.padding(vertical = 16.dp).size(320.dp),
    )
    Text("Interactions handled: $interactions")
    Button(onClick = {
        interactions = 0
        log.clear()
    }, modifier = Modifier.padding(top = 8.dp)) {
        Text("Reset counters")
    }

    Spacer(Modifier.height(12.dp))
    DebugOverlayToggle(enabled = showDebug, onEnabledChange = { showDebug = it })
    if (showDebug) {
        DebugOverlayCard(
            state = state,
            itemCount = items.size,
            activeParentId = state.path.lastOrNull(),
            activeSeekValue = "${volume.toInt()}%",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    EventLogCard(log)
}
