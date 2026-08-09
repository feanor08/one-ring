package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Toys
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
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

/** A realistic vehicle-HMI preset: larger touch targets, a Climate submenu with a temperature seek. */
@Composable
fun AutomotiveDemo(onBack: () -> Unit) {
    var lastAction by remember { mutableStateOf<String?>(null) }
    var temperature by remember { mutableFloatStateOf(22f) }
    var fanSpeed by remember { mutableFloatStateOf(2f) }
    var mediaVolume by remember { mutableFloatStateOf(0.5f) }

    val items = remember(temperature, fanSpeed, mediaVolume) {
        oneRingItems {
            action(id = "navigation", label = "Navigation", icon = OneRingIcon.Vector(Icons.Rounded.Navigation))
            menu(id = "media", label = "Media", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                action(id = "previous", label = "Previous")
                action(id = "playPause", label = "Play / Pause")
                action(id = "next", label = "Next")
                seek(
                    id = "mediaVolume",
                    label = "Volume",
                    icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                    value = mediaVolume,
                    onValueChange = { mediaVolume = it },
                    valueLabel = { "${(it * 100).toInt()}%" },
                )
            }
            action(id = "calls", label = "Calls", icon = OneRingIcon.Vector(Icons.Rounded.Call))
            menu(id = "climate", label = "Climate", icon = OneRingIcon.Vector(Icons.Rounded.Thermostat)) {
                seek(
                    id = "temperature",
                    label = "Temperature",
                    value = temperature,
                    valueRange = 16f..30f,
                    steps = 27,
                    onValueChange = { temperature = it },
                    valueLabel = { "%.1f°C".format(it) },
                )
                seek(
                    id = "fan",
                    label = "Fan",
                    icon = OneRingIcon.Vector(Icons.Rounded.Toys),
                    value = fanSpeed,
                    valueRange = 0f..5f,
                    steps = 4,
                    onValueChange = { fanSpeed = it },
                    valueLabel = { "${it.toInt()}" },
                )
                action(
                    id = "seatHeat",
                    label = "Seat Heat",
                    icon = OneRingIcon.Vector(Icons.Rounded.AirlineSeatReclineNormal),
                )
                action(id = "ac", label = "AC", icon = OneRingIcon.Vector(Icons.Rounded.AcUnit))
            }
            action(id = "vehicle", label = "Vehicle", icon = OneRingIcon.Vector(Icons.Rounded.DirectionsCar))
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    DemoScaffold(
        title = Demo.Automotive.title,
        subtitle = "OneRingDefaults.automotiveStyle() -- larger touch slop, bolder highlight and stroke. " +
            "No real car APIs are integrated; this is purely a UI showcase.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = { lastAction = it.label },
            style = OneRingDefaults.automotiveStyle(),
            modifier = Modifier.padding(vertical = 24.dp).size(340.dp),
        )
        Text("Last action: ${lastAction ?: "(none yet)"}")
        Text(
            "Climate -> Temperature: ${"%.1f".format(temperature)}°C  ·  " +
                "Fan: ${fanSpeed.toInt()}  ·  Media volume: ${(mediaVolume * 100).toInt()}%",
        )
        CodeExampleCard(
            code = """
                OneRing(
                    items = items, onAction = { ... },
                    style = OneRingDefaults.automotiveStyle(),
                )
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
