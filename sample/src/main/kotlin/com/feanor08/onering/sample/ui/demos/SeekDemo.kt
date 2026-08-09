package com.feanor08.onering.sample.ui.demos

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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

private data class SeekPreset(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val format: (Float) -> String,
)

private val seekPresets = listOf(
    SeekPreset("volume", "Volume", Icons.AutoMirrored.Rounded.VolumeUp, 0f..100f, 0) { "${it.toInt()}%" },
    SeekPreset("brightness", "Brightness", Icons.Rounded.Brightness6, 0f..100f, 0) { "${it.toInt()}%" },
    SeekPreset("temperature", "Temperature", Icons.Rounded.Thermostat, 16f..30f, 27) { "%.1f°C".format(it) },
    SeekPreset("zoom", "Zoom", Icons.Rounded.ZoomIn, 1f..5f, 7) { "%.1fx".format(it) },
    SeekPreset("fan", "Fan speed", Icons.Rounded.Air, 0f..5f, 4) { "${it.toInt()}" },
    SeekPreset("position", "Media position", Icons.Rounded.PlayCircle, 0f..300f, 0) { "${it.toInt()}s" },
)

/** G + H + I + J + K. Main-ring seek, submenu seek, arbitrary ranges/steps, and the intro-hint overshoot. */
@Composable
fun SeekDemo(onBack: () -> Unit) {
    DemoScaffold(title = Demo.Seek.title, onBack = onBack) {
        MainRingSeekSection()
        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        SubmenuSeekSection()
        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        StepSizeSection()
    }
}

@Composable
private fun MainRingSeekSection() {
    var presetIndex by remember { mutableStateOf(0) }
    var value by remember(presetIndex) { mutableFloatStateOf(seekPresets[presetIndex].range.start) }
    var hintEnabled by remember { mutableStateOf(true) }
    var overshoot by remember { mutableFloatStateOf(0.05f) }
    val preset = seekPresets[presetIndex]

    Text("Main-ring seek", style = MaterialTheme.typography.titleMedium)
    Text(
        "Select the seek wedge: it grows into the X, siblings fade, and the rest of the ring becomes a " +
            "drag track that starts on its clockwise edge.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        seekPresets.forEachIndexed { index, p ->
            FilterChip(selected = index == presetIndex, onClick = { presetIndex = index }, label = { Text(p.label) })
        }
    }

    val items = remember(preset, value) {
        oneRingItems {
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            seek(
                id = preset.id,
                label = preset.label,
                icon = OneRingIcon.Vector(preset.icon),
                value = value,
                valueRange = preset.range,
                steps = preset.steps,
                onValueChange = { value = it },
                valueLabel = preset.format,
            )
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    OneRing(
        items = items,
        onAction = {},
        style = OneRingDefaults.style(
            behavior = OneRingDefaults.behavior(seekHintEnabled = hintEnabled, seekHintOvershootFraction = overshoot),
        ),
        modifier = Modifier.padding(vertical = 16.dp).size(300.dp),
    )
    Text("${preset.label}: ${preset.format(value)}")

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Intro hint", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = hintEnabled, onCheckedChange = { hintEnabled = it })
    }
    Text("Overshoot: ${(overshoot * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0.02f, 0.05f, 0.10f).forEach { fraction ->
            FilterChip(
                selected = overshoot == fraction,
                onClick = { overshoot = fraction },
                label = { Text("${(fraction * 100).toInt()}%") },
            )
        }
    }
    CodeExampleCard(
        code = """
            var volume by remember { mutableFloatStateOf(48f) }

            val items = oneRingItems {
                seek(
                    id = "volume", label = "Volume", value = volume,
                    valueRange = 0f..100f,
                    onValueChange = { volume = it },
                    valueLabel = { "${'$'}{it.toInt()}%" },
                )
            }

            OneRing(
                items = items, onAction = {},
                style = OneRingDefaults.style(
                    behavior = OneRingDefaults.behavior(seekHintOvershootFraction = 0.05f),
                ),
            )
        """,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SubmenuSeekSection() {
    var musicVolume by remember { mutableFloatStateOf(67f) }

    Text("Submenu seek", style = MaterialTheme.typography.titleMedium)
    Text(
        "Open Music, then select Volume: the Music parent stays the outer X, sibling children fade, and " +
            "the seek track is built from what's left -- excluding both the parent gap and Volume's own wedge.",
        style = MaterialTheme.typography.bodySmall,
    )

    val items = remember(musicVolume) {
        oneRingItems {
            action(id = "audio", label = "Audio")
            menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                action(id = "previous", label = "Previous")
                action(id = "playPause", label = "Play / Pause")
                action(id = "next", label = "Next")
                seek(
                    id = "musicVolume",
                    label = "Volume",
                    icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                    value = musicVolume,
                    valueRange = 0f..100f,
                    onValueChange = { musicVolume = it },
                    valueLabel = { "${it.toInt()}%" },
                )
            }
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    OneRing(
        items = items,
        onAction = {},
        modifier = Modifier.padding(vertical = 16.dp).size(300.dp),
    )
    Text("Music volume: ${musicVolume.toInt()}%")
}

@Composable
private fun StepSizeSection() {
    var stepIndex by remember { mutableStateOf(0) }
    val stepSizes = listOf(0, 1, 5, 10)
    val stepLabels = listOf("Continuous", "Step 1", "Step 5", "Step 10")
    var value by remember { mutableFloatStateOf(50f) }
    val steps = when (val size = stepSizes[stepIndex]) {
        0 -> 0
        else -> (100 / size) - 1
    }

    Text("Seek steps", style = MaterialTheme.typography.titleMedium)
    Text("Same 0-100 range, different quantization -- one-ring never interprets the value, only the fraction.")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        stepLabels.forEachIndexed { index, label ->
            FilterChip(selected = index == stepIndex, onClick = { stepIndex = index }, label = { Text(label) })
        }
    }

    val items = remember(steps, value) {
        oneRingItems {
            action(id = "a", label = "A")
            seek(
                id = "quantized",
                label = "Value",
                icon = OneRingIcon.Vector(Icons.Rounded.Brightness6),
                value = value,
                valueRange = 0f..100f,
                steps = steps,
                onValueChange = { value = it },
                valueLabel = { "${it.toInt()}" },
            )
            action(id = "b", label = "B")
        }
    }

    OneRing(
        items = items,
        onAction = {},
        modifier = Modifier.padding(vertical = 16.dp).size(280.dp),
    )
    Text("Value: ${value.toInt()}")
}
