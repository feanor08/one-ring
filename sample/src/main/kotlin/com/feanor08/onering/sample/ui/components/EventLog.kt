package com.feanor08.onering.sample.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private const val MAX_EVENTS = 20

/** A small ring buffer of the last [MAX_EVENTS] callback events, for verifying callback behavior at a glance. */
class EventLogState {
    private val backing = mutableStateListOf<String>()
    val events: List<String> get() = backing
    var expanded by mutableStateOf(false)

    fun log(message: String) {
        backing.add(0, message)
        while (backing.size > MAX_EVENTS) backing.removeAt(backing.lastIndex)
    }

    fun clear() {
        backing.clear()
    }
}

@Composable
fun rememberEventLogState(): EventLogState = remember { EventLogState() }

@Composable
fun EventLogCard(state: EventLogState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Event log", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { state.expanded = !state.expanded }) {
                    Text(if (state.expanded) "Hide" else "Show (${state.events.size})")
                }
            }
            if (state.expanded) {
                if (state.events.isEmpty()) {
                    Text(
                        "(no events yet -- interact with the ring above)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    state.events.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
