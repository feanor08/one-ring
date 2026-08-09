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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

private data class WeightPreset(val label: String, val labels: List<String>, val weights: List<Float>)

private val presets = listOf(
    WeightPreset("50/25/25", listOf("Primary", "Secondary", "Third"), listOf(2f, 1f, 1f)),
    WeightPreset("40/30/20/10", listOf("A", "B", "C", "D"), listOf(4f, 3f, 2f, 1f)),
    WeightPreset("5/1/1/1/2", listOf("Huge", "One", "Two", "Three", "Double"), listOf(5f, 1f, 1f, 1f, 2f)),
    WeightPreset("Equal", List(6) { "Item ${it + 1}" }, List(6) { 1f }),
)

/**
 * Weighted sectors: relative weights, normalized against their siblings and
 * scaled to whatever sweep is available. The percentages shown under each ring
 * are computed the same way the geometry engine does, so they're a readable
 * cross-check on what's drawn.
 */
@Composable
fun WeightedDemo(onBack: () -> Unit) {
    DemoScaffold(
        title = Demo.Weighted.title,
        subtitle = "Weights are relative, not percentages -- 2/1/1 and 50/25/25 are the same ring.",
        onBack = onBack,
    ) {
        PresetSection()
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        LiveEditorSection()
    }
}

@Composable
private fun PresetSection() {
    var preset by remember { mutableStateOf(presets.first()) }
    var lastSelected by remember { mutableStateOf<String?>(null) }

    Text("Presets", style = MaterialTheme.typography.titleMedium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        presets.forEach { p ->
            FilterChip(selected = p == preset, onClick = { preset = p }, label = { Text(p.label) })
        }
    }

    val items = remember(preset) {
        preset.labels.mapIndexed { index, label ->
            OneRingItem.Action(
                id = "item$index",
                label = label,
                icon = OneRingIcon.Vector(Icons.Rounded.Star),
                weight = preset.weights[index],
            )
        }
    }

    OneRing(
        items = items,
        onAction = { lastSelected = it.label },
        modifier = Modifier.padding(vertical = 20.dp).size(320.dp),
    )
    WeightBreakdown(preset.labels, preset.weights)
    Text("Last selected: ${lastSelected ?: "(none yet)"}", modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LiveEditorSection() {
    var weightA by remember { mutableFloatStateOf(2f) }
    var weightB by remember { mutableFloatStateOf(1f) }
    var weightC by remember { mutableFloatStateOf(1f) }
    var lastSelected by remember { mutableStateOf<String?>(null) }

    Text("Live weight editor", style = MaterialTheme.typography.titleMedium)
    Text(
        "Drag a slider and the ring re-lays out immediately -- geometry is recomputed from the weights, " +
            "never from a cached sector size.",
        style = MaterialTheme.typography.bodySmall,
    )

    val labels = listOf("Item A", "Item B", "Item C")
    val weights = listOf(weightA, weightB, weightC)
    val items = remember(weightA, weightB, weightC) {
        labels.mapIndexed { index, label ->
            OneRingItem.Action(
                id = "live$index",
                label = label,
                icon = OneRingIcon.Vector(Icons.Rounded.Star),
                weight = weights[index],
            )
        }
    }

    OneRing(
        items = items,
        onAction = { lastSelected = it.label },
        modifier = Modifier.padding(vertical = 20.dp).size(320.dp),
    )
    WeightBreakdown(labels, weights)
    Text("Last selected: ${lastSelected ?: "(none yet)"}", modifier = Modifier.padding(top = 8.dp))

    Spacer(Modifier.height(12.dp))
    WeightSlider("Item A weight", weightA) { weightA = it }
    WeightSlider("Item B weight", weightB) { weightB = it }
    WeightSlider("Item C weight", weightC) { weightC = it }

    CodeExampleCard(
        code = """
            val items = listOf(
                OneRingItem.Action(id = "primary",   label = "Primary",   weight = 2f),
                OneRingItem.Action(id = "secondary", label = "Secondary", weight = 1f),
                OneRingItem.Action(id = "third",     label = "Third",     weight = 1f),
            )

            // Weights are relative: 2/1/1 == 50/25/25. Omit weight entirely
            // (it defaults to 1f) and every sector is equal, as before.
            OneRing(items = items, onAction = { ... })
        """,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun WeightSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = 0.5f..5f)
}

/** Shows each item's weight and the share of the ring it normalizes to. */
@Composable
private fun WeightBreakdown(labels: List<String>, weights: List<Float>) {
    val total = weights.sum()
    labels.forEachIndexed { index, label ->
        val percent = if (total > 0f) weights[index] / total * 100f else 0f
        Text(
            "$label: ${"%.2f".format(weights[index])} -> ${"%.1f".format(percent)}%",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
