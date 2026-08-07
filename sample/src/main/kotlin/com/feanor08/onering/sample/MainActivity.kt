package com.feanor08.onering.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.sample.theme.OneRingSampleTheme

/**
 * Sample host activity, reproducing the reference prototype's main menu
 * (Audio, Bluetooth, Messages, Music, Volume, Settings) with the real
 * [OneRing] composable. Pointer interaction lands in Phase 3 -- this
 * currently exercises Phase 2's static rendering only.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneRingSampleTheme {
                SampleApp()
            }
        }
    }
}

@Composable
private fun SampleApp() {
    val items = remember {
        listOf(
            OneRingItem.Menu(
                id = "audio",
                label = "Audio",
                icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq),
                children = listOf(
                    OneRingItem.Action(id = "low", label = "Low"),
                    OneRingItem.Action(id = "mute", label = "Mute"),
                    OneRingItem.Action(id = "high", label = "High"),
                ),
            ),
            OneRingItem.Menu(
                id = "bluetooth",
                label = "Bluetooth",
                icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth),
                children = listOf(
                    OneRingItem.Action(id = "devices", label = "Devices"),
                    OneRingItem.Action(id = "connect", label = "Connect"),
                    OneRingItem.Action(id = "disconnect", label = "Disconnect"),
                ),
            ),
            OneRingItem.Menu(
                id = "messages",
                label = "Messages",
                icon = OneRingIcon.Vector(Icons.Rounded.Message),
                children = listOf(
                    OneRingItem.Action(id = "read", label = "Read"),
                    OneRingItem.Action(id = "reply", label = "Reply"),
                    OneRingItem.Action(id = "dismiss", label = "Dismiss"),
                ),
            ),
            OneRingItem.Menu(
                id = "music",
                label = "Music",
                icon = OneRingIcon.Vector(Icons.Rounded.MusicNote),
                children = listOf(
                    OneRingItem.Action(id = "previous", label = "Previous"),
                    OneRingItem.Action(id = "playPause", label = "Play / Pause"),
                    OneRingItem.Action(id = "next", label = "Next"),
                ),
            ),
            OneRingItem.Seek(
                id = "volume",
                label = "Volume",
                icon = OneRingIcon.Vector(Icons.Rounded.VolumeUp),
                value = 0.48f,
                onValueChange = {},
            ),
            OneRingItem.Menu(
                id = "settings",
                label = "Settings",
                icon = OneRingIcon.Vector(Icons.Rounded.Settings),
                children = listOf(
                    OneRingItem.Action(id = "display", label = "Display"),
                    OneRingItem.Action(id = "sound", label = "Sound"),
                    OneRingItem.Action(id = "system", label = "System"),
                ),
            ),
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OneRing(items = items, modifier = Modifier.size(360.dp))
            }
        }
    }
}
