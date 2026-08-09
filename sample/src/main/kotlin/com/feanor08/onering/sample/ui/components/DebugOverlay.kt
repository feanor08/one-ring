package com.feanor08.onering.sample.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingState

/**
 * A developer/QA diagnostic readout, built entirely from [OneRingState]'s
 * already-public surface (`mode`, `path`, `highlightedId`) plus values the
 * demo screen already has on hand -- no internal library API is touched.
 * Pointer-level diagnostics (live angle/radius) aren't included: the only way
 * to observe them would be a second pointer listener layered over the ring's
 * own gesture detector, which risks stealing events from it, so that's left
 * as a documented gap rather than something that can silently break input.
 */
@Composable
fun DebugOverlayToggle(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text("Show geometry / state overlay", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
fun DebugOverlayCard(
    state: OneRingState,
    itemCount: Int,
    activeParentId: String? = null,
    activeSeekValue: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Debug overlay", style = MaterialTheme.typography.titleSmall)
            DebugRow("mode", state.mode.toString())
            DebugRow("path", state.path.ifEmpty { listOf("(root)") }.toString())
            DebugRow("highlightedId", state.highlightedId ?: "(none)")
            DebugRow("itemCount", itemCount.toString())
            DebugRow("activeParent", activeParentId ?: "(none)")
            if (activeSeekValue != null) DebugRow("seekValue", activeSeekValue)
        }
    }
}

@Composable
private fun DebugRow(key: String, value: String) {
    Text("$key = $value", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}
