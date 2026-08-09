package com.feanor08.onering.sample.ui.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
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
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.components.CodeExampleCard
import com.feanor08.onering.sample.ui.components.DemoScaffold

private val counts = listOf(2, 3, 4, 5, 6, 8, 10, 12)

/** B. Proves the geometry engine is generic across item count, not hardcoded to six sectors. */
@Composable
fun ItemCountDemo(onBack: () -> Unit) {
    var count by remember { mutableStateOf(6) }
    var lastSelected by remember { mutableStateOf<String?>(null) }

    val items = remember(count) {
        (1..count).map { index ->
            OneRingItem.Action(id = "item$index", label = "Item $index", icon = OneRingIcon.Vector(Icons.Rounded.Star))
        }
    }

    DemoScaffold(
        title = Demo.ItemCounts.title,
        subtitle = "The same RingLayout.spans() geometry, driven by nothing but items.size.",
        onBack = onBack,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(counts) { n ->
                FilterChip(selected = n == count, onClick = { count = n }, label = { Text("$n") })
            }
        }
        OneRing(
            items = items,
            onAction = { lastSelected = it.label },
            modifier = Modifier.padding(vertical = 24.dp).size(320.dp),
        )
        Text("Last selected: ${lastSelected ?: "(none yet)"}")
        CodeExampleCard(
            code = """
                val items = (1..count).map { i ->
                    OneRingItem.Action(id = "item${'$'}i", label = "Item ${'$'}i")
                }
                OneRing(items = items, onAction = { lastSelected = it.label })
            """,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
