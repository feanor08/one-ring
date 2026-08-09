package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Fireplace
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.oneRingItems
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

/** An 8-item weapon wheel -- proves one-ring isn't automotive-specific. Also demonstrates the center content slot (R). */
@Composable
fun GameWheelDemo(onBack: () -> Unit) {
    var equipped by remember { mutableStateOf<String?>(null) }

    val items = remember {
        oneRingItems {
            action(id = "sword", label = "Sword", icon = OneRingIcon.Vector(Icons.Rounded.Hardware))
            action(id = "bow", label = "Bow", icon = OneRingIcon.Vector(Icons.Rounded.Colorize))
            action(id = "shield", label = "Shield", icon = OneRingIcon.Vector(Icons.Rounded.Security))
            action(id = "potion", label = "Potion", icon = OneRingIcon.Vector(Icons.Rounded.Science))
            action(id = "bomb", label = "Bomb", icon = OneRingIcon.Vector(Icons.Rounded.Whatshot))
            action(id = "torch", label = "Torch", icon = OneRingIcon.Vector(Icons.Rounded.Fireplace))
            action(id = "map", label = "Map", icon = OneRingIcon.Vector(Icons.Rounded.Map))
            action(id = "magic", label = "Magic", icon = OneRingIcon.Vector(Icons.Rounded.AutoAwesome))
        }
    }

    DemoScaffold(
        title = Demo.GameWheel.title,
        subtitle = "OneRingDefaults.gameStyle() -- snappier, bouncier motion. The center slot shows what's " +
            "currently under the pointer.",
        onBack = onBack,
    ) {
        OneRing(
            items = items,
            onAction = { equipped = it.label },
            style = OneRingDefaults.gameStyle(),
            modifier = Modifier.padding(vertical = 24.dp).size(340.dp),
            center = {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        highlightedId?.replaceFirstChar(Char::uppercase) ?: equipped ?: "Equip",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
        Text("Equipped: ${equipped ?: "(nothing)"}")
        CodeExampleCard(
            code = """
                OneRing(
                    items = items,
                    onAction = { equipped = it.label },
                    style = OneRingDefaults.gameStyle(),
                    center = {
                        // OneRingCenterScope exposes highlightedId and currentMenuId
                        Text(highlightedId ?: "Equip")
                    },
                )
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
