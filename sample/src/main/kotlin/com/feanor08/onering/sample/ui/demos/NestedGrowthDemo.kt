package com.feanor08.onering.sample.ui.demos

import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.sample.ui.components.EventLogCard
import com.feanor08.onering.sample.ui.components.rememberEventLogState

private enum class GrowthSpeed(val label: String, val revealMs: Int, val selectMs: Int) {
    /** Deliberately exaggerated so the radial growth can be inspected frame by frame. */
    Slow("Slow", 1200, 1000),

    /** The library's production timing (see OneRingDefaults.motion). */
    Default("Default", 280, 280),
    Fast("Fast", 180, 180),
}

/**
 * The submenu opening choreography, and the combined weighted + nested + seek
 * case: a weight-2 Media parent whose children are themselves weighted, with a
 * weight-2 Volume seek among them.
 */
@Composable
fun NestedGrowthDemo(onBack: () -> Unit) {
    var speed by remember { mutableStateOf(GrowthSpeed.Slow) }
    var mediaVolume by remember { mutableFloatStateOf(60f) }
    var temperature by remember { mutableFloatStateOf(21f) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    // Bumping this remounts the ring with fresh state, returning it to the main
    // menu so the open animation can be triggered again without leaving the
    // screen. (Opening a menu programmatically isn't part of the public API.)
    var resetKey by remember { mutableIntStateOf(0) }
    val log = rememberEventLogState()

    val items = remember(mediaVolume, temperature) {
        oneRingItems {
            // Weight 2: Media takes half the main ring, so its reserved gap --
            // and therefore the submenu's available sweep -- is visibly unequal.
            menu(id = "media", label = "Media", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote), weight = 2f) {
                action(
                    id = "playPause",
                    label = "Play / Pause",
                    icon = OneRingIcon.Vector(Icons.Rounded.Pause),
                    weight = 2f,
                )
                action(id = "previous", label = "Previous", icon = OneRingIcon.Vector(Icons.Rounded.SkipPrevious))
                action(id = "next", label = "Next", icon = OneRingIcon.Vector(Icons.Rounded.SkipNext))
                seek(
                    id = "mediaVolume",
                    label = "Volume",
                    icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                    value = mediaVolume,
                    valueRange = 0f..100f,
                    onValueChange = { mediaVolume = it },
                    valueLabel = { "${it.toInt()}%" },
                    weight = 2f,
                )
            }
            menu(id = "climate", label = "Climate", icon = OneRingIcon.Vector(Icons.Rounded.Thermostat)) {
                seek(
                    id = "temperature",
                    label = "Temperature",
                    value = temperature,
                    valueRange = 16f..30f,
                    steps = 27,
                    onValueChange = { temperature = it },
                    valueLabel = { "%.1f°C".format(it) },
                    weight = 2f,
                )
                action(id = "fan", label = "Fan", icon = OneRingIcon.Vector(Icons.Rounded.Toys))
                action(id = "ac", label = "AC", icon = OneRingIcon.Vector(Icons.Rounded.AcUnit))
            }
            action(id = "navigation", label = "Navigation", icon = OneRingIcon.Vector(Icons.Rounded.Navigation))
        }
    }

    val style = remember(speed) {
        OneRingDefaults.style(
            motion = OneRingDefaults.motion(
                submenuReveal = tween(speed.revealMs),
                select = tween(speed.selectMs),
                close = tween((speed.selectMs * 0.9f).toInt()),
                siblingExit = tween((speed.selectMs * 0.6f).toInt()),
            ),
        )
    }

    DemoScaffold(
        title = Demo.NestedGrowth.title,
        subtitle = "Select a parent and watch: its icon fades out, the wedge grows, the submenu grows " +
            "outer-edge inward, then the X fades in and rests in normal styling. Touch the X to see it " +
            "highlight; scrub off it and release to cancel the close. Slow mode separates every stage.",
        onBack = onBack,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            GrowthSpeed.entries.forEach { s ->
                FilterChip(selected = s == speed, onClick = { speed = s }, label = { Text(s.label) })
            }
        }

        // The ring's state lives inside the key so "Reset" genuinely returns it
        // to the main menu rather than just remounting an already-open ring.
        key(resetKey) {
            val state = rememberOneRingState()
            OneRing(
                items = items,
                onAction = {
                    lastAction = it.label
                    log.log("action -> ${it.label}")
                },
                state = state,
                style = style,
                onEvent = { log.log(it.toString()) },
                modifier = Modifier.padding(vertical = 20.dp).size(330.dp),
            )
            Text("path: ${state.path.ifEmpty { listOf("(root)") }}  ·  mode: ${state.mode}")
        }

        Button(onClick = { resetKey++ }) { Text("Reset to main ring (replay)") }

        Text("Main ring: Media 2 / Climate 1 / Navigation 1  ->  50% / 25% / 25%")
        Text("Media children: Play 2 / Previous 1 / Next 1 / Volume 2  ->  33.3% / 16.7% / 16.7% / 33.3%")
        Text(
            "Last action: ${lastAction ?: "(none yet)"}",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Media volume: ${mediaVolume.toInt()}%  ·  Temperature: ${"%.1f".format(temperature)}°C")

        Spacer(Modifier.height(12.dp))
        EventLogCard(log)
        CodeExampleCard(
            code = """
                oneRingItems {
                    menu(id = "media", label = "Media", weight = 2f) {   // half the main ring
                        action(id = "playPause", label = "Play / Pause", weight = 2f)
                        action(id = "previous",  label = "Previous")     // weight defaults to 1f
                        action(id = "next",      label = "Next")
                        seek(id = "mediaVolume", label = "Volume", weight = 2f, /* ... */)
                    }
                    menu(id = "climate", label = "Climate") { /* ... */ }
                    action(id = "navigation", label = "Navigation")
                }
            """,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
