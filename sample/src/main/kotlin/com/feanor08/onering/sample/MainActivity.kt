package com.feanor08.onering.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.sample.theme.OneRingSampleTheme

/**
 * Sample host activity, reproducing the reference prototype's main menu with
 * the real [OneRing] composable, including both seek variants and a preset
 * switcher demonstrating [OneRingDefaults]' style presets. See
 * [sampleItems] for the item tree and [StylePreset] for the presets.
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

/** The four style presets shown as chips, matching [OneRingDefaults]' factory functions. */
private enum class StylePreset(val label: String) {
    Default("Default"),
    Compact("Compact"),
    Automotive("Automotive"),
    Game("Game"),
}

@Composable
private fun SampleApp() {
    var volume by remember { mutableFloatStateOf(0.48f) }
    var musicVolume by remember { mutableFloatStateOf(0.67f) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    var preset by remember { mutableStateOf(StylePreset.Default) }

    val items = remember(volume, musicVolume) {
        sampleItems(
            volume = volume,
            onVolumeChange = { volume = it },
            musicVolume = musicVolume,
            onMusicVolumeChange = { musicVolume = it },
        )
    }

    val style = remember(preset) {
        when (preset) {
            StylePreset.Default -> OneRingDefaults.style()
            StylePreset.Compact -> OneRingDefaults.compactStyle()
            StylePreset.Automotive -> OneRingDefaults.automotiveStyle()
            StylePreset.Game -> OneRingDefaults.gameStyle()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StylePreset.entries.forEach { entry ->
                        FilterChip(
                            selected = entry == preset,
                            onClick = { preset = entry },
                            label = { Text(entry.label) },
                        )
                    }
                }

                OneRing(
                    items = items,
                    onAction = { lastAction = it.label },
                    style = style,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(360.dp),
                )

                Text("Last action: ${lastAction ?: "(none yet)"}", modifier = Modifier.padding(top = 24.dp))
                Text("Volume: ${(volume * 100).toInt()}%  ·  Music volume: ${(musicVolume * 100).toInt()}%")
            }
        }
    }
}
