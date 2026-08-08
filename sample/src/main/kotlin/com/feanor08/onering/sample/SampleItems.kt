package com.feanor08.onering.sample

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.oneRingItems

/**
 * The reference prototype's exact main menu (Audio, Bluetooth, Messages,
 * Music, Volume, Settings), rebuilt with the [oneRingItems] DSL and both
 * seek variants -- a main-ring Volume and a Music -> Volume submenu seek.
 * See docs/interaction-spec.md for the behavioural spec this reproduces.
 */
internal fun sampleItems(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    musicVolume: Float,
    onMusicVolumeChange: (Float) -> Unit,
): List<OneRingItem> = oneRingItems {
    menu(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq)) {
        action(id = "low", label = "Low")
        action(id = "mute", label = "Mute")
        action(id = "high", label = "High")
    }
    menu(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth)) {
        action(id = "devices", label = "Devices")
        action(id = "connect", label = "Connect")
        action(id = "disconnect", label = "Disconnect")
    }
    menu(id = "messages", label = "Messages", icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.Message)) {
        action(id = "read", label = "Read")
        action(id = "reply", label = "Reply")
        action(id = "dismiss", label = "Dismiss")
    }
    menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
        action(id = "previous", label = "Previous")
        action(id = "playPause", label = "Play / Pause")
        action(id = "next", label = "Next")
        seek(
            id = "musicVolume",
            label = "Volume",
            icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
            value = musicVolume,
            onValueChange = onMusicVolumeChange,
            valueLabel = { "${(it * 100).toInt()}%" },
        )
    }
    seek(
        id = "volume",
        label = "Volume",
        icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
        value = volume,
        onValueChange = onVolumeChange,
        valueLabel = { "${(it * 100).toInt()}%" },
    )
    menu(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings)) {
        action(id = "display", label = "Display")
        action(id = "sound", label = "Sound")
        action(id = "system", label = "System")
    }
}
