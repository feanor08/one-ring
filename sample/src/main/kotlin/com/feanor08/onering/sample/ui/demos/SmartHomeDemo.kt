package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Curtains
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

/** Q. A realistic smart-home wheel, and the place custom composable wedge content (OneRingIcon.Content) is shown. */
@Composable
fun SmartHomeDemo(onBack: () -> Unit) {
    var brightness by remember { mutableFloatStateOf(70f) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    var acOn by remember { mutableStateOf(true) }

    val items = remember(brightness, acOn) {
        oneRingItems {
            menu(id = "lights", label = "Lights", icon = OneRingIcon.Vector(Icons.Rounded.Lightbulb)) {
                action(id = "living", label = "Living Room")
                action(id = "bedroom", label = "Bedroom")
                action(id = "kitchen", label = "Kitchen")
                action(id = "balcony", label = "Balcony")
                seek(
                    id = "brightness",
                    label = "Brightness",
                    value = brightness,
                    valueRange = 0f..100f,
                    onValueChange = { brightness = it },
                    valueLabel = { "${it.toInt()}%" },
                )
            }
            action(id = "fan", label = "Fan", icon = OneRingIcon.Vector(Icons.Rounded.Toys))
            // Custom composable wedge content: a status dot whose colour tracks
            // app state, proving content slots aren't limited to a static icon.
            action(
                id = "ac",
                label = "AC",
                contentDescription = if (acOn) "AC, on" else "AC, off",
                icon = OneRingIcon.Content {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (acOn) Color(0xFF00C896) else Color(0xFF6B6B6B)),
                        )
                    }
                },
            )
            action(id = "tv", label = "TV", icon = OneRingIcon.Vector(Icons.Rounded.Tv))
            action(id = "curtains", label = "Curtains", icon = OneRingIcon.Vector(Icons.Rounded.Curtains))
            action(id = "scenes", label = "Scenes", icon = OneRingIcon.Vector(Icons.Rounded.AutoAwesome))
        }
    }

    DemoScaffold(
        title = Demo.SmartHome.title,
        subtitle = "Lights opens a five-child submenu whose last child is a Brightness seek. The AC wedge uses " +
            "OneRingIcon.Content -- a live status dot rather than a static icon.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = {
                lastAction = it.label
                if (it.id == "ac") acOn = !acOn
            },
            modifier = Modifier.padding(vertical = 24.dp).size(330.dp),
        )
        Text("Last action: ${lastAction ?: "(none yet)"}")
        Text("Brightness: ${brightness.toInt()}%  ·  AC: ${if (acOn) "on" else "off"}")
        Text(
            "Select AC repeatedly to watch its custom content change with app state.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        CodeExampleCard(
            code = """
                action(
                    id = "ac", label = "AC",
                    icon = OneRingIcon.Content {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(if (acOn) Green else Grey))
                    },
                )
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
