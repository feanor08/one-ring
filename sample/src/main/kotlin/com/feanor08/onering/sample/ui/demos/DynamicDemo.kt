package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

private data class Template(val id: String, val label: String, val icon: ImageVector)

private val pool = listOf(
    Template("wifi", "Wi-Fi", Icons.Rounded.Wifi),
    Template("bluetooth", "Bluetooth", Icons.Rounded.Bluetooth),
    Template("navigation", "Navigation", Icons.Rounded.Navigation),
    Template("camera", "Camera", Icons.Rounded.CameraAlt),
    Template("extra1", "Extra 1", Icons.Rounded.Star),
    Template("extra2", "Extra 2", Icons.Rounded.Star),
    Template("extra3", "Extra 3", Icons.Rounded.Star),
)

/** S + T + U + V. Add/remove/shuffle/disable at runtime, a dynamic label toggle, and hoisted seek state. */
@Composable
fun DynamicDemo(onBack: () -> Unit) {
    val active = remember {
        mutableStateListOf(
            pool[0].copy(),
            pool[1].copy(),
            pool[2].copy(),
            pool[3].copy(),
        )
    }
    val disabled = remember { mutableStateOf(setOf<String>()) }
    var brightness by remember { mutableStateOf(60f) }
    var lastSelected by remember { mutableStateOf<String?>(null) }

    fun rebuild(): List<OneRingItem> = active.map { t ->
        OneRingItem.Action(
            id = t.id,
            label = t.label,
            icon = OneRingIcon.Vector(t.icon),
            enabled = t.id !in disabled.value,
        )
    } + OneRingItem.Seek(
        id = "brightness",
        label = "Brightness",
        value = brightness,
        valueRange = 0f..100f,
        onValueChange = { brightness = it },
        valueLabel = { "${it.toInt()}%" },
    )

    val items = rebuild()

    DemoScaffold(
        title = Demo.Dynamic.title,
        subtitle = "The wheel is just a function of this list -- change it, and the geometry recomputes with " +
            "no Activity restart. Brightness is hoisted, so the slider below and the ring stay in sync.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = { lastSelected = it.label },
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Last selected: ${lastSelected ?: "(none yet)"}  ·  ${active.size} items")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = {
                val next = pool.firstOrNull { it.id !in active.map(Template::id) }
                if (next != null) active.add(next.copy())
            }) { Text("Add") }
            Button(onClick = { if (active.size > 1) active.removeAt(active.lastIndex) }) { Text("Remove") }
            Button(onClick = { active.shuffle() }) { Text("Shuffle") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = {
                val candidate = active.map(Template::id).randomOrNull()
                if (candidate != null) {
                    disabled.value =
                        if (candidate in disabled.value) disabled.value - candidate else disabled.value + candidate
                }
            }) { Text("Toggle random disabled") }
            Button(onClick = {
                active.clear()
                active.addAll(listOf(pool[0].copy(), pool[1].copy(), pool[2].copy(), pool[3].copy()))
                disabled.value = emptySet()
            }) { Text("Reset") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Brightness (hoisted): ${brightness.toInt()}%")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..100f)

        CodeExampleCard(
            code = """
                var active by remember { mutableStateListOf(/* ... */) }

                val items = active.map { t ->
                    OneRingItem.Action(id = t.id, label = t.label, enabled = t.id !in disabledIds)
                }

                OneRing(items = items, onAction = { lastSelected = it.label })
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
