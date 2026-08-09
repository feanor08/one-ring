package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold
import com.feanor08.onering.style.OneRingColors

private enum class ColorPreset(val label: String, val build: () -> OneRingColors) {
    DefaultDark("Default Dark", { OneRingDefaults.colors() }),
    Light(
        "Light",
        {
            OneRingDefaults.colors(
                sector = Color(0xFFE8E8E8),
                sectorAlternate = Color(0xFFDCDCDC),
                sectorHighlighted = Color(0xFF356FA2),
                stroke = Color(0xFFC4C4C4),
                content = Color(0xFF1B1B1B),
                contentHighlighted = Color.White,
                center = Color(0xFFF5F5F5),
            )
        },
    ),
    Automotive(
        "Automotive",
        {
            OneRingDefaults.colors(
                sector = Color(0xFF232830),
                sectorHighlighted = Color(0xFF3D8BFF),
                strokeHighlighted = Color(0xFF7FB8FF),
                seekProgress = Color(0xFF3D8BFF),
            )
        },
    ),
    Cyberpunk(
        "Cyberpunk",
        {
            OneRingDefaults.colors(
                sector = Color(0xFF120A1E),
                sectorAlternate = Color(0xFF1B0F2E),
                sectorHighlighted = Color(0xFFFF2079),
                strokeHighlighted = Color(0xFF00F0FF),
                content = Color(0xFF00F0FF),
                seekProgress = Color(0xFFFF2079),
                seekThumb = Color(0xFF00F0FF),
            )
        },
    ),
    MinimalMonochrome(
        "Minimal Monochrome",
        {
            OneRingDefaults.colors(
                sector = Color(0xFF2B2B2B),
                sectorAlternate = Color(0xFF2B2B2B),
                sectorHighlighted = Color(0xFF6B6B6B),
                stroke = Color(0xFF000000),
                strokeHighlighted = Color(0xFFFFFFFF),
                seekProgress = Color(0xFFBDBDBD),
            )
        },
    ),
    HighContrast(
        "High Contrast",
        {
            OneRingDefaults.colors(
                sector = Color.Black,
                sectorAlternate = Color(0xFF111111),
                sectorHighlighted = Color.Yellow,
                stroke = Color.White,
                strokeHighlighted = Color.Yellow,
                content = Color.White,
                contentHighlighted = Color.Black,
                seekProgress = Color.Yellow,
            )
        },
    ),
}

private val highlightSwatches =
    listOf(Color(0xFF356FA2), Color(0xFF3D8BFF), Color(0xFFFF2079), Color(0xFF00C896), Color(0xFFFFC107))

/** L + M. Color presets built entirely through OneRingDefaults.colors(), plus a couple of live swatch overrides. */
@Composable
fun StylingDemo(onBack: () -> Unit) {
    var preset by remember { mutableStateOf(ColorPreset.DefaultDark) }
    var highlightOverride by remember { mutableStateOf<Color?>(null) }

    val items = remember {
        oneRingItems {
            action(id = "audio", label = "Audio", icon = OneRingIcon.Vector(Icons.Rounded.GraphicEq))
            action(id = "bluetooth", label = "Bluetooth", icon = OneRingIcon.Vector(Icons.Rounded.Bluetooth))
            action(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote))
            seek(
                id = "volume",
                label = "Volume",
                icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
                value = 0.6f,
                onValueChange = {},
            )
            action(id = "settings", label = "Settings", icon = OneRingIcon.Vector(Icons.Rounded.Settings))
        }
    }

    val baseColors = preset.build()
    val colors = if (highlightOverride != null) {
        OneRingDefaults.colors(
            sector = baseColors.sector,
            sectorAlternate = baseColors.sectorAlternate,
            sectorHighlighted = highlightOverride!!,
            strokeHighlighted = highlightOverride!!,
            content = baseColors.content,
            contentHighlighted = baseColors.contentHighlighted,
            center = baseColors.center,
            seekProgress = highlightOverride!!,
        )
    } else {
        baseColors
    }

    DemoScaffold(
        title = Demo.Styling.title,
        subtitle = "Every color here comes from OneRingDefaults.colors() -- nothing reaches into the " +
            "library's internals.",
        onBack = onBack,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ColorPreset.entries) { p ->
                FilterChip(selected = p == preset, onClick = {
                    preset = p
                    highlightOverride = null
                }, label = { Text(p.label) })
            }
        }
        OneRing(
            items = items,
            onAction = {},
            style = OneRingDefaults.style(colors = colors),
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Highlight color override", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            highlightSwatches.forEach { swatch ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .clickable { highlightOverride = swatch },
                )
            }
        }
        CodeExampleCard(
            code = """
                OneRing(
                    items = items, onAction = {},
                    style = OneRingDefaults.style(
                        colors = OneRingDefaults.colors(sectorHighlighted = Color(0xFF3D8BFF)),
                    ),
                )
            """,
        )
    }
}
