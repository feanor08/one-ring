package com.feanor08.onering.sample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feanor08.onering.sample.ui.demos.AccessibilityDemo
import com.feanor08.onering.sample.ui.demos.AutomotiveDemo
import com.feanor08.onering.sample.ui.demos.BasicDemo
import com.feanor08.onering.sample.ui.demos.DynamicDemo
import com.feanor08.onering.sample.ui.demos.GameWheelDemo
import com.feanor08.onering.sample.ui.demos.GeometryDemo
import com.feanor08.onering.sample.ui.demos.ItemCountDemo
import com.feanor08.onering.sample.ui.demos.MediaDemo
import com.feanor08.onering.sample.ui.demos.MotionDemo
import com.feanor08.onering.sample.ui.demos.NestedDemo
import com.feanor08.onering.sample.ui.demos.NestedGrowthDemo
import com.feanor08.onering.sample.ui.demos.NotificationDemo
import com.feanor08.onering.sample.ui.demos.NotificationStackStressDemo
import com.feanor08.onering.sample.ui.demos.SeekDemo
import com.feanor08.onering.sample.ui.demos.SmartHomeDemo
import com.feanor08.onering.sample.ui.demos.StressTestDemo
import com.feanor08.onering.sample.ui.demos.StylingDemo
import com.feanor08.onering.sample.ui.demos.WeightedDemo

/** Root of the showcase: a flat home list, and a single-level "open a demo" navigation. */
@Composable
fun ShowcaseApp() {
    var current by rememberSaveable { mutableStateOf<Demo?>(null) }

    val demo = current
    if (demo == null) {
        ShowcaseHome(onSelect = { current = it })
    } else {
        DemoHost(demo = demo, onBack = { current = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowcaseHome(onSelect: (Demo) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("One Ring Showcase") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(Demo.entries) { demo ->
                Card(
                    onClick = { onSelect(demo) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(demo.title, style = MaterialTheme.typography.titleMedium)
                        Text(demo.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoHost(demo: Demo, onBack: () -> Unit) {
    when (demo) {
        Demo.Basic -> BasicDemo(onBack)
        Demo.Nested -> NestedDemo(onBack)
        Demo.Weighted -> WeightedDemo(onBack)
        Demo.NestedGrowth -> NestedGrowthDemo(onBack)
        Demo.Seek -> SeekDemo(onBack)
        Demo.Notifications -> NotificationDemo(onBack)
        Demo.NotificationStackStress -> NotificationStackStressDemo(onBack)
        Demo.ItemCounts -> ItemCountDemo(onBack)
        Demo.Geometry -> GeometryDemo(onBack)
        Demo.Styling -> StylingDemo(onBack)
        Demo.Motion -> MotionDemo(onBack)
        Demo.Dynamic -> DynamicDemo(onBack)
        Demo.Automotive -> AutomotiveDemo(onBack)
        Demo.GameWheel -> GameWheelDemo(onBack)
        Demo.SmartHome -> SmartHomeDemo(onBack)
        Demo.Media -> MediaDemo(onBack)
        Demo.StressTest -> StressTestDemo(onBack)
        Demo.Accessibility -> AccessibilityDemo(onBack)
    }
}
