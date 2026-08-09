package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
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
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

/** U. Transport controls whose icons and labels swap with app state, plus a media-position seek. */
@Composable
fun MediaDemo(onBack: () -> Unit) {
    var playing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(72f) }
    var lastAction by remember { mutableStateOf<String?>(null) }

    val items = remember(playing, muted, position) {
        oneRingItems {
            action(id = "previous", label = "Previous", icon = OneRingIcon.Vector(Icons.Rounded.SkipPrevious))
            action(id = "rewind", label = "Rewind", icon = OneRingIcon.Vector(Icons.Rounded.FastRewind))
            action(
                id = "playPause",
                label = if (playing) "Pause" else "Play",
                icon = OneRingIcon.Vector(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow),
            )
            action(id = "forward", label = "Forward", icon = OneRingIcon.Vector(Icons.Rounded.FastForward))
            action(id = "next", label = "Next", icon = OneRingIcon.Vector(Icons.Rounded.SkipNext))
            action(
                id = "mute",
                label = if (muted) "Unmute" else "Mute",
                icon = OneRingIcon.Vector(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                ),
            )
            seek(
                id = "position",
                label = "Position",
                value = position,
                valueRange = 0f..300f,
                onValueChange = { position = it },
                valueLabel = { "${(it.toInt() / 60)}:${(it.toInt() % 60).toString().padStart(2, '0')}" },
            )
        }
    }

    DemoScaffold(
        title = Demo.Media.title,
        subtitle = "Play/Pause and Mute/Unmute swap both their icon and their label from app state -- the item " +
            "list is just a function of that state, so nothing is reconstructed manually.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = {
                lastAction = it.label
                when (it.id) {
                    "playPause" -> playing = !playing
                    "mute" -> muted = !muted
                }
            },
            modifier = Modifier.padding(vertical = 24.dp).size(330.dp),
        )
        Text(if (playing) "Playing" else "Paused", style = MaterialTheme.typography.titleMedium)
        Text(
            "Position: ${position.toInt() / 60}:${(position.toInt() % 60).toString().padStart(2, '0')} / 5:00  ·  " +
                (if (muted) "muted" else "unmuted"),
        )
        Text("Last action: ${lastAction ?: "(none yet)"}")
        CodeExampleCard(
            code = """
                action(
                    id = "playPause",
                    label = if (playing) "Pause" else "Play",
                    icon = OneRingIcon.Vector(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow),
                )
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
