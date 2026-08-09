package com.feanor08.onering.sample.ui.demos

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private enum class MotionPreset(val label: String, val durationMs: Int) {
    Instant("Instant", 0),
    Fast("Fast", 120),
    Default("Default", 280),
    Slow("Slow", 700),
}

/** X. Every OneRingMotion field is a real AnimationSpec<Float> -- swap them all for tween(duration) or snap(). */
@Composable
fun MotionDemo(onBack: () -> Unit) {
    var preset by remember { mutableStateOf(MotionPreset.Default) }

    val items = remember {
        oneRingItems {
            action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq))
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
                action(id = "previous", label = "Previous")
                action(id = "next", label = "Next")
                seek(
                    id = "musicVolume",
                    label = "Volume",
                    icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                    value = 0.5f,
                    onValueChange = {},
                )
            }
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    val motion = if (preset == MotionPreset.Instant) {
        OneRingDefaults.motion(
            select = snap(),
            close = snap(),
            submenuReveal = snap(),
            iconMorph = snap(),
            siblingExit = snap(),
            seekHintRise = snap(),
            seekHintSettle = snap(),
        )
    } else {
        val d = preset.durationMs
        OneRingDefaults.motion(
            select = tween(d),
            close = tween((d * 0.9f).toInt()),
            submenuReveal = tween(d),
            siblingExit = tween((d * 0.65f).toInt()),
            seekHintRise = tween((d * 1.7f).toInt()),
            seekHintSettle = tween((d * 0.7f).toInt()),
        )
    }

    DemoScaffold(
        title = Demo.Motion.title,
        subtitle = "Open Music and try Previous/Next, or select Music itself, at each speed.",
        onBack = onBack,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            MotionPreset.entries.forEach { p ->
                FilterChip(selected = p == preset, onClick = { preset = p }, label = { Text(p.label) })
            }
        }
        OneRing(
            items = items,
            onAction = {},
            style = OneRingDefaults.style(motion = motion),
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        CodeExampleCard(
            code = """
                OneRing(
                    items = items, onAction = {},
                    style = OneRingDefaults.style(
                        motion = OneRingDefaults.motion(
                            select = tween(120), close = tween(108), submenuReveal = tween(120),
                        ),
                    ),
                )
            """,
        )
    }
}
