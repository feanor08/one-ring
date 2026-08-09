package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.style.RadialSize

/** C + D + N. A live playground over every radius, angle, and spacing knob on OneRingDimensions. */
@Composable
fun GeometryDemo(onBack: () -> Unit) {
    var mainInner by remember { mutableFloatStateOf(0.552f) }
    var mainOuter by remember { mutableFloatStateOf(0.808f) }
    var mainHoverOuter by remember { mutableFloatStateOf(0.880f) }
    var mainSelectedOuter by remember { mutableFloatStateOf(0.952f) }
    var submenuInner by remember { mutableFloatStateOf(0.280f) }
    var submenuOuter by remember { mutableFloatStateOf(0.512f) }
    var sectorGap by remember { mutableFloatStateOf(2f) }
    var seekThumbRadius by remember { mutableFloatStateOf(7f) }
    var startAngle by remember { mutableFloatStateOf(0f) }
    var totalSweep by remember { mutableFloatStateOf(360f) }

    val items = remember {
        oneRingItems {
            action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq))
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            action(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote))
            seek(
                id = "volume",
                label = "Volume",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                value = 0.5f,
                onValueChange = {},
            )
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    val dimensions = OneRingDefaults.dimensions(
        mainInner = RadialSize.Fraction(mainInner),
        mainOuter = RadialSize.Fraction(mainOuter),
        mainHoverOuter = RadialSize.Fraction(mainHoverOuter),
        mainSelectedOuter = RadialSize.Fraction(mainSelectedOuter),
        submenuInner = RadialSize.Fraction(submenuInner),
        submenuOuter = RadialSize.Fraction(submenuOuter),
        sectorGap = sectorGap.dp,
        seekThumbRadius = seekThumbRadius.dp,
        startAngleDeg = startAngle,
        totalSweepDeg = totalSweep,
    )

    DemoScaffold(
        title = Demo.Geometry.title,
        subtitle = "Every knob here is a real OneRingDimensions field, built through OneRingDefaults.dimensions().",
        onBack = onBack,
    ) {
        Text("Size preset", style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(selected = false, onClick = {
                mainInner = 0.552f
                mainOuter = 0.808f
                mainHoverOuter = 0.880f
                mainSelectedOuter = 0.952f
                submenuInner = 0.280f
                submenuOuter = 0.512f
            }, label = { Text("Standard") })
            FilterChip(selected = false, onClick = {
                mainInner = 0.60f
                mainOuter = 0.85f
                mainHoverOuter = 0.90f
                mainSelectedOuter = 0.97f
                submenuInner = 0.35f
                submenuOuter = 0.58f
            }, label = { Text("Compact") })
            FilterChip(selected = false, onClick = {
                mainInner = 0.552f
                mainOuter = 0.808f
                mainHoverOuter = 0.880f
                mainSelectedOuter = 0.952f
                submenuInner = 0.280f
                submenuOuter = 0.512f
                sectorGap = 4f
                seekThumbRadius = 10f
            }, label = { Text("Automotive") })
            FilterChip(selected = false, onClick = {
                mainInner = 0.45f
                mainOuter = 0.82f
                mainHoverOuter = 0.90f
                mainSelectedOuter = 0.98f
                submenuInner = 0.22f
                submenuOuter = 0.50f
            }, label = { Text("Large") })
        }
        OneRing(
            items = items,
            onAction = {},
            style = OneRingDefaults.style(dimensions = dimensions),
            modifier = Modifier.padding(vertical = 16.dp).size(320.dp),
        )
        GeometrySlider("Main inner", mainInner, 0.1f..0.85f) { mainInner = it }
        GeometrySlider("Main outer", mainOuter, 0.2f..0.95f) { mainOuter = it }
        GeometrySlider("Main hover outer", mainHoverOuter, 0.2f..1f) { mainHoverOuter = it }
        GeometrySlider("Main selected outer", mainSelectedOuter, 0.2f..1f) { mainSelectedOuter = it }
        GeometrySlider("Submenu inner", submenuInner, 0.05f..0.6f) { submenuInner = it }
        GeometrySlider("Submenu outer", submenuOuter, 0.1f..0.7f) { submenuOuter = it }
        GeometrySlider("Sector gap (dp)", sectorGap, 0f..16f) { sectorGap = it }
        GeometrySlider("Seek thumb radius (dp)", seekThumbRadius, 2f..16f) { seekThumbRadius = it }
        GeometrySlider("Start angle (deg)", startAngle, 0f..360f) { startAngle = it }
        GeometrySlider("Total sweep (deg)", totalSweep, 60f..360f) { totalSweep = it }
    }
}

@Composable
private fun GeometrySlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
